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
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthWalletCreator
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.ui.wallet.WalletScreenNews.NavigateToOnBoarding
import me.sdi.ethwallet.ui.wallet.WalletScreenNews.NavigateToWalletDetails
import me.sdi.ethwallet.ui.wallet.WalletsListEvent.OnBackPress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var walletCreator: FakeEthWalletCreator
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        walletCreator = FakeEthWalletCreator()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        // Populate database with mock wallets
        walletDao.wallets = listOf(
            WalletEntity(
                address = testWallet1.address.toString(),
                encryptedPrivateKeyBase64 = "encrypted_key_1",
                label = "My Primary Wallet",
                isActive = true,
            ),
            WalletEntity(
                address = testWallet2.address.toString(),
                encryptedPrivateKeyBase64 = "encrypted_key_2",
                label = "My Secondary Wallet",
                isActive = false,
            ),
        )

        walletRepository = WalletRepository(
            walletDao = walletDao,
            walletCreator = walletCreator,
            privateKeyEncryptor = privateKeyEncryptor,
        )

        viewModel = WalletsListViewModel(walletRepository = walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_show_loading_state() = runTest(testDispatcher) {
        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val initialState = viewModel.uiState.first()
        assertTrue(initialState.isLoading, "Initial state should have isLoading = true")
        assertEquals(emptyList<EthWallet>(), initialState.otherWallets, "Initial state should have empty wallet list")
        assertTrue(news.isEmpty(), "Initial state should have no navigation target")
        job.cancel()
    }

    @Test
    fun loadWallets_should_fetch_wallets_from_repository_and_update_state() = runTest(testDispatcher) {
        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        viewModel.onEvent(WalletsListEvent.Start)
        advanceUntilIdle()

        val currentState = viewModel.uiState.first()
        assertFalse(currentState.isLoading, "Loading state should be false after initialization")
        assertEquals(testWallet1, currentState.activeWallet, "Active wallet should be updated with test wallet 1")
        assertEquals(listOf(testWallet2), currentState.otherWallets, "Wallet list otherWallets should be updated and exclude active wallet")
        assertTrue(news.isEmpty(), "Navigation state should remain null")
        job.cancel()
    }

    @Test
    fun loadWallets_should_handle_empty_wallet_list_correctly() = runTest(testDispatcher) {
        walletDao.wallets = emptyList()

        viewModel.onEvent(WalletsListEvent.Start)
        advanceUntilIdle()

        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val currentState = viewModel.uiState.first()
        assertFalse(currentState.isLoading, "Loading state should be false after initialization")
        assertEquals(emptyList<EthWallet>(), currentState.otherWallets, "Wallet list should be empty")
        assertTrue(news.isEmpty(), "Navigation state should remain null")
        job.cancel()
    }

    @Test
    fun loadWallets_should_handle_repository_exceptions() = runTest(testDispatcher) {
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.Start)
        advanceUntilIdle()

        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val currentState = viewModel.uiState.first()
        assertFalse(currentState.isLoading, "Loading state should be false after error")
        assertEquals(emptyList<EthWallet>(), currentState.otherWallets, "Wallet list should remain empty after error")
        assertTrue(news.isEmpty(), "Navigation state should remain null")
        job.cancel()
    }

    @Test
    fun setActiveWallet_should_update_active_wallet_through_repository_and_reload_wallet_list() = runTest(testDispatcher) {
        viewModel.onEvent(WalletsListEvent.Start)
        viewModel.onEvent(WalletsListEvent.SetActiveWallet(testWallet2.address))
        advanceUntilIdle()

        // Verify it was marked active in fake database
        val updatedWallets = walletDao.getAllWallets()
        val wallet2 = updatedWallets.find { it.address == testWallet2.address.toString() }
        assertTrue(wallet2?.isActive == true, "Wallet 2 should be marked active")
    }

    @Test
    fun setActiveWallet_should_handle_repository_exceptions() = runTest(testDispatcher) {
        viewModel.onEvent(WalletsListEvent.Start)
        advanceUntilIdle()

        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val targetAddress = testWallet2.address
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.SetActiveWallet(targetAddress))
        advanceUntilIdle()

        val currentState = viewModel.uiState.first()
        assertEquals(testWallet1, currentState.activeWallet, "Active wallet should not change after error")
        assertEquals(listOf(testWallet2), currentState.otherWallets, "otherWallets should not change after error")
        assertFalse(currentState.isLoading, "Loading state should be false after error")
        assertEquals(1, news.size, "There should be 1 error news item")
        assertTrue(news[0] is WalletScreenNews.ShowErrorMessage, "News should contain error message")
        job.cancel()
    }

    @Test
    fun deleteWallet_should_delete_wallet_and_reload_list_when_other_wallets_remain() = runTest(testDispatcher) {
        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val addressToDelete = testWallet2.address
        viewModel.onEvent(WalletsListEvent.Start)
        viewModel.onEvent(WalletsListEvent.DeleteWallet(addressToDelete))
        advanceUntilIdle()

        val remainingWallets = walletDao.getAllWallets()
        assertTrue(remainingWallets.none { it.address == addressToDelete.toString() }, "Wallet should be deleted")
        assertTrue(news.isEmpty(), "Navigation state should remain null when wallets remain")
        job.cancel()
    }

    @Test
    fun deleteWallet_should_navigate_to_onboarding_when_no_wallets_remain() = runTest(testDispatcher) {
        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        viewModel.onEvent(WalletsListEvent.Start)
        viewModel.onEvent(WalletsListEvent.DeleteWallet(testWallet1.address))
        viewModel.onEvent(WalletsListEvent.DeleteWallet(testWallet2.address))
        advanceUntilIdle()

        assertEquals(
            NavigateToOnBoarding,
            news.single(),
            "Should navigate to onboarding when no wallets remain",
        )
        job.cancel()
    }

    @Test
    fun deleteWallet_should_handle_repository_exceptions() = runTest(testDispatcher) {
        viewModel.onEvent(WalletsListEvent.Start)
        advanceUntilIdle()

        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        val addressToDelete = testWallet1.address
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.DeleteWallet(addressToDelete))
        advanceUntilIdle()

        val currentState = viewModel.uiState.first()
        assertEquals(testWallet1, currentState.activeWallet, "Active wallet should not change after error")
        assertEquals(listOf(testWallet2), currentState.otherWallets, "otherWallets should not change after error")
        assertFalse(currentState.isLoading, "Loading state should be false after error")
        assertTrue(news.none { it is NavigateToOnBoarding || it is NavigateToWalletDetails }, "Navigation state should remain null")
        job.cancel()
    }

    @Test
    fun navigateToWalletDetails_should_update_navigation_state_to_WalletScreen() = runTest(testDispatcher) {
        val news = mutableListOf<WalletScreenNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect {
                news.add(it)
            }
        }

        viewModel.onEvent(WalletsListEvent.Start)
        viewModel.onEvent(OnBackPress)
        advanceUntilIdle()

        assertEquals(
            NavigateToWalletDetails,
            news.single(),
            "Navigation state should be set to WalletScreen",
        )
        job.cancel()
    }
}
