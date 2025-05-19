package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.security.FakePinCodeStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetPinCodeViewModelWithStorageTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var viewModel: SetPinCodeViewModel

    @Before
    fun setUp() {
        pinCodeStorage = FakePinCodeStorage()
        viewModel = SetPinCodeViewModel(pinCodeStorage)
    }

    @Test
    fun `should store PIN code when PINs match`() =
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
    fun `should show error when storage fails`() =
        runTest {
            val collector = mutableListOf<SetPinCodeNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    collector.add(it)
                }
            }

            // Set storage to throw exception
            pinCodeStorage.setShouldThrowException(true)

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
            assertNotNull("Error message should not be null", state.errorMessage)
            assertTrue("Error message should contain error details", state.errorMessage!!.isNotEmpty())
            assertEquals(1, collector.size)
            assertTrue("Should emit Error news", collector[0] is SetPinCodeNews.Error)
        }
}
