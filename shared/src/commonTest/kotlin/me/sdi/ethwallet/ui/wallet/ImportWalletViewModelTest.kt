package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWalletViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: ImportWalletViewModel

    private val validMnemonic =
        "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        ethManager = FakeEthManager()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = privateKeyEncryptor,
            historyInitializationStore = HistoryInitializationStoreInMemory(),
        )
        viewModel = ImportWalletViewModel(walletRepository = walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun import_should_be_disabled_until_twelve_words_are_entered() = runTest(testDispatcher) {
        viewModel.onEvent(ImportWalletEvent.LabelChanged("Imported wallet"))
        viewModel.onEvent(ImportWalletEvent.MnemonicChanged("abandon abandon about"))
        assertFalse(
            viewModel.viewState.value.isImportEnabled,
            "Import should be disabled for fewer than 12 words",
        )

        viewModel.onEvent(ImportWalletEvent.MnemonicChanged(validMnemonic))
        assertTrue(
            viewModel.viewState.value.isImportEnabled,
            "Import should be enabled for 12 non-empty words",
        )
    }

    @Test
    fun import_success_should_store_the_wallet_and_emit_WalletImported_news() = runTest(testDispatcher) {
        val news = mutableListOf<ImportWalletNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news.add(it) }
        }

        viewModel.onEvent(ImportWalletEvent.LabelChanged("  Imported   wallet  "))
        viewModel.onEvent(ImportWalletEvent.MnemonicChanged(validMnemonic))
        viewModel.onEvent(ImportWalletEvent.ImportClicked)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, walletDao.wallets.size, "importWallet should persist the imported wallet")
        assertEquals("Imported wallet", walletDao.wallets.single().label)
        assertTrue(news.contains(ImportWalletNews.WalletImported), "WalletImported news should be emitted")
        assertFalse(viewModel.viewState.value.isError, "State should not be an error on success")
    }

    @Test
    fun import_failure_should_set_error_state_and_not_emit_news() = runTest(testDispatcher) {
        ethManager.exceptionToThrow = IllegalArgumentException("Invalid recovery phrase")
        val news = mutableListOf<ImportWalletNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news.add(it) }
        }

        viewModel.onEvent(ImportWalletEvent.LabelChanged("Imported wallet"))
        viewModel.onEvent(ImportWalletEvent.MnemonicChanged(validMnemonic))
        viewModel.onEvent(ImportWalletEvent.ImportClicked)
        advanceUntilIdle()
        job.cancel()

        assertTrue(viewModel.viewState.value.isError, "State should be an error when import fails")
        assertEquals(
            "Invalid recovery phrase",
            viewModel.viewState.value.errorMessage,
            "Error message should be surfaced from the exception",
        )
        assertTrue(news.isEmpty(), "No news should be emitted on failure")
        assertTrue(walletDao.wallets.isEmpty(), "No wallet should be stored on failure")
    }

    @Test
    fun import_should_rethrow_CancellationException_without_showing_an_error() = runTest(testDispatcher) {
        ethManager.exceptionToThrow = CancellationException("cancelled")

        viewModel.onEvent(ImportWalletEvent.LabelChanged("Imported wallet"))
        viewModel.onEvent(ImportWalletEvent.MnemonicChanged(validMnemonic))
        viewModel.onEvent(ImportWalletEvent.ImportClicked)
        advanceUntilIdle()

        assertFalse(
            viewModel.viewState.value.isError,
            "CancellationException should not be converted into an error state",
        )
    }

    @Test
    fun import_requires_a_valid_wallet_name() = runTest(testDispatcher) {
        viewModel.onEvent(ImportWalletEvent.MnemonicChanged(validMnemonic))
        assertFalse(viewModel.viewState.value.isImportEnabled)

        viewModel.onEvent(ImportWalletEvent.LabelChanged("   "))
        assertFalse(viewModel.viewState.value.isImportEnabled)

        viewModel.onEvent(ImportWalletEvent.LabelChanged("x".repeat(MAX_WALLET_LABEL_LENGTH)))
        assertTrue(viewModel.viewState.value.isImportEnabled)

        viewModel.onEvent(ImportWalletEvent.LabelChanged("x".repeat(MAX_WALLET_LABEL_LENGTH + 1)))
        assertFalse(viewModel.viewState.value.isImportEnabled)
    }
}
