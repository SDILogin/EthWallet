package me.sdi.ethwallet.ui.wallet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.data.repository.WalletRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWalletViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mock dependencies
    private lateinit var walletRepository: WalletRepository

    // Class under test
    private lateinit var viewModel: CreateWalletViewModel

    // Test data
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

    @Before
    fun setUp() {
        walletRepository = mockk()
        viewModel = CreateWalletViewModel(walletRepository = walletRepository)
    }

    @Test
    fun `initial state should be Loading`() =
        runTest {
            assertTrue(
                "Initial state should be Loading",
                viewModel.viewState.first() is CreateWalletViewState.Loading,
            )
        }

    @Test
    fun `createWallet should update state to Content with seed phrase on success`() =
        runTest {
            coEvery { walletRepository.createWallet(any()) } returns testSeedPhrase

            viewModel.onEvent(CreateWalletEvent.CreateWallet)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertTrue(
                "State should be Content after successful wallet creation",
                currentState is CreateWalletViewState.Content,
            )

            val contentState = currentState as CreateWalletViewState.Content
            assertEquals(
                "Seed phrase in state should match the one from repository",
                testSeedPhrase,
                contentState.seedPhrase,
            )
            coVerify(exactly = 1) { walletRepository.createWallet(label = "My Wallet") }
        }

    @Test
    fun `createWallet should update state to Error when exception occurs`() =
        runTest {
            val testException = RuntimeException("Wallet creation failed")
            coEvery { walletRepository.createWallet(any()) } throws testException

            viewModel.onEvent(CreateWalletEvent.CreateWallet)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertTrue(
                "State should be Error after wallet creation fails",
                currentState is CreateWalletViewState.Error,
            )

            val errorState = currentState as CreateWalletViewState.Error
            assertEquals(
                "Error state should contain the exception thrown by repository",
                testException,
                errorState.throwable,
            )
            coVerify(exactly = 1) { walletRepository.createWallet(label = "My Wallet") }
        }

    @Test
    fun `createWallet should handle CancellationException correctly`() =
        runTest {
            coEvery { walletRepository.createWallet(any()) } throws CancellationException("Coroutine cancelled")

            viewModel.onEvent(CreateWalletEvent.CreateWallet)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertTrue(
                "State should be Error when CancellationException occurs",
                currentState is CreateWalletViewState.Error,
            )

            val errorState = currentState as CreateWalletViewState.Error
            assertTrue(
                "Error state should contain a CancellationException",
                errorState.throwable is CancellationException,
            )

            coVerify(exactly = 1) { walletRepository.createWallet(label = "My Wallet") }
        }

    @Test
    fun `createWallet should set Loading state before starting wallet creation`() =
        runTest {
            val states = mutableListOf<CreateWalletViewState>()
            coEvery { walletRepository.createWallet(any()) } coAnswers {
                val state = viewModel.viewState.first()
                states.add(state)
                testSeedPhrase
            }

            viewModel.onEvent(CreateWalletEvent.CreateWallet)
            runCurrent()

            assertTrue(
                "State should be set to Loading immediately when createWallet is called",
                states[0] is CreateWalletViewState.Loading,
            )

            advanceUntilIdle()

            val finalState = viewModel.viewState.first()
            assertTrue(
                "Final state should be Content after successful wallet creation",
                finalState is CreateWalletViewState.Content,
            )
        }
}
