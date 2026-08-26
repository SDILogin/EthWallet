package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: WalletViewModel
    private lateinit var walletDao: FakeWalletDao
    private lateinit var walletRepository: WalletRepository

    private val testAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val chainConfig = ChainConfig(
        rpcUrl = "https://eth.llamarpc.com",
        chainId = 1L,
        explorerBaseUrl = "https://etherscan.io",
    )

    // Flow state holders for mocking providers
    private val balanceFlow = MutableStateFlow<Map<EthAddress, BigInteger>>(emptyMap())
    private val fiatRateFlow = MutableStateFlow<Map<EthAddress, BigDecimal>>(emptyMap())
    private val observedAddresses = mutableListOf<EthAddress>()
    private val forceUpdatedAddresses = mutableListOf<EthAddress>()
    private var balanceUpdateFailure: Throwable? = null
    private var fiatRateUpdateFailure: Throwable? = null

    private val balanceProvider: BalanceProvider = object : BalanceProvider {
        override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> {
            observedAddresses += address
            return balanceFlow
        }

        override suspend fun forceUpdate(address: EthAddress) {
            forceUpdatedAddresses += address
            balanceUpdateFailure?.let { throw it }
        }
    }
    private val fiatRateProvider: FiatRateProvider = object : FiatRateProvider {
        override fun observe(): Flow<Map<EthAddress, BigDecimal>> = fiatRateFlow

        override suspend fun forceUpdate() {
            fiatRateUpdateFailure?.let { throw it }
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        balanceFlow.value = emptyMap()
        fiatRateFlow.value = emptyMap()
        observedAddresses.clear()
        forceUpdatedAddresses.clear()
        balanceUpdateFailure = null
        fiatRateUpdateFailure = null
        walletDao = FakeWalletDao()
        walletRepository = WalletRepository(walletDao, FakeEthManager(), FakePrivateKeyEncryptor())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = WalletViewModel(
            walletAddress = testAddress,
            balanceProvider = balanceProvider,
            fiatRateProvider = fiatRateProvider,
            chainConfig = chainConfig,
            walletRepository = walletRepository,
        )
    }

    private fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun should_observe_balance_and_fiat_rate_flows_and_update_state_correctly() = runTest(testDispatcher) {
        val oneEthInWei = BigInteger("1000000000000000000")
        val ethPrice = BigDecimal("2000.00")

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to oneEthInWei,
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertEquals(WalletBalanceLoadState.Content, currentState.balanceLoadState)
        assertEquals(1L, currentState.chainId, "Chain ID should be exposed in state")
        assertEquals(
            "1 ETH",
            currentState.totalBalanceEth,
            "ETH balance should be formatted correctly",
        )
        assertEquals(
            "$2,000.00",
            currentState.totalBalanceUsd,
            "USD value should be calculated correctly",
        )
        assertEquals(
            3,
            currentState.favoriteTokens.size,
            "Should have favorite tokens",
        )
    }

    @Test
    fun balanceOperationsUseConstructorAddress() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(testAddress), observedAddresses)
        assertTrue(forceUpdatedAddresses.isNotEmpty())
        assertTrue(forceUpdatedAddresses.all { it == testAddress })
        assertEquals(WalletBalanceLoadState.Content, viewModel.viewState.value.balanceLoadState)
    }

    @Test
    fun walletNameIsObservedFromTheWalletRepository() = runTest(testDispatcher) {
        walletDao.wallets = listOf(
            WalletEntity(
                address = testAddress.toString(),
                encryptedPrivateKeyBase64 = "encrypted-key",
                label = "Daily spending",
            ),
        )

        createViewModel()
        advanceUntilIdle()

        assertEquals("Daily spending", viewModel.viewState.value.walletName)
    }

    @Test
    fun should_handle_zero_balance_correctly() = runTest(testDispatcher) {
        val zeroWei = BigInteger.ZERO
        val ethPrice = BigDecimal("3000.00")

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to zeroWei,
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertEquals(WalletBalanceLoadState.Content, currentState.balanceLoadState)
        assertEquals(
            "0 ETH",
            currentState.totalBalanceEth,
            "Zero ETH balance should be formatted correctly",
        )
        assertEquals(
            "$0.00",
            currentState.totalBalanceUsd,
            "Zero USD value should be calculated correctly",
        )
    }

    @Test
    fun should_handle_ERC20_token_balances_correctly() = runTest(testDispatcher) {
        val oneEthInWei = BigInteger("1000000000000000000")
        val ethPrice = BigDecimal("2000.00")

        // 100 USDT (6 decimals), 50 USDC (6 decimals), 200 DAI (18 decimals)
        val usdtBalance = BigInteger("100000000") // 100 USDT (6 decimals)
        val usdcBalance = BigInteger("50000000") // 50 USDC (6 decimals)
        val daiBalance = BigInteger("200000000000000000000") // 200 DAI (18 decimals)

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to oneEthInWei,
            usdtOnMainnet.ethAddress to usdtBalance,
            usdcOnMainnet.ethAddress to usdcBalance,
            daiOnMainnet.ethAddress to daiBalance,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()

        // ETH: $2000, Tokens: $350 (100+50+200), Total: $2350
        assertEquals(
            "$2,350.00",
            currentState.totalBalanceUsd,
            "Total USD should include ETH and token values",
        )

        assertEquals(
            3,
            currentState.favoriteTokens.size,
            "Should have 3 favorite tokens",
        )

        val usdtToken = currentState.favoriteTokens.find { it.ticker == "USDT" }
        assertEquals("100", usdtToken?.amount)
        assertEquals("$100.00", usdtToken?.usdValueFormatted)
    }

    @Test
    fun should_expose_token_balances_when_fiat_rates_are_unavailable() = runTest(testDispatcher) {
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger("100000000"),
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )

        createViewModel()
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertEquals(WalletBalanceLoadState.Content, currentState.balanceLoadState)
        assertNull(currentState.totalBalanceUsd)
        assertEquals("1 ETH", currentState.totalBalanceEth)
        assertEquals("100", currentState.favoriteTokens.first { it.ticker == "USDT" }.amount)
    }

    @Test
    fun fiat_refresh_failure_does_not_prevent_balance_loading() = runTest(testDispatcher) {
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )
        fiatRateUpdateFailure = IllegalStateException("Fiat unavailable")

        createViewModel()
        advanceUntilIdle()

        assertTrue(forceUpdatedAddresses.contains(testAddress))
        assertEquals(WalletBalanceLoadState.Content, viewModel.viewState.value.balanceLoadState)
        assertNull(viewModel.viewState.value.totalBalanceUsd)
        assertEquals(WalletFiatLoadState.Unavailable, viewModel.viewState.value.fiatLoadState)
    }

    @Test
    fun fiat_refresh_failure_keeps_a_cached_usd_rate() = runTest(testDispatcher) {
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )
        fiatRateFlow.value = mapOf(
            EthContractAddress to BigDecimal("2000"),
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )
        fiatRateUpdateFailure = IllegalStateException("Fiat unavailable")

        createViewModel()
        advanceUntilIdle()

        assertEquals(WalletBalanceLoadState.Content, viewModel.viewState.value.balanceLoadState)
        assertEquals("$2,000.00", viewModel.viewState.value.totalBalanceUsd)
        assertEquals(WalletFiatLoadState.Available, viewModel.viewState.value.fiatLoadState)
    }

    @Test
    fun initial_balance_failure_reaches_error_state() = runTest(testDispatcher) {
        balanceUpdateFailure = IllegalStateException("RPC unavailable")

        createViewModel()
        advanceUntilIdle()

        assertEquals(WalletBalanceLoadState.Error, viewModel.viewState.value.balanceLoadState)
        assertFalse(viewModel.viewState.value.showP2rIndicator)
    }

    @Test
    fun retry_recovers_from_initial_balance_failure() = runTest(testDispatcher) {
        balanceUpdateFailure = IllegalStateException("RPC unavailable")
        createViewModel()
        advanceUntilIdle()

        balanceUpdateFailure = null
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )
        viewModel.onEvent(WalletEvent.Retry)
        advanceUntilIdle()

        assertEquals(WalletBalanceLoadState.Content, viewModel.viewState.value.balanceLoadState)
        assertEquals("1 ETH", viewModel.viewState.value.totalBalanceEth)
    }

    @Test
    fun balance_refresh_failure_preserves_cached_content_and_emits_one_news_event() = runTest(testDispatcher) {
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )
        fiatRateFlow.value = mapOf(
            EthContractAddress to BigDecimal("2000"),
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )
        createViewModel()
        advanceUntilIdle()
        balanceUpdateFailure = IllegalStateException("RPC unavailable")

        viewModel.onEvent(WalletEvent.Refresh)
        advanceUntilIdle()

        assertEquals(WalletBalanceLoadState.Content, viewModel.viewState.value.balanceLoadState)
        assertEquals("1 ETH", viewModel.viewState.value.totalBalanceEth)
        assertFalse(viewModel.viewState.value.showP2rIndicator)
        assertEquals(WalletNews.ShowErrorMessage, viewModel.news.first())
    }

    @Test
    fun both_refresh_failures_emit_only_one_news_event() = runTest(testDispatcher) {
        balanceFlow.value = mapOf(
            EthContractAddress to BigInteger("1000000000000000000"),
            usdtOnMainnet.ethAddress to BigInteger.ZERO,
            usdcOnMainnet.ethAddress to BigInteger.ZERO,
            daiOnMainnet.ethAddress to BigInteger.ZERO,
        )
        fiatRateFlow.value = mapOf(
            EthContractAddress to BigDecimal("2000"),
            usdtOnMainnet.ethAddress to BigDecimal.ONE,
            usdcOnMainnet.ethAddress to BigDecimal.ONE,
            daiOnMainnet.ethAddress to BigDecimal.ONE,
        )
        createViewModel()
        advanceUntilIdle()
        balanceUpdateFailure = IllegalStateException("RPC unavailable")
        fiatRateUpdateFailure = IllegalStateException("Rates unavailable")

        viewModel.onEvent(WalletEvent.Refresh)
        advanceUntilIdle()

        assertEquals(WalletNews.ShowErrorMessage, viewModel.news.first())
        assertNull(withTimeoutOrNull(1) { viewModel.news.first() })
    }
}
