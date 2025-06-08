package me.sdi.ethwallet.ui.wallet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.ui.wallet.WalletScreenNews.NavigateToOnBoarding
import me.sdi.ethwallet.ui.wallet.WalletScreenNews.NavigateToWalletDetails
import me.sdi.ethwallet.ui.wallet.WalletsListEvent.OnBackPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: WalletsListViewModel
    private val testWallet1 =
        EthWallet(
            address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
            label = "My Primary Wallet",
        )

    private val testWallet2 =
        EthWallet(
            address = EthAddress("0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"),
            label = "My Secondary Wallet",
        )

    private val testWallets = listOf(testWallet1, testWallet2)

    @Before
    fun setUp() {
        walletRepository = mockk(relaxed = true)
        coEvery { walletRepository.getAllWallets() } returns testWallets

        viewModel = WalletsListViewModel(walletRepository = walletRepository)
    }

    @Test
    fun `initial state should show loading state`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            val initialState = viewModel.uiState.first()
            assertTrue("Initial state should have isLoading = true", initialState.isLoading)
            assertEquals("Initial state should have empty wallet list", emptyList<EthWallet>(), initialState.otherWallets)
            assertTrue("Initial state should have no navigation target", news.isEmpty())
        }

    @Test
    fun `loadWallets should fetch wallets from repository and update state`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            viewModel.onEvent(WalletsListEvent.Start)
            advanceUntilIdle()

            val currentState = viewModel.uiState.first()
            assertFalse("Loading state should be false after initialization", currentState.isLoading)
            assertEquals("Wallet list should be updated with test wallets", testWallets, currentState.otherWallets)
            assertTrue("Navigation state should remain null", news.isEmpty())
        }

    @Test
    fun `loadWallets should handle empty wallet list correctly`() =
        runTest {
            coEvery { walletRepository.getAllWallets() } returns emptyList()

            viewModel.onEvent(WalletsListEvent.Start)
            advanceUntilIdle()

            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            val currentState = viewModel.uiState.first()
            assertFalse("Loading state should be false after initialization", currentState.isLoading)
            assertEquals("Wallet list should be empty", emptyList<WalletEntity>(), currentState.otherWallets)
            assertTrue("Navigation state should remain null", news.isEmpty())
        }

    @Test
    fun `loadWallets should handle repository exceptions`() =
        runTest {
            coEvery { walletRepository.getAllWallets() } throws RuntimeException("Repository failure")

            viewModel.onEvent(WalletsListEvent.Start)
            advanceUntilIdle()

            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            val currentState = viewModel.uiState.first()
            assertFalse("Loading state should be false after error", currentState.isLoading)
            assertEquals("Wallet list should remain empty after error", emptyList<EthWallet>(), currentState.otherWallets)
            assertTrue("Navigation state should remain null", news.isEmpty())
        }

    @Test
    fun `setActiveWallet should update active wallet through repository and reload wallet list`() =
        runTest {
            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(WalletsListEvent.SetActiveWallet(testWallet2.address))
            advanceUntilIdle()

            coVerify(exactly = 1) { walletRepository.setActiveWallet(testWallet2.address) }
        }

    @Test
    fun `setActiveWallet should handle repository exceptions`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            advanceUntilIdle()

            val targetAddress = testWallet2.address
            coEvery { walletRepository.setActiveWallet(targetAddress) } throws RuntimeException("Setting active wallet failed")

            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(WalletsListEvent.SetActiveWallet(targetAddress))
            advanceUntilIdle()

            coVerify(exactly = 1) { walletRepository.setActiveWallet(targetAddress) }

            val currentState = viewModel.uiState.first()
            assertEquals("Wallet list should not change after error", testWallets, currentState.otherWallets)
            assertFalse("Loading state should be false after error", currentState.isLoading)
            assertEquals("There should be 1 error news item", 1, news.size)
            assertTrue("News should contain error message", news[0] is WalletScreenNews.ShowErrorMessage)
        }

    @Test
    fun `deleteWallet should delete wallet and reload list when other wallets remain`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            val addressToDelete = testWallet2.address
            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(WalletsListEvent.DeleteWallet(addressToDelete))
            advanceUntilIdle()

            coVerify(exactly = 1) { walletRepository.deleteWallet(addressToDelete) }

            assertTrue("Navigation state should remain null when wallets remain", news.isEmpty())
        }

    @Test
    fun `deleteWallet should navigate to onboarding when no wallets remain`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(WalletsListEvent.DeleteWallet(testWallet1.address))
            viewModel.onEvent(WalletsListEvent.DeleteWallet(testWallet2.address))
            advanceUntilIdle()

            assertEquals(
                "Should navigate to onboarding when no wallets remain",
                NavigateToOnBoarding,
                news.single(),
            )
        }

    @Test
    fun `deleteWallet should handle repository exceptions`() =
        runTest {
            val addressToDelete = testWallet1.address
            coEvery { walletRepository.deleteWallet(addressToDelete) } throws RuntimeException("Delete wallet failed")

            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(WalletsListEvent.DeleteWallet(addressToDelete))
            advanceUntilIdle()

            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            coVerify(exactly = 1) { walletRepository.deleteWallet(addressToDelete) }

            val currentState = viewModel.uiState.first()
            assertEquals("Wallet list should not change after error", testWallets, currentState.otherWallets)
            assertFalse("Loading state should be false after error", currentState.isLoading)
            assertTrue("Navigation state should remain null", news.isEmpty())
        }

    @Test
    fun `navigateToWalletDetails should update navigation state to WalletScreen`() =
        runTest {
            val news = mutableListOf<WalletScreenNews>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.news.collect {
                    news.add(it)
                }
            }

            viewModel.onEvent(WalletsListEvent.Start)
            viewModel.onEvent(OnBackPress)
            advanceUntilIdle()

            assertEquals(
                "Navigation state should be set to WalletScreen",
                NavigateToWalletDetails,
                news.single(),
            )
        }
}
