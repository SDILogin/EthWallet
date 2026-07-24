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
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
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
    private lateinit var walletDao: FakeWalletDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: SetPinCodeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        walletDao = FakeWalletDao()
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = FakeEthManager(),
            privateKeyEncryptor = FakePrivateKeyEncryptor(),
        )
        viewModel = SetPinCodeViewModel(pinCodeStorage, walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_store_PIN_code_when_PINs_match() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")

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
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should emit PinCodeSet news",
        )
        assertTrue(pinCodeStorage.storedPin != null, "PIN code should be stored")
    }

    @Test
    fun should_persist_pending_wallet_before_emitting_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.createWallet("Test Wallet")

        val collector = mutableListOf<SetPinCodeNews>()
        val walletCountWhenNewsArrived = mutableListOf<Int>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                collector.add(it)
                walletCountWhenNewsArrived.add(walletDao.wallets.size)
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

        assertEquals(SetPinCodeNews.PinCodeSet, collector.single(), "Should emit PinCodeSet news")
        assertEquals(
            1,
            walletCountWhenNewsArrived.single(),
            "Pending wallet should already be persisted by the time PinCodeSet news is emitted",
        )
    }

    @Test
    fun should_emit_error_news_and_not_PinCodeSet_when_persist_pending_wallet_fails() = runTest(testDispatcher) {
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

        assertEquals(1, collector.size, "Should emit exactly one news event")
        assertTrue(
            collector.single() is SetPinCodeNews.Error,
            "Should emit Error news, not PinCodeSet, when persistPendingWallet() fails",
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
