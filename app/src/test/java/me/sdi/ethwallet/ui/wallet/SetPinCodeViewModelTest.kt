package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetPinCodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SetPinCodeViewModel

    @Before
    fun setUp() {
        viewModel = SetPinCodeViewModel()
    }

    @Test
    fun `initial state should be empty with no confirmation mode`() =
        runTest {
            val initialState = viewModel.viewState.first()

            assertEquals("Initial first PIN should be empty", "", initialState.firstPin)
            assertEquals("Initial second PIN should be empty", "", initialState.secondPin)
            assertFalse("Should not be in confirmation mode initially", initialState.isConfirmationMode)
            assertNull("Error message should be null initially", initialState.errorMessage)
            assertFalse("Saving state should be false initially", initialState.isSaving)
        }

    @Test
    fun `digit clicks should append to first PIN when not in confirmation mode`() =
        runTest {
            // Click digits 1, 2, 3
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))

            val state = viewModel.viewState.first()
            assertEquals("First PIN should contain clicked digits", "123", state.firstPin)
            assertEquals("Second PIN should still be empty", "", state.secondPin)
            assertFalse("Should still not be in confirmation mode", state.isConfirmationMode)
        }

    @Test
    fun `should switch to confirmation mode when first PIN is complete`() =
        runTest {
            // Enter a complete 6-digit PIN
            for (i in 1..6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
            }

            // Need to advance the test dispatcher for the delay to complete
            advanceUntilIdle()

            val state = viewModel.viewState.first()
            assertEquals("First PIN should contain all 6 digits", "123456", state.firstPin)
            assertEquals("Second PIN should still be empty", "", state.secondPin)
            assertTrue("Should be in confirmation mode", state.isConfirmationMode)
        }

    @Test
    fun `digit clicks should append to second PIN when in confirmation mode`() =
        runTest {
            // Enter first PIN
            for (i in 1..6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Now in confirmation mode, enter second PIN
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))

            val state = viewModel.viewState.first()
            assertEquals("First PIN should be intact", "123456", state.firstPin)
            assertEquals("Second PIN should contain clicked digits", "12", state.secondPin)
            assertTrue("Should be in confirmation mode", state.isConfirmationMode)
        }

    @Test
    fun `delete click should remove last digit from first PIN`() =
        runTest {
            // Enter some digits
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))

            // Delete last digit
            viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

            val state = viewModel.viewState.first()
            assertEquals("Last digit should be removed from first PIN", "12", state.firstPin)
        }

    @Test
    fun `delete click should remove last digit from second PIN when in confirmation mode`() =
        runTest {
            // Enter first PIN to get to confirmation mode
            for (i in 1..6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter some digits for second PIN
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))

            // Delete last digit
            viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

            val state = viewModel.viewState.first()
            assertEquals("First PIN should remain intact", "123456", state.firstPin)
            assertEquals("Last digit should be removed from second PIN", "1", state.secondPin)
        }

    @Test
    fun `reset click should clear second PIN and return to first PIN entry`() =
        runTest {
            // Enter first PIN to get to confirmation mode
            for (i in 1..6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(i))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter some digits for second PIN
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))

            // Reset
            viewModel.onEvent(SetPinCodeEvent.OnResetClick)

            val state = viewModel.viewState.first()
            assertEquals("First PIN should remain intact", "123456", state.firstPin)
            assertEquals("Second PIN should be cleared", "", state.secondPin)
            assertFalse("Should not be in confirmation mode anymore", state.isConfirmationMode)
        }

    @Test
    fun `should emit PinCodeSet news when PINs match`() =
        runTest {
            val collector = mutableListOf<SetPinCodeNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    collector.add(it)
                }
            }

            // Enter first PIN (all 1s)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter second PIN (all 1s to match)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete validation and emit news
            advanceUntilIdle()

            val state = viewModel.viewState.first()
            assertTrue("Should be in saving state", state.isSaving)
            assertEquals(
                "Should emit PinCodeSet news with correct PIN",
                SetPinCodeNews.PinCodeSet("111111"),
                collector.single(),
            )
        }

    @Test
    fun `should clear PINs and show error when PINs don't match`() =
        runTest {
            // Enter first PIN (all 1s)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter second PIN (all 2s, won't match)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(2))
            }

            // Need to advance for the delay to complete validation
            advanceUntilIdle()

            val state = viewModel.viewState.first()
            assertEquals("First PIN should be cleared", "", state.firstPin)
            assertEquals("Second PIN should be cleared", "", state.secondPin)
            assertFalse("Should not be in confirmation mode", state.isConfirmationMode)
            assertEquals("Should show error message", "PINs don't match. Try again.", state.errorMessage)
        }

    @Test
    fun `should not process digit clicks when in saving state`() =
        runTest {
            // Enter first PIN (all 1s)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter second PIN (all 1s to match)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete validation
            advanceUntilIdle()

            // Now in saving state, try to click more digits
            viewModel.onEvent(SetPinCodeEvent.OnDigitClick(3))

            val state = viewModel.viewState.first()
            assertEquals("First PIN should remain unchanged", "111111", state.firstPin)
            assertEquals("Second PIN should remain unchanged", "111111", state.secondPin)
            assertTrue("Should still be in saving state", state.isSaving)
        }

    @Test
    fun `should not process delete clicks when in saving state`() =
        runTest {
            // Enter first PIN (all 1s)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete
            advanceUntilIdle()

            // Enter second PIN (all 1s to match)
            repeat(6) {
                viewModel.onEvent(SetPinCodeEvent.OnDigitClick(1))
            }

            // Need to advance for the delay to complete validation
            advanceUntilIdle()

            // Now in saving state, try to use delete
            viewModel.onEvent(SetPinCodeEvent.OnDeleteClick)

            val state = viewModel.viewState.first()
            assertEquals("First PIN should remain unchanged", "111111", state.firstPin)
            assertEquals("Second PIN should remain unchanged", "111111", state.secondPin)
            assertTrue("Should still be in saving state", state.isSaving)
        }
}
