package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: VerifyPinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var dataCleaner: FakeDataCleaner
    private val testPin = "123456"
    private val wrongPin = "000000"

    private val collectedNews = mutableListOf<VerifyPinCodeNews>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        dataCleaner = FakeDataCleaner()
        viewModel = VerifyPinCodeViewModel(pinCodeStorage, dataCleaner)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_have_default_values() = runTest(testDispatcher) {
        val initialState = viewModel.viewState.first()

        assertEquals("", initialState.enteredPin, "Initial PIN should be empty")
        assertNull(initialState.errorMessage, "No error message should be present initially")
        assertEquals(3, initialState.remainingAttempts, "Initial attempts should be set to MAX_ATTEMPTS (3)")
        assertFalse(initialState.isVerifying, "Verification should not be in progress initially")
        assertFalse(initialState.isLoggingOut, "Logout should not be in progress")
        assertFalse(initialState.isLocked, "Lock state should be inactive initially")
        assertEquals(0, initialState.lockCountdown, "Lock countdown should be zero initially")
    }

    @Test
    fun should_verify_PIN_successfully_and_emit_PinVerified_event() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        collectNews()

        inputPin(testPin)

        assertEquals(
            VerifyPinCodeNews.PinVerified,
            collectedNews.firstOrNull(),
            "Success should emit PinVerified news",
        )

        val finalState = viewModel.viewState.first()
        assertTrue(finalState.isVerifying, "Verification flag should remain set until navigation")
        assertEquals(testPin, finalState.enteredPin, "Entered PIN should remain complete until navigation")
        assertEquals(3, finalState.remainingAttempts, "Successful verification should not change attempts")
        assertFalse(finalState.isLocked, "Successful verification should not lock the screen")
        assertNull(finalState.errorMessage, "No error message should be present after successful PIN")
    }

    @Test
    fun should_ignore_input_after_successful_verification_until_reset() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
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
        inputPin("123")

        val state = viewModel.viewState.first()
        assertEquals("123", state.enteredPin, "Digits should be appended to PIN")
        assertFalse(state.isVerifying, "Verification shouldn't start until PIN is complete")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_PIN() = runTest(testDispatcher) {
        inputPin("123")
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("12", state.enteredPin, "Delete should remove the last entered digit")
    }

    @Test
    fun delete_click_should_have_no_effect_when_PIN_is_empty() = runTest(testDispatcher) {
        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Delete should have no effect on empty PIN")
    }

    @Test
    fun invalid_PIN_verification_should_decrement_remaining_attempts() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        inputPin(wrongPin)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Pin must be reset after wrong attempt")
        assertFalse(state.errorMessage.isNullOrBlank(), "Error message after invalid pin input")
        assertEquals(2, state.remainingAttempts, "remainingAttempts must change after wrong pin input")
        assertFalse(state.isVerifying, "Flag isVerifying must be reset after pin check to unlock numpad")
        assertFalse(state.isLocked, "User must be able to input pin again when there are 2 more attempts left")
    }

    @Test
    fun entered_pin_should_remain_visible_immediately_after_wrong_attempt() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
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
        inputPin(testPin)

        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "Error message should be present when verification fails")
        assertEquals("", state.enteredPin, "Entered PIN should clear immediately on storage errors")
        assertEquals(0, state.failedAttemptId, "Storage errors are not wrong-PIN attempts")
    }

    @Test
    fun entered_pin_should_clear_after_error_delay() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        inputPin(wrongPin)
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Entered PIN should clear once the error delay elapses")
        assertNotNull(state.errorMessage, "Error message should remain visible after the PIN clears")
    }

    @Test
    fun second_wrong_pin_should_increment_failedAttemptId() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val state = viewModel.viewState.first()
        assertEquals(2, state.failedAttemptId, "Second wrong attempt should bump failedAttemptId to 2")
    }

    @Test
    fun digit_click_during_error_window_should_replace_stale_pin() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        inputPin(wrongPin)

        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(5))

        val state = viewModel.viewState.first()
        assertEquals("5", state.enteredPin, "A fresh digit should replace the stale failed PIN")
        assertNull(state.errorMessage, "Error message should clear when starting a fresh attempt")
    }

    @Test
    fun delete_click_during_error_window_should_clear_stale_pin() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        inputPin(wrongPin)

        viewModel.onEvent(VerifyPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("", state.enteredPin, "Delete should clear the stale failed PIN")
        assertNull(state.errorMessage, "Error message should clear when deleting the stale PIN")
    }

    @Test
    fun third_wrong_pin_should_lock_and_clear_pin_after_delay() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
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
    fun handleInvalidPin_should_decrement_remaining_attempts_until_locked() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
        val initialState = viewModel.viewState.first()
        assertEquals(3, initialState.remainingAttempts, "Initial attempts should be MAX_ATTEMPTS (3)")

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
        val states = mutableListOf<VerifyPinCodeViewState>()
        val job = viewModel.viewState.onEach { states.add(it) }.launchIn(backgroundScope)

        inputPin(wrongPin)
        inputPin(wrongPin)
        inputPin(wrongPin)

        val lockedState = states.firstOrNull { it.isLocked && it.remainingAttempts == 0 }
        assertNotNull(lockedState, "A locked state should exist")
        assertTrue(lockedState.lockCountdown > 0, "Countdown should be active")

        advanceTimeBy(31 * 1000)
        advanceUntilIdle()
        job.cancel()

        val unlockedState = states.lastOrNull()
        assertNotNull(unlockedState, "An unlocked state should exist after countdown")
        assertFalse(unlockedState.isLocked, "Account should be unlocked")
        assertEquals(3, unlockedState.remainingAttempts, "Attempts should reset to 3")
        assertEquals(0, unlockedState.lockCountdown, "Countdown should be 0")
    }

    @Test
    fun reset_should_logout_when_pin_screen_is_locked() = runTest(testDispatcher) {
        pinCodeStorage.storePinCode(testPin)
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
        collectNews()

        inputPin(testPin)

        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "Error message should be present when verification fails")
        assertTrue(state.errorMessage!!.startsWith("Error:"), "Error message should include error details")
        assertFalse(state.isVerifying, "Verification flag should be reset after error")

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
        viewModel = VerifyPinCodeViewModel(blockingStorage, dataCleaner)
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
