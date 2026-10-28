package me.sdi.ethwallet.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ChangePinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage

    private val collectedNews = mutableListOf<ChangePinCodeNews>()

    @BeforeTest
    fun setUp() {
        Logger.enabled = true
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage().apply { storedPin = CURRENT_PIN }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun entering_new_pin_and_matching_confirmation_changes_the_pin() = runTest(testDispatcher) {
        createViewModel()
        collectNews()

        assertEquals(ChangePinCodeStep.EnterNew, viewModel.viewState.value.step, "Flow should start with the new PIN")
        enterPin(NEW_PIN)
        assertEquals(ChangePinCodeStep.ConfirmNew, viewModel.viewState.value.step, "Confirmation step should follow")
        enterPin(NEW_PIN)

        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.PinChanged), collectedNews, "User should be told the PIN changed")
        assertEquals(NEW_PIN, pinCodeStorage.storedPin, "The new PIN should replace the current one")
        assertEquals(1, pinCodeStorage.changePinCalledCount, "The PIN should be saved once")
    }

    @Test
    fun mismatched_confirmation_returns_to_new_pin_step_and_shakes_the_indicator() = runTest(testDispatcher) {
        createViewModel()
        collectNews()

        enterPin(NEW_PIN)
        enterPin(OTHER_PIN)

        val state = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.EnterNew, state.step, "Mismatch should send the user back to the new PIN step")
        assertEquals("", state.newPin, "The rejected new PIN should be cleared")
        assertEquals("", state.confirmPin, "The rejected confirmation should be cleared")
        assertEquals(ChangePinCodeError.PinsDoNotMatch, state.error, "User should see the mismatch error")
        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.ShakePinInput), collectedNews, "The indicator should shake on a mismatch")
        assertEquals(0, pinCodeStorage.changePinCalledCount, "Nothing should be saved on a mismatch")

        enterPin(NEW_PIN)
        assertNull(viewModel.viewState.value.error, "The error should clear once the user types again")
        enterPin(NEW_PIN)

        assertEquals(NEW_PIN, pinCodeStorage.storedPin, "Retrying with a matching confirmation should change the PIN")
        assertEquals(ChangePinCodeNews.PinChanged, collectedNews.last(), "User should be told the PIN changed")
    }

    @Test
    fun storage_failure_restarts_the_flow_with_an_error() = runTest(testDispatcher) {
        pinCodeStorage.shouldThrowException = true
        createViewModel()
        collectNews()

        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        val state = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.EnterNew, state.step, "User should start over after a failed save")
        assertEquals("", state.newPin, "The unsaved new PIN should be cleared")
        assertEquals(ChangePinCodeError.Unknown, state.error, "User should see that something went wrong")
        assertFalse(state.isSaving, "Keypad should be usable again after the failure")
        assertEquals(
            listOf<ChangePinCodeNews>(ChangePinCodeNews.ShakePinInput),
            collectedNews,
            "No navigation should happen after a failed save",
        )
        assertEquals(CURRENT_PIN, pinCodeStorage.storedPin, "A failed save must keep the current PIN")
    }

    @Test
    fun keypad_ignores_input_while_saving() = runTest(testDispatcher) {
        val changeResult = CompletableDeferred<Unit>()
        val blockingStorage = object : PinCodeStorage by pinCodeStorage {
            override suspend fun changePinCode(newPin: String) = changeResult.await()
        }
        viewModel = ChangePinCodeViewModel(blockingStorage)
        runCurrent()
        collectNews()

        enterPin(NEW_PIN)
        enterPin(NEW_PIN)

        assertTrue(viewModel.viewState.value.isSaving, "Saving should be in progress")

        viewModel.onEvent(ChangePinCodeEvent.OnDeleteClick)
        viewModel.onEvent(ChangePinCodeEvent.OnDigitClick(1))
        runCurrent()

        val savingState = viewModel.viewState.value
        assertEquals(ChangePinCodeStep.ConfirmNew, savingState.step, "Input while saving must not change the step")
        assertEquals(NEW_PIN, savingState.confirmPin, "Input while saving must not change the confirmation")

        changeResult.complete(Unit)
        runCurrent()

        assertEquals(listOf<ChangePinCodeNews>(ChangePinCodeNews.PinChanged), collectedNews, "User should be told the PIN changed")
    }

    private fun TestScope.createViewModel() {
        viewModel = ChangePinCodeViewModel(pinCodeStorage)
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
    }
}
