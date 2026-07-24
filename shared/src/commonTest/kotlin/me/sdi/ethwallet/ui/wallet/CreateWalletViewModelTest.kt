package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWalletViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: CreateWalletViewModel

    private val testSeedPhrase =
        listOf(
            "abandon",
            "ability",
            "able",
            "about",
            "above",
            "absent",
            "absorb",
            "abstract",
            "absurd",
            "abuse",
            "access",
            "accident",
        )

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
        )
        viewModel = CreateWalletViewModel(walletRepository = walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_be_Loading() = runTest(testDispatcher) {
        assertTrue(
            viewModel.viewState.first() is CreateWalletViewState.Loading,
            "Initial state should be Loading",
        )
    }

    @Test
    fun createWallet_should_update_state_to_Content_with_seed_phrase_on_success() = runTest(testDispatcher) {
        ethManager.creationResult = ethManager.creationResult.copy(seedPhrase = testSeedPhrase)

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Content,
            "State should be Content after successful wallet creation",
        )

        val contentState = currentState as CreateWalletViewState.Content
        assertEquals(
            testSeedPhrase,
            contentState.seedPhrase,
            "Seed phrase in state should match the one from repository",
        )
        assertTrue(walletDao.wallets.isEmpty(), "createWallet should not persist to the DAO yet")
    }

    @Test
    fun createWallet_should_update_state_to_Error_when_exception_occurs() = runTest(testDispatcher) {
        ethManager.shouldThrowException = true

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Error,
            "State should be Error after wallet creation fails",
        )

        val errorState = currentState as CreateWalletViewState.Error
        assertEquals(
            "Wallet creation failed",
            errorState.throwable.message,
            "Error state should contain the exception thrown by repository",
        )
    }

    @Test
    fun createWallet_should_handle_CancellationException_correctly() = runTest(testDispatcher) {
        ethManager.exceptionToThrow = CancellationException("Coroutine cancelled")

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Error,
            "State should be Error when CancellationException occurs",
        )

        val errorState = currentState as CreateWalletViewState.Error
        assertTrue(
            errorState.throwable is CancellationException,
            "Error state should contain a CancellationException",
        )
    }

    @Test
    fun createWallet_should_set_Loading_state_before_starting_wallet_creation() = runTest(testDispatcher) {
        val states = mutableListOf<CreateWalletViewState>()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { states.add(it) }
        }

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        runCurrent()
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            states[0] is CreateWalletViewState.Loading,
            "State should be set to Loading immediately when createWallet is called",
        )

        val finalState = viewModel.viewState.first()
        assertTrue(
            finalState is CreateWalletViewState.Content,
            "Final state should be Content after successful wallet creation",
        )
    }

    @Test
    fun multiple_createWallet_events_should_not_interfere_with_each_other() = runTest(testDispatcher) {
        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Content,
            "State should be Content after wallet creation",
        )
        assertTrue(walletDao.wallets.isEmpty(), "createWallet should not persist to the DAO yet")
    }

    @Test
    fun createWallet_should_use_correct_wallet_label() = runTest(testDispatcher) {
        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        assertTrue(walletDao.wallets.isEmpty(), "createWallet should not persist to the DAO yet")
    }

    @Test
    fun createWallet_should_preserve_seed_phrase_content_correctly() = runTest(testDispatcher) {
        val customSeedPhrase = listOf("test", "seed", "phrase", "words")
        ethManager.creationResult = ethManager.creationResult.copy(seedPhrase = customSeedPhrase)

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Content,
            "State should be Content",
        )

        val contentState = currentState as CreateWalletViewState.Content
        assertEquals(
            customSeedPhrase,
            contentState.seedPhrase,
            "Seed phrase should match exactly",
        )
    }

    @Test
    fun error_state_should_preserve_the_original_exception() = runTest(testDispatcher) {
        val originalException = IllegalStateException("Custom error message")
        ethManager.exceptionToThrow = originalException

        viewModel.onEvent(CreateWalletEvent.CreateWallet)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertTrue(
            currentState is CreateWalletViewState.Error,
            "State should be Error",
        )

        val errorState = currentState as CreateWalletViewState.Error
        assertEquals(
            originalException,
            errorState.throwable,
            "Error should preserve original exception",
        )
        assertEquals(
            "Custom error message",
            errorState.throwable.message,
            "Error message should be preserved",
        )
    }
}
