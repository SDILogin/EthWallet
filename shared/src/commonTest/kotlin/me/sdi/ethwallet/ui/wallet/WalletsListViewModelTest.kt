package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdcOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigDecimal
import java.math.BigInteger
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
    private lateinit var viewModel: WalletsListViewModel
    private val balances = mutableMapOf<EthAddress, MutableStateFlow<Map<EthAddress, BigInteger>>>()
    private val fiatRates = MutableStateFlow<Map<EthAddress, BigDecimal>>(emptyMap())
    private val forceUpdatedAddresses = mutableListOf<EthAddress>()
    private val balanceRefreshFailures = mutableSetOf<EthAddress>()
    private var fiatRateUpdates = 0

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
        balances.clear()
        balances[firstWallet.address] = MutableStateFlow(emptyMap())
        balances[secondWallet.address] = MutableStateFlow(emptyMap())
        fiatRates.value = emptyMap()
        forceUpdatedAddresses.clear()
        balanceRefreshFailures.clear()
        fiatRateUpdates = 0
        viewModel = WalletsListViewModel(
            walletRepository = WalletRepository(walletDao, FakeEthManager(), FakePrivateKeyEncryptor()),
            balanceProvider = object : BalanceProvider {
                override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> = balances.getValue(address)

                override suspend fun forceUpdate(address: EthAddress) {
                    forceUpdatedAddresses += address
                    if (address in balanceRefreshFailures) {
                        throw IllegalStateException("Balance unavailable")
                    }
                }
            },
            fiatRateProvider = object : FiatRateProvider {
                override fun observe(): Flow<Map<EthAddress, BigDecimal>> = fiatRates

                override suspend fun forceUpdate() {
                    fiatRateUpdates++
                }
            },
            chainConfig = ChainConfig(
                rpcUrl = "https://example.com",
                chainId = 1L,
                explorerBaseUrl = "https://example.com",
                transactionHistoryApiBaseUrl = "https://history.example.test",
            ),
            backgroundDispatcher = testDispatcher,
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

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets.map { it.wallet })
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(emptyList(), news)
    }

    @Test
    fun deletingWalletUpdatesListAndEmitsDeletedAddress() = runTest(testDispatcher) {
        val news = collectNews()
        advanceUntilIdle()

        viewModel.onEvent(WalletsListEvent.OnDeleteWalletClick(secondWallet.address))
        advanceUntilIdle()

        assertEquals(listOf(firstWallet), viewModel.uiState.value.wallets.map { it.wallet })
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

        assertEquals(listOf(firstWallet, secondWallet), viewModel.uiState.value.wallets.map { it.wallet })
        assertEquals(listOf<WalletsListNews>(WalletsListNews.ShowErrorMessage), news)
    }

    @Test
    fun renamingWalletNormalizesTheLabelAndUpdatesTheList() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(WalletsListEvent.OnRenameWalletClick(firstWallet))
        assertEquals(firstWallet.label, viewModel.uiState.value.walletLabelEditor?.draftLabel)

        viewModel.onEvent(WalletsListEvent.OnWalletLabelChanged("  Daily   spending  "))
        viewModel.onEvent(WalletsListEvent.OnWalletLabelSaveClick)
        advanceUntilIdle()

        assertEquals("Daily spending", walletDao.wallets.first().label)
        assertEquals("Daily spending", viewModel.uiState.value.wallets.first().wallet.label)
        assertEquals(null, viewModel.uiState.value.walletLabelEditor)
    }

    @Test
    fun renamingWalletRejectsInvalidOrUnchangedLabelsAndCanBeCancelled() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(WalletsListEvent.OnRenameWalletClick(firstWallet))
        assertFalse(viewModel.uiState.value.walletLabelEditor?.isSaveEnabled == true)

        viewModel.onEvent(WalletsListEvent.OnWalletLabelChanged("   "))
        assertFalse(viewModel.uiState.value.walletLabelEditor?.isSaveEnabled == true)

        viewModel.onEvent(WalletsListEvent.OnWalletLabelEditorDismiss)
        assertEquals(null, viewModel.uiState.value.walletLabelEditor)
        assertEquals(firstWallet.label, walletDao.wallets.first().label)
    }

    @Test
    fun failedWalletRenameKeepsTheEditorOpen() = runTest(testDispatcher) {
        advanceUntilIdle()
        walletDao.shouldThrowException = true

        viewModel.onEvent(WalletsListEvent.OnRenameWalletClick(firstWallet))
        viewModel.onEvent(WalletsListEvent.OnWalletLabelChanged("Renamed wallet"))
        viewModel.onEvent(WalletsListEvent.OnWalletLabelSaveClick)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.walletLabelEditor?.hasSaveError == true)
        assertFalse(viewModel.uiState.value.walletLabelEditor?.isSaving == true)
        assertEquals(firstWallet.label, walletDao.wallets.first().label)
    }

    @Test
    fun walletCardsDisplayTheirOwnTotalsAndRefreshEveryWallet() = runTest(testDispatcher) {
        balances.getValue(firstWallet.address).value = supportedBalances(ethWei = BigInteger("1000000000000000000"))
        balances.getValue(secondWallet.address).value = supportedBalances(ethWei = BigInteger("500000000000000000"))
        fiatRates.value = supportedFiatRates(ethRate = BigDecimal("2000"))

        advanceUntilIdle()

        assertEquals(
            listOf("$2,000.00", "$1,000.00"),
            viewModel.uiState.value.wallets.map { it.totalBalanceUsd },
        )
        assertEquals(setOf(firstWallet.address, secondWallet.address), forceUpdatedAddresses.toSet())
        assertEquals(1, fiatRateUpdates)
    }

    @Test
    fun walletCardKeepsUsdLoadingWhenFiatRatesAreUnavailable() = runTest(testDispatcher) {
        balances.getValue(firstWallet.address).value = supportedBalances(ethWei = BigInteger("1000000000000000000"))

        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.wallets.first().totalBalanceUsd)
    }

    @Test
    fun newlyImportedWalletIsObservedAndRefreshed() = runTest(testDispatcher) {
        advanceUntilIdle()
        val thirdWallet = EthWallet(
            address = EthAddress("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"),
            label = "My imported wallet",
        )
        balances[thirdWallet.address] = MutableStateFlow(emptyMap())
        walletDao.wallets += WalletEntity(thirdWallet.address.toString(), "encrypted-key-3", thirdWallet.label)

        advanceUntilIdle()

        assertTrue(forceUpdatedAddresses.contains(thirdWallet.address))
        assertEquals(thirdWallet, viewModel.uiState.value.wallets.last().wallet)
    }

    @Test
    fun failedWalletRefreshDoesNotCancelOtherWalletRefreshes() = runTest(testDispatcher) {
        val news = collectNews()
        balanceRefreshFailures += firstWallet.address

        advanceUntilIdle()

        assertEquals(setOf(firstWallet.address, secondWallet.address), forceUpdatedAddresses.toSet())
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

    private fun supportedBalances(ethWei: BigInteger): Map<EthAddress, BigInteger> = mapOf(
        EthContractAddress to ethWei,
        usdtOnMainnet.ethAddress to BigInteger.ZERO,
        usdcOnMainnet.ethAddress to BigInteger.ZERO,
        daiOnMainnet.ethAddress to BigInteger.ZERO,
    )

    private fun supportedFiatRates(ethRate: BigDecimal): Map<EthAddress, BigDecimal> = mapOf(
        EthContractAddress to ethRate,
        usdtOnMainnet.ethAddress to BigDecimal.ONE,
        usdcOnMainnet.ethAddress to BigDecimal.ONE,
        daiOnMainnet.ethAddress to BigDecimal.ONE,
    )
}
