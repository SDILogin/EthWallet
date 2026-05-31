package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetPinCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SetPinCodeViewModel
    private lateinit var pinCodeStorage: FakePinCodeStorage

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        viewModel = SetPinCodeViewModel(pinCodeStorage)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_be_empty_with_no_confirmation_mode() = runTest(testDispatcher) {
        val initialState = viewModel.viewState.first()

        assertEquals("", initialState.firstPin, "Initial first PIN should be empty")
        assertEquals("", initialState.secondPin, "Initial second PIN should be empty")
        assertFalse(initialState.isConfirmationMode, "Should not be in confirmation mode initially")
        assertNull(initialState.errorMessage, "Error message should be null initially")
        assertFalse(initialState.isSaving, "Saving state should be false initially")
    }

    @Test
    fun digit_clicks_should_append_to_first_PIN_when_not_in_confirmation_mode() = runTest(testDispatcher) {
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))

        val state = viewModel.viewState.first()
        assertEquals("123", state.firstPin, "First PIN should contain clicked digits")
        assertEquals("", state.secondPin, "Second PIN should still be empty")
        assertFalse(state.isConfirmationMode, "Should still not be in confirmation mode")
    }

    @Test
    fun should_switch_to_confirmation_mode_when_first_PIN_is_complete() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should contain all 6 digits")
        assertEquals("", state.secondPin, "Second PIN should still be empty")
        assertTrue(state.isConfirmationMode, "Should be in confirmation mode")
    }

    @Test
    fun digit_clicks_should_append_to_second_PIN_when_in_confirmation_mode() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }
        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should be intact")
        assertEquals("12", state.secondPin, "Second PIN should contain clicked digits")
        assertTrue(state.isConfirmationMode, "Should be in confirmation mode")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_first_PIN() = runTest(testDispatcher) {
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))
        viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("12", state.firstPin, "Last digit should be removed from first PIN")
    }

    @Test
    fun delete_click_should_remove_last_digit_from_second_PIN_when_in_confirmation_mode() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }

        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should remain intact")
        assertEquals("1", state.secondPin, "Last digit should be removed from second PIN")
    }

    @Test
    fun reset_click_should_clear_second_PIN_and_return_to_first_PIN_entry() = runTest(testDispatcher) {
        for (i in 1..6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
        }

        advanceUntilIdle()
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        viewModel.onEvent(SetPinCodeEvent.OnResetClick)

        val state = viewModel.viewState.first()
        assertEquals("123456", state.firstPin, "First PIN should remain intact")
        assertEquals("", state.secondPin, "Second PIN should be cleared")
        assertFalse(state.isConfirmationMode, "Should not be in confirmation mode anymore")
    }

    @Test
    fun should_emit_PinCodeSet_news_when_PINs_match() = runTest(testDispatcher) {
        val collector = mutableListOf<SetPinCodeNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                collector.add(it)
            }
        }

        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }

        advanceUntilIdle()
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            SetPinCodeNews.PinCodeSet("111111"),
            collector.single(),
            "Should emit PinCodeSet news with correct PIN",
        )
    }

    @Test
    fun should_clear_PINs_and_show_error_when_PINs_dont_match() = runTest(testDispatcher) {
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
        }
        advanceUntilIdle()
        repeat(6) {
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
        }
        advanceUntilIdle()

        val state = viewModel.viewState.first()
        assertEquals("", state.firstPin, "First PIN should be cleared")
        assertEquals("", state.secondPin, "Second PIN should be cleared")
        assertFalse(state.isConfirmationMode, "Should not be in confirmation mode")
        assertEquals("PINs don't match. Try again.", state.errorMessage, "Should show error message")
    }
}
