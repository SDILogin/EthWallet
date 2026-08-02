package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: WalletsListViewModel

    private val firstWallet = EthWallet(
        address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
        label = "My primary wallet",
    )
    private val secondWallet = EthWallet(
        address = EthAddress("0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF"),
        label = "My secondary wallet",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao().also {
            it.wallets = listOf(
                WalletEntity(firstWallet.address.toString(), "encrypted-key-1", firstWallet.label),
                WalletEntity(secondWallet.address.toString(), "encrypted-key-2", secondWallet.label),
            )
            it.currentWalletAddress = firstWallet.address.toString()
        }
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = FakeEthManager(),
            privateKeyEncryptor = FakePrivateKeyEncryptor(),
        )
        viewModel = WalletsListViewModel(walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationDisplaysAllWalletsAndTheCurrentSelection() = runTest(testDispatcher) {
        val news = collectNews()

        advanceUntilIdle()

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets)
        assertEquals(firstWallet, viewModel.uiState.value.selected)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf<WalletScreenNews>(WalletScreenNews.ShowWalletDetails(firstWallet.address)), news)
    }

    @Test
    fun initializationWithNoWalletsShowsEmptyUnselectedState() = runTest(testDispatcher) {
        walletDao.wallets = emptyList()
        viewModel = WalletsListViewModel(walletRepository)
        val news = collectNews()

        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.wallets)
        assertNull(viewModel.uiState.value.selected)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(news.isEmpty())
    }

    @Test
    fun selectingWalletMakesItCurrentAndShowsItsDetails() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        news.clear()

        viewModel.onEvent(WalletsListEvent.OnWalletSelected(secondWallet.address))
        advanceUntilIdle()

        assertEquals(secondWallet.address.toString(), walletDao.currentWalletAddress)
        assertEquals(secondWallet, viewModel.uiState.value.selected)
        assertEquals(listOf<WalletScreenNews>(WalletScreenNews.ShowWalletDetails(secondWallet.address)), news)
    }

    @Test
    fun deletingInactiveWalletKeepsCurrentSelection() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        news.clear()

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(secondWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(firstWallet), viewModel.uiState.value.wallets)
        assertEquals(firstWallet, viewModel.uiState.value.selected)
        assertEquals(firstWallet.address.toString(), walletDao.currentWalletAddress)
        assertTrue(news.isEmpty())
    }

    @Test
    fun deletingCurrentWalletClearsSelectionWithoutActivatingAnotherWallet() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        news.clear()

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(firstWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(secondWallet), viewModel.uiState.value.wallets)
        assertNull(viewModel.uiState.value.selected)
        assertNull(walletDao.currentWalletAddress)
        assertEquals(listOf<WalletScreenNews>(WalletScreenNews.SelectedWalletDeleted), news)
    }

    @Test
    fun failedWalletSelectionShowsErrorAndPreservesSelection() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        news.clear()
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.OnWalletSelected(secondWallet.address))
        advanceUntilIdle()

        assertEquals(firstWallet, viewModel.uiState.value.selected)
        assertEquals(listOf<WalletScreenNews>(WalletScreenNews.ShowErrorMessage), news)
    }

    @Test
    fun failedWalletDeletionShowsErrorAndPreservesWallets() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        news.clear()
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(secondWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets)
        assertEquals(firstWallet, viewModel.uiState.value.selected)
        assertEquals(listOf<WalletScreenNews>(WalletScreenNews.ShowErrorMessage), news)
    }

    private fun kotlinx.coroutines.test.TestScope.collectNews(): MutableList<WalletScreenNews> {
        val news = mutableListOf<WalletScreenNews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news.add(it) }
        }
        return news
    }

    private fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
