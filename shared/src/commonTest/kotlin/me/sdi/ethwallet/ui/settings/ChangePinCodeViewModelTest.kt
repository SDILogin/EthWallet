package me.sdi.ethwallet.ui.settings

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.fakes.FakeDataCleaner
import me.sdi.ethwallet.fakes.FakePinAttemptsGuard
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ChangePinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var pinAttemptsGuard: FakePinAttemptsGuard
    private lateinit var dataCleaner: FakeDataCleaner

    private val collectedNews = mutableListOf<ChangePinCodeNews>()

    @BeforeTest
    fun setUp() {
        Logger.enabled = true
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage().apply { storedPin = CURRENT_PIN }
        pinAttemptsGuard = FakePinAttemptsGuard()
        dataCleaner = FakeDataCleaner()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun entering_current_new_and_matching_confirmation_changes_the_pin() = runTest(testDispatcher) {
        createViewModel()
        collectNews()

        enterPin(CURRENT_PIN)
        assertEquals(ChangePinCodeStep.EnterNew, viewModel.viewState.value.step, "New PIN step should follow")
        enterPin(NEW_PIN)
        assertEquals(ChangePinCodeStep.ConfirmNew, viewModel.viewState.value.step, "Confirmation step should follow")
        enterPin(NEW_PIN)

        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.PinChanged), collectedNews, "User should be told the PIN changed")
        assertEquals(NEW_PIN, pinCodeStorage.storedPin, "The new PIN should replace the current one")
        assertEquals(1, pinCodeStorage.changePinCalledCount, "The current PIN should be checked only once")
        assertEquals(1, pinAttemptsGuard.onSuccessCallCount, "A successful change should reset failed attempts")
        assertEquals(0, pinAttemptsGuard.onFailedAttemptCallCount, "A successful change must not count a failure")
    }

    @Test
    fun mismatched_confirmation_returns_to_new_pin_step_and_keeps_current_pin() = runTest(testDispatcher) {
        createViewModel()
        collectNews()

        enterPin(CURRENT_PIN)
        enterPin(NEW_PIN)
        enterPin(OTHER_PIN)

        val state = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.EnterNew, state.step, "Mismatch should send the user back to the new PIN step")
        assertEquals("", state.newPin, "The rejected new PIN should be cleared")
        assertEquals("", state.confirmPin, "The rejected confirmation should be cleared")
        assertEquals(CURRENT_PIN, state.currentPin, "User should not have to re-enter the current PIN")
        assertEquals("PINs don't match. Try again.", state.errorMessage, "User should see the mismatch error")
        assertEquals(0, pinCodeStorage.changePinCalledCount, "Nothing should be saved on a mismatch")

        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        assertEquals(NEW_PIN, pinCodeStorage.storedPin, "Retrying with a matching confirmation should change the PIN")
        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.PinChanged), collectedNews, "User should be told the PIN changed")
    }

    @Test
    fun new_pin_equal_to_current_pin_is_rejected_before_confirmation() = runTest(testDispatcher) {
        createViewModel()

        enterPin(CURRENT_PIN)
        enterPin(CURRENT_PIN)

        val state = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.EnterNew, state.step, "User should stay on the new PIN step")
        assertEquals("", state.newPin, "The rejected new PIN should be cleared")
        assertEquals(
            "New PIN must be different from the current PIN",
            state.errorMessage,
            "User should be told the new PIN must differ",
        )
        assertEquals(0, pinCodeStorage.changePinCalledCount, "Nothing should be saved for an unchanged PIN")
    }

    @Test
    fun wrong_current_pin_counts_an_attempt_and_returns_to_first_step() = runTest(testDispatcher) {
        createViewModel()
        collectNews()

        enterPin(WRONG_PIN)
        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        val state = viewModel.viewState.value
        assertEquals(1, pinAttemptsGuard.onFailedAttemptCallCount, "A wrong current PIN should count as a failed attempt")
        assertEquals(ChangePinCodeStep.EnterCurrent, state.step, "User should be sent back to enter the current PIN")
        assertEquals("", state.currentPin, "The wrong current PIN should be cleared")
        assertEquals("", state.newPin, "The new PIN should be discarded")
        assertEquals("", state.confirmPin, "The confirmation should be discarded")
        assertEquals("Incorrect PIN code", state.errorMessage, "User should see the incorrect PIN error")
        assertTrue(state.showRemainingAttempts, "User should see how many attempts remain")
        assertEquals(2, state.remainingAttempts, "Remaining attempts should come from the guard")
        assertFalse(state.isSaving, "Keypad should be usable again after the failure")
        assertEquals(CURRENT_PIN, pinCodeStorage.storedPin, "A wrong current PIN must not change the stored PIN")
        assertTrue(collectedNews.isEmpty(), "No navigation should happen after a wrong current PIN")
    }

    @Test
    fun initial_lockout_blocks_the_keypad_until_the_countdown_ends() = runTest(testDispatcher) {
        pinAttemptsGuard.statusResult = PinLockState.Active(3, 3, 25.seconds)
        createViewModel()

        val lockedState = viewModel.viewState.value
        assertTrue(lockedState.isLocked, "An active lockout should lock the screen on open")
        assertEquals(25, lockedState.lockCountdown, "Countdown should mirror the guard's remaining lock time")
        assertFalse(lockedState.isKeypadEnabled, "Keypad should be disabled while locked")

        viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(1))
        assertEquals("", viewModel.viewState.value.enteredPin, "Digits should be ignored while locked")

        pinAttemptsGuard.statusResult = PinLockState.Active(3, 3, Duration.ZERO)
        advanceTimeBy(26.seconds)
        runCurrent()

        assertFalse(viewModel.viewState.value.isLocked, "Screen should unlock once the guard reports no lock")
        viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(1))
        assertEquals("1", viewModel.viewState.value.enteredPin, "Digits should be accepted after the lockout")
    }

    @Test
    fun repeated_wrong_current_pins_lock_the_screen() = runTest(testDispatcher) {
        pinAttemptsGuard.onFailedAttemptResults += listOf(
            PinLockState.Active(1, 2, Duration.ZERO),
            PinLockState.Active(2, 1, Duration.ZERO),
            PinLockState.Active(3, 3, 30.seconds),
        )
        createViewModel()

        repeat(3) {
            enterPin(WRONG_PIN)
            enterPin(NEW_PIN)
            enterPin(NEW_PIN)
        }

        val state = viewModel.viewState.value
        assertEquals(3, pinAttemptsGuard.onFailedAttemptCallCount, "Every wrong current PIN should be counted")
        assertTrue(state.isLocked, "The screen should lock after the guard reports a lockout")
        assertEquals(30, state.lockCountdown, "Countdown should mirror the guard's lock duration")
        assertEquals("Too many failed attempts", state.errorMessage, "User should be told about the lockout")

        viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(1))
        assertEquals("", viewModel.viewState.value.enteredPin, "Digits should be ignored while locked")

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tamper_reported_on_a_failed_attempt_wipes_data_and_logs_out() = runTest(testDispatcher) {
        pinAttemptsGuard.onFailedAttemptResult = PinLockState.Tampered
        createViewModel()
        collectNews()

        enterPin(WRONG_PIN)
        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        assertEquals(1, dataCleaner.cleanCalledCount, "Tampering should wipe local data")
        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.LoggedOut), collectedNews, "User should be logged out")
        assertTrue(viewModel.viewState.value.isLoggingOut, "Keypad should stay disabled during logout")
    }

    @Test
    fun tamper_reported_on_open_wipes_data_and_logs_out() = runTest(testDispatcher) {
        pinAttemptsGuard.statusResult = PinLockState.Tampered
        viewModel = ChangePinCodeViewModel(pinCodeStorage, pinAttemptsGuard, dataCleaner)
        collectNews()
        runCurrent()

        assertEquals(1, dataCleaner.cleanCalledCount, "Tampering should wipe local data")
        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.LoggedOut), collectedNews, "User should be logged out")
    }

    @Test
    fun keypad_ignores_input_while_saving() = runTest(testDispatcher) {
        val changeResult = CompletableDeferred<Boolean>()
        val blockingStorage = object : PinCodeStorage by pinCodeStorage {
            override suspend fun changePinCode(currentPin: String, newPin: String): Boolean = changeResult.await()
        }
        viewModel = ChangePinCodeViewModel(blockingStorage, pinAttemptsGuard, dataCleaner)
        runCurrent()
        collectNews()

        enterPin(CURRENT_PIN)
        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        assertTrue(viewModel.viewState.value.isSaving, "Saving should be in progress")
        assertFalse(viewModel.viewState.value.isKeypadEnabled, "Keypad should be disabled while saving")

        viewModel.onEvent(ChangePinCodeEvent.OnDeleteClick)
        viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(1))
        runCurrent()

        val savingState = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.ConfirmNew, savingState.step, "Input while saving must not change the step")
        assertEquals(NEW_PIN, savingState.confirmPin, "Input while saving must not change the confirmation")

        changeResult.complete(true)
        runCurrent()

        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.PinChanged), collectedNews, "User should be told the PIN changed")
    }

    private fun TestScope.createViewModel() {
        viewModel = ChangePinCodeViewModel(pinCodeStorage, pinAttemptsGuard, dataCleaner)
        runCurrent()
    }

    private fun TestScope.enterPin(pin: String) {
        pin.forEach { digit ->
            viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(digit.digitToInt()))
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

    private companion object {
        private const val CURRENT_PIN = "123456"
        private const val NEW_PIN = "654321"
        private const val OTHER_PIN = "111111"
        private const val WRONG_PIN = "000000"
    }
}
