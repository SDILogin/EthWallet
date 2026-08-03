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

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
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
        }
        viewModel = WalletsListViewModel(
            WalletRepository(walletDao, FakeEthManager(), FakePrivateKeyEncryptor()),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationDisplaysAllWalletsWithoutSelectingOne() = runTest(testDispatcher) {
        val news = collectNews()

        advanceUntilIdle()

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(emptyList(), news)
    }

    @Test
    fun deletingWalletUpdatesListAndEmitsDeletedAddress() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(secondWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(firstWallet), viewModel.uiState.value.wallets)
        assertEquals(
            listOf<WalletsListNews>(WalletsListNews.WalletDeleted(secondWallet.address)),
            news,
        )
    }

    @Test
    fun failedWalletDeletionShowsErrorAndPreservesWallets() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(secondWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets)
        assertEquals(listOf<WalletsListNews>(WalletsListNews.ShowErrorMessage), news)
    }

    private fun kotlinx.coroutines.test.TestScope.collectNews(): MutableList<WalletsListNews> {
        val news = mutableListOf<WalletsListNews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news.add(it) }
        }
        return news
    }

    private fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
