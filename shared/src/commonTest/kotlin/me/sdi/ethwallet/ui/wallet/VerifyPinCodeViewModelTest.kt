package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: VerifyPinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var dataCleaner: FakeDataCleaner
    private lateinit var pinAttemptsGuard: FakePinAttemptsGuard
    private lateinit var biometricUnlock: FakeBiometricUnlock
    private val testPin = "123456"
    private val wrongPin = "000000"

    private val collectedNews = mutableListOf<VerifyPinCodeNews>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        dataCleaner = FakeDataCleaner()
        pinAttemptsGuard = FakePinAttemptsGuard()
        biometricUnlock = FakeBiometricUnlock()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_have_default_values() = runTest(testDispatcher) {
        createViewModel()

        val initialState = viewModel.viewState.first()

        assertEquals("", initialState.enteredPin, "Initial PIN should be empty")
        assertNull(initialState.errorMessage, "No error message should be present initially")
        assertEquals(3, initialState.remainingAttempts, "Initial attempts should default to the guard's window size")
        assertFalse(initialState.isVerifying, "Verification should not be in progress initially")
        assertFalse(initialState.isLoggingOut, "Logout should not be in progress")
        assertFalse(initialState.isLocked, "Lock state should be inactive initially")
        assertEquals(0, initialState.lockCountdown, "Lock countdown should be zero initially")
        assertFalse(initialState.isInitializing, "Initialization should complete once status has been queried")
    }

    @Test
    fun should_ignore_digit_clicks_while_initializing() = runTest(testDispatcher) {
        viewModel = VerifyPinCodeViewModel(pinCodeStorage, dataCleaner, pinAttemptsGuard, biometricUnlock)
        assertTrue(viewModel.viewState.value.isInitializing, "ViewModel should start in the initializing state")

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        assertEquals("", viewModel.viewState.value.enteredPin, "Digit clicks during initialization should be ignored")

        runCurrent()
        assertFalse(viewModel.viewState.value.isInitializing, "Initialization should complete after status resolves")
    }

    @Test
    fun should_render_locked_state_from_status_at_init_and_ignore_digit_clicks() = runTest(testDispatcher) {
        pinAttemptsGuard.statusResult = PinLockState.Active(3, 3, 25.seconds)
        createViewModel()

        val state = viewModel.viewState.first()
        assertTrue(state.isLocked, "A locked status at init should render the screen as locked")
        assertEquals(25, state.lockCountdown, "Lock countdown should mirror the guard's remaining lock time")

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        assertEquals("", viewModel.viewState.value.enteredPin, "Digit clicks should be ignored while locked")

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tampered_status_at_init_should_wipe_and_log_out() = runTest(testDispatcher) {
        pinAttemptsGuard.statusResult = PinLockState.Tampered
        viewModel = VerifyPinCodeViewModel(pinCodeStorage, dataCleaner, pinAttemptsGuard, biometricUnlock)
        collectNews()
        runCurrent()

        assertEquals(
            VerifyPinCodeNews.LoggedOut,
            collectedNews.last(),
            "Tampered status at init should log the user out",
        )
        assertEquals(1, dataCleaner.cleanCalledCount, "Tampered status should trigger a data cleanup")
        assertTrue(viewModel.viewState.first().isLoggingOut, "Tampered status should show the logout state")
    }

    @Test
    fun should_verify_PIN_successfully_and_forget_failed_attempts() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        collectNews()

        inputPin(testPin)

        assertEquals(1, pinAttemptsGuard.onSuccessCallCount, "Success should reset the guard's failure count")
        assertTrue(collectedNews.isEmpty(), "A verified PIN is observed through the session, not through news")

        val finalState = viewModel.viewState.first()
        assertTrue(finalState.isVerifying, "Verification flag should remain set until navigation")
        assertEquals(testPin, finalState.enteredPin, "Entered PIN should remain complete until navigation")
        assertEquals(3, finalState.remainingAttempts, "Successful verification should not change attempts")
        assertFalse(finalState.isLocked, "Successful verification should not lock the screen")
        assertNull(finalState.errorMessage, "No error message should be present after successful PIN")
        assertEquals(1, pinAttemptsGuard.onSuccessCallCount, "Correct PIN should reset the guard's failure count")
    }

    @Test
    fun should_ignore_input_after_successful_verification_until_reset() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        collectNews()

        inputPin(testPin)

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        val state = viewModel.viewState.first()
        assertEquals(testPin, state.enteredPin, "PIN input should remain unchanged until navigation")
        assertTrue(state.isVerifying, "Verification should remain in progress until navigation")
    }

    @Test
    fun reset_should_logout_after_successful_verification() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        collectNews()

        inputPin(testPin)

        viewModel.onEvent(VerifyPinCodeEvent.Reset)
        advanceUntilIdle()
        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        val state = viewModel.viewState.first()
        assertEquals(testPin, state.enteredPin, "PIN input should remain disabled during logout")
        assertTrue(state.isLoggingOut, "Reset should start logout")
        assertEquals(VerifyPinCodeNews.LoggedOut, collectedNews.last())
    }

    @Test
    fun digit_clicks_should_append_to_PIN() = runTest(testDispatcher) {
        createViewModel()
        inputPin("123")

        val state = viewModel.viewState.first()
        assertEquals("123", state.enteredPin, "Digits should be appended to PIN")
        assertFalse(state.isVerifying, "Verification shouldn't start until PIN is complete")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_PIN() = runTest(testDispatcher) {
        createViewModel()
        inputPin("123")
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("12", state.enteredPin, "Delete should remove the last entered digit")
    }

    @Test
    fun delete_click_should_have_no_effect_when_PIN_is_empty() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Delete should have no effect on empty PIN")
    }

    @Test
    fun invalid_PIN_verification_should_decrement_remaining_attempts() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Pin must be reset after wrong attempt")
        assertFalse(state.errorMessage.isNullOrBlank(), "Error message after invalid pin input")
        assertEquals(2, state.remainingAttempts, "remainingAttempts must mirror the guard's remaining-in-window")
        assertTrue(state.showRemainingAttempts, "A wrong PIN should show how many attempts are left")
        assertFalse(state.isVerifying, "Flag isVerifying must be reset after pin check to unlock numpad")
        assertFalse(state.isLocked, "User must be able to input pin again when there are 2 more attempts left")
        assertEquals(1, pinAttemptsGuard.onFailedAttemptCallCount, "Wrong PIN should be recorded with the guard")
    }

    @Test
    fun entered_pin_should_remain_visible_immediately_after_wrong_attempt() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)

        val state = viewModel.viewState.first()
        assertEquals(1, state.failedAttemptId, "First wrong attempt should set failedAttemptId to 1")
        assertNotNull(state.errorMessage, "Error message should be present after wrong PIN")
        assertEquals(2, state.remainingAttempts, "First wrong attempt should reduce attempts to 2")
        assertEquals(wrongPin, state.enteredPin, "Entered PIN should stay visible for the shake animation")
    }

    @Test
    fun storage_exception_should_clear_entered_pin_immediately() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinCodeStorage.shouldThrowException = true
        createViewModel()
        inputPin(testPin)

        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "Error message should be present when verification fails")
        assertEquals("", state.enteredPin, "Entered PIN should clear immediately on storage errors")
        assertEquals(0, state.failedAttemptId, "Storage errors are not wrong-PIN attempts")
        assertEquals(
            0,
            pinAttemptsGuard.onFailedAttemptCallCount,
            "Storage errors should not be recorded as guard attempts",
        )
    }

    @Test
    fun entered_pin_should_clear_after_error_delay() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Entered PIN should clear once the error delay elapses")
        assertNotNull(state.errorMessage, "Error message should remain visible after the PIN clears")
    }

    @Test
    fun second_wrong_pin_should_increment_failedAttemptId() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)
        inputPin(wrongPin)

        val state = viewModel.viewState.first()
        assertEquals(2, state.failedAttemptId, "Second wrong attempt should bump failedAttemptId to 2")
    }

    @Test
    fun digit_click_during_error_window_should_replace_stale_pin() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(5))

        val state = viewModel.viewState.first()
        assertEquals("5", state.enteredPin, "A fresh digit should replace the stale failed PIN")
        assertNull(state.errorMessage, "Error message should clear when starting a fresh attempt")
    }

    @Test
    fun delete_click_during_error_window_should_clear_stale_pin() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)

        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Delete should clear the stale failed PIN")
        assertNull(state.errorMessage, "Error message should clear when deleting the stale PIN")
    }

    @Test
    fun third_wrong_pin_should_lock_and_clear_pin_after_delay() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResults.addAll(escalatingToLockResults())
        createViewModel()

        inputPin(wrongPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val lockedState = viewModel.viewState.first()
        assertTrue(lockedState.isLocked, "Third wrong attempt should lock the screen")
        assertEquals(3, lockedState.failedAttemptId, "Third wrong attempt should bump failedAttemptId to 3")

        advanceUntilIdle()

        val clearedState = viewModel.viewState.first()
        assertEquals("", clearedState.enteredPin, "Entered PIN should clear after the lock-branch delay too")
    }

    @Test
    fun escalated_lock_duration_from_guard_should_drive_countdown() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResult = PinLockState.Active(6, 3, 45.seconds)
        createViewModel()

        inputPin(wrongPin)

        val state = viewModel.viewState.first()
        assertTrue(state.isLocked, "A locked guard result should lock the screen")
        assertEquals(45, state.lockCountdown, "Lock countdown should mirror the escalated lock duration")
        assertEquals("Too many failed attempts", state.errorMessage, "Locked state should show the lock error message")
    }

    @Test
    fun tampered_result_from_failed_attempt_should_wipe_and_log_out() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResult = PinLockState.Tampered
        createViewModel()
        collectNews()

        inputPin(wrongPin)
        advanceUntilIdle()

        assertEquals(
            VerifyPinCodeNews.LoggedOut,
            collectedNews.last(),
            "A tampered guard result should log the user out",
        )
        assertEquals(1, dataCleaner.cleanCalledCount, "Tampered guard result should trigger a data cleanup")
        assertTrue(viewModel.viewState.first().isLoggingOut, "Tampered guard result should show the logout state")
    }

    @Test
    fun handleInvalidPin_should_decrement_remaining_attempts_until_locked() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResults.addAll(
            listOf(
                PinLockState.Active(1, 2, Duration.ZERO),
                PinLockState.Active(2, 1, Duration.ZERO),
            ),
        )
        createViewModel()
        val initialState = viewModel.viewState.first()
        assertEquals(3, initialState.remainingAttempts, "Initial attempts should default to the guard's window size")

        inputPin(wrongPin)
        val state1 = viewModel.viewState.first()
        assertEquals(2, state1.remainingAttempts, "First wrong attempt should reduce attempts to 2")

        inputPin(wrongPin)
        val state2 = viewModel.viewState.first()
        assertEquals(1, state2.remainingAttempts, "Second wrong attempt should reduce attempts to 1")
    }

    @Test
    fun error_message_should_be_cleared_when_entering_digits_after_error() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        inputPin(wrongPin)

        val stateWithError = viewModel.viewState.first()
        assertNotNull(stateWithError.errorMessage, "Error message should be present after wrong PIN")

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(1))

        val stateAfterDigit = viewModel.viewState.first()
        assertNull(stateAfterDigit.errorMessage, "Error message should clear when entering new digits")
        assertEquals("1", stateAfterDigit.enteredPin, "New digit should be added to PIN")
    }

    @Test
    fun should_emit_LoggedOut_news_when_logout_is_confirmed() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.Reset)
        advanceUntilIdle()

        assertEquals(
            VerifyPinCodeNews.LoggedOut,
            collectedNews.firstOrNull(),
            "Logout click should emit LoggedOut news",
        )
        assertEquals(1, dataCleaner.cleanCalledCount)
    }

    @Test
    fun should_emit_Error_news_when_logout_fails() = runTest(testDispatcher) {
        dataCleaner.shouldThrowException = true
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.Reset)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("Error: Simulated data cleanup failure", state.errorMessage)
        assertTrue(collectedNews.single() is VerifyPinCodeNews.Error)
        assertTrue(collectedNews.none { it is VerifyPinCodeNews.LoggedOut })
    }

    @Test
    fun account_should_lock_and_unlock_with_correct_attempt_counts() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResults.addAll(escalatingToLockResults())
        createViewModel()
        val states = mutableListOf<VerifyPinCodeViewState>()
        val job = viewModel.viewState.onEach { states.add(it) }.launchIn(backgroundScope)

        inputPin(wrongPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val lockedState = states.firstOrNull { it.isLocked && it.lockCountdown == 30 }
        assertNotNull(lockedState, "A locked state should exist")

        advanceTimeBy(31 * 1000)
        advanceUntilIdle()
        job.cancel()

        val unlockedState = states.lastOrNull()
        assertNotNull(unlockedState, "An unlocked state should exist after countdown")
        assertFalse(unlockedState.isLocked, "Account should be unlocked")
        assertEquals(3, unlockedState.remainingAttempts, "Attempts should reset to the guard's window size")
        assertEquals(0, unlockedState.lockCountdown, "Countdown should be 0")
        assertEquals(
            2,
            pinAttemptsGuard.statusCallCount,
            "status should be queried once at init and once when the lock countdown reaches zero",
        )
    }

    @Test
    fun reset_should_logout_when_pin_screen_is_locked() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResults.addAll(escalatingToLockResults())
        createViewModel()
        collectNews()

        inputPin(wrongPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val lockedState = viewModel.viewState.first()
        assertTrue(lockedState.isLocked)

        viewModel.onEvent(VerifyPinCodeEvent.Reset)
        advanceUntilIdle()

        assertEquals(VerifyPinCodeNews.LoggedOut, collectedNews.last())
        assertTrue(viewModel.viewState.first().isLoggingOut)
    }

    @Test
    fun should_handle_errors_during_verification() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinCodeStorage.shouldThrowException = true
        createViewModel()
        collectNews()

        inputPin(testPin)

        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "Error message should be present when verification fails")
        assertTrue(state.errorMessage!!.startsWith("Error:"), "Error message should include error details")
        assertFalse(state.isVerifying, "Verification flag should be reset after error")
        assertEquals(
            0,
            pinAttemptsGuard.onFailedAttemptCallCount,
            "Storage errors should not be recorded as guard attempts",
        )

        assertTrue(
            collectedNews.first() is VerifyPinCodeNews.Error,
            "Error news should be emitted on verification failure",
        )
    }

    @Test
    fun reset_should_cancel_verification_and_logout() = runTest(testDispatcher) {
        var verificationStarted = false
        val blockingStorage = object : PinCodeStorage by pinCodeStorage {
            override suspend fun verifyPinCode(pinCode: String): Boolean {
                verificationStarted = true
                awaitCancellation()
            }
        }
        viewModel = VerifyPinCodeViewModel(blockingStorage, dataCleaner, pinAttemptsGuard, biometricUnlock)
        runCurrent()
        collectNews()

        testPin.forEach { digit ->
            viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(digit.digitToInt()))
        }
        advanceTimeBy(301)
        runCurrent()

        assertTrue(verificationStarted)

        viewModel.onEvent(VerifyPinCodeEvent.Reset)
        advanceUntilIdle()

        assertTrue(viewModel.viewState.first().isLoggingOut)
        assertEquals(VerifyPinCodeNews.LoggedOut, collectedNews.last())
    }

    @Test
    fun still_locked_status_at_countdown_zero_should_restart_ticker() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        pinAttemptsGuard.onFailedAttemptResult = PinLockState.Active(3, 3, 2.seconds)
        createViewModel()
        val states = mutableListOf<VerifyPinCodeViewState>()
        val job = viewModel.viewState.onEach { states.add(it) }.launchIn(backgroundScope)

        inputPin(wrongPin)

        val lockedState = viewModel.viewState.first()
        assertTrue(lockedState.isLocked, "A locked guard result should lock the screen")
        assertEquals(2, lockedState.lockCountdown, "Lock countdown should start from the guard's lock duration")

        pinAttemptsGuard.statusResult = PinLockState.Active(3, 3, 10.seconds)
        advanceTimeBy(2 * 1000)
        runCurrent()

        val restartedState = states.last()
        assertTrue(restartedState.isLocked, "A still-locked status at zero should keep the screen locked")
        assertEquals(10, restartedState.lockCountdown, "The ticker should restart from the re-queried lock duration")

        pinAttemptsGuard.statusResult = PinLockState.Active(0, 3, Duration.ZERO)
        advanceTimeBy(11 * 1000)
        runCurrent()
        job.cancel()

        val unlockedState = states.last()
        assertFalse(unlockedState.isLocked, "The account should unlock once the restarted countdown resolves")
        assertEquals(3, unlockedState.remainingAttempts, "Attempts should reset to the guard's window size")
        assertEquals(0, unlockedState.lockCountdown, "Countdown should be 0 once unlocked")
    }

    @Test
    fun biometric_status_Ready_at_init_should_set_isBiometricAvailable_true() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        createViewModel()

        val state = viewModel.viewState.first()
        assertTrue(state.isBiometricAvailable, "Ready biometric status at init should make biometric unlock available")
    }

    @Test
    fun biometric_status_NotSetUp_at_init_should_keep_isBiometricAvailable_false() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.NotSetUp
        createViewModel()

        val state = viewModel.viewState.first()
        assertFalse(
            state.isBiometricAvailable,
            "NotSetUp biometric status at init should not show the biometric button",
        )
    }

    @Test
    fun should_unlock_with_biometrics_and_forget_failed_attempts() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Success
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        assertEquals(1, pinAttemptsGuard.onSuccessCallCount, "Biometric success should reset the guard's failure count")
        assertTrue(collectedNews.isEmpty(), "A biometric unlock is observed through the session, not through news")
    }

    @Test
    fun biometric_unlock_cancelled_should_have_no_visible_effect() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Cancelled
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        assertTrue(collectedNews.isEmpty(), "A cancelled biometric prompt should not emit news")
        val state = viewModel.viewState.first()
        assertNull(state.errorMessage, "A cancelled biometric prompt should not show an error")
        assertTrue(state.isBiometricAvailable, "A cancelled biometric prompt should keep the biometric button visible")
    }

    @Test
    fun biometric_unlock_invalidated_should_hide_button_and_show_error() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Invalidated
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        assertTrue(collectedNews.isEmpty(), "An invalidated biometric unlock should not emit news")
        val state = viewModel.viewState.first()
        assertFalse(state.isBiometricAvailable, "An invalidated biometric unlock should hide the biometric button")
        assertNotNull(state.errorMessage, "An invalidated biometric unlock should show an error message")
        assertEquals(0, state.failedAttemptId, "An invalidated biometric unlock should not trigger the shake animation")
    }

    @Test
    fun biometric_unlock_error_should_show_error_message() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Error("boom")
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        assertTrue(collectedNews.isEmpty(), "A biometric unlock error should not emit news")
        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "A biometric unlock error should show an error message")
    }

    @Test
    fun biometric_unlock_error_should_not_show_remaining_attempts() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Error("boom")
        createViewModel()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals(
            "Biometric unlock failed. Use your PIN.",
            state.errorMessage,
            "A biometric unlock error should show the biometric error message",
        )
        assertFalse(
            state.showRemainingAttempts,
            "A biometric failure consumes no PIN attempt, so the attempts line must stay hidden",
        )
        assertEquals(3, state.remainingAttempts, "A biometric failure should not change the remaining PIN attempts")
        assertEquals(
            0,
            pinAttemptsGuard.onFailedAttemptCallCount,
            "A biometric failure should not be recorded with the guard",
        )
    }

    @Test
    fun biometric_unlock_throwing_should_release_the_screen_and_show_the_error() = runTest(testDispatcher) {
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockThrowable = IllegalStateException("Biometric unlock broke its contract")
        createViewModel()
        collectNews()

        viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertFalse(state.isVerifying, "A throwing biometric unlock must not leave the screen stuck in verification")
        assertEquals(
            "Biometric unlock failed. Use your PIN.",
            state.errorMessage,
            "A throwing biometric unlock should show the biometric error message",
        )
        assertFalse(state.showRemainingAttempts, "A throwing biometric unlock consumes no PIN attempt")
        assertEquals(
            0,
            pinAttemptsGuard.onFailedAttemptCallCount,
            "A throwing biometric unlock should not be recorded with the guard",
        )
    }

    @Test
    fun biometric_click_should_unlock_while_locked() = runTest(testDispatcher) {
        pinAttemptsGuard.statusResult = PinLockState.Active(3, 3, 25.seconds)
        biometricUnlock.currentStatus = BiometricStatus.Ready
        biometricUnlock.unlockResult = BiometricResult.Success
        createViewModel()
        collectNews()

        try {
            val state = viewModel.viewState.first()
            assertTrue(state.isLocked, "A locked status at init should render the screen as locked")

            viewModel.onEvent(VerifyPinCodeEvent.OnBiometricClick)
            runCurrent()

            assertEquals(1, biometricUnlock.unlockCallCount, "The PIN lockout must not block the biometric prompt")
            assertEquals(1, pinAttemptsGuard.onSuccessCallCount, "Biometric success should reset the guard's failures")
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun escalatingToLockResults() = listOf(
        PinLockState.Active(1, 2, Duration.ZERO),
        PinLockState.Active(2, 1, Duration.ZERO),
        PinLockState.Active(3, 3, 30.seconds),
    )

    private fun TestScope.createViewModel() {
        viewModel = VerifyPinCodeViewModel(pinCodeStorage, dataCleaner, pinAttemptsGuard, biometricUnlock)
        runCurrent()
    }

    private fun TestScope.inputPin(pin: String) {
        pin.forEach { digit ->
            viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(digit.toString().toInt()))
        }
        advanceTimeBy(400)
    }

    private fun TestScope.collectNews() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news ->
                collectedNews.add(news)
            }
        }
    }
}
