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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetPinCodeViewModelWithStorageTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pinCodeStorage: FakePinCodeStorage
    private lateinit var viewModel: SetPinCodeViewModel

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
    fun should_store_PIN_code_when_PINs_match() = runTest(testDispatcher) {
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
    fun should_show_error_when_storage_fails() = runTest(testDispatcher) {
        pinCodeStorage.shouldThrowException = true
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

        val state = viewModel.viewState.first()
        assertNotNull(state.errorMessage, "Error message should not be null")
        assertTrue(state.errorMessage!!.isNotEmpty(), "Error message should contain error details")
        assertEquals(1, collector.size)
        assertTrue(collector[0] is SetPinCodeNews.Error, "Should emit Error news")
    }
}
