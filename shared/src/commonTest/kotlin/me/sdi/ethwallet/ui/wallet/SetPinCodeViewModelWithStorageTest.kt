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
import me.sdi.ethwallet.fakes.FakeBiometricUnlock
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePinCodeStorage
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
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
    private lateinit var ethManager: FakeEthManager
    private lateinit var walletRepository: WalletRepository
    private lateinit var biometricUnlock: FakeBiometricUnlock
    private lateinit var viewModel: SetPinCodeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinCodeStorage = FakePinCodeStorage()
        walletDao = FakeWalletDao()
        ethManager = FakeEthManager()
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = FakePrivateKeyEncryptor(),
            historyInitializationStore = HistoryInitializationStoreInMemory(),
        )
        biometricUnlock = FakeBiometricUnlock()
        viewModel = SetPinCodeViewModel(pinCodeStorage, walletRepository, biometricUnlock)
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
            SetPinCodeNews.PinCodeSet,
            collector.single(),
            "Should emit PinCodeSet news",
        )
        assertTrue(pinCodeStorage.storedPin != null, "PIN code should be stored")
    }

    @Test
    fun should_leave_already_persisted_wallet_unchanged_before_emitting_PinCodeSet() = runTest(testDispatcher) {
        walletRepository.saveWallet(ethManager.creationResult, "Test Wallet")

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
            "Setting a PIN should not change the already persisted wallet",
        )
    }

    @Test
    fun should_emit_PinCodeSet_when_wallet_storage_is_unavailable() = runTest(testDispatcher) {
        walletDao.shouldThrowException = true
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

        assertEquals(listOf<SetPinCodeNews>(SetPinCodeNews.PinCodeSet), collector)
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
