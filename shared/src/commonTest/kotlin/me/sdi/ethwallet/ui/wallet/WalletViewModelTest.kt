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
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
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
    private var fiatRateUpdateFailure: Throwable? = null

    private val balanceProvider: BalanceProvider = object : BalanceProvider {
        override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> {
            observedAddresses += address
            return balanceFlow
        }

        override suspend fun forceUpdate(address: EthAddress) {
            forceUpdatedAddresses += address
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
        fiatRateUpdateFailure = null
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
        assertFalse(currentState.isBalanceLoading, "Should not be loading after data is available")
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
        assertTrue(viewModel.viewState.value.isBalanceLoading)
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
        assertFalse(currentState.isBalanceLoading)
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
        assertFalse(currentState.isBalanceLoading)
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
        assertFalse(viewModel.viewState.value.isBalanceLoading)
        assertNull(viewModel.viewState.value.totalBalanceUsd)
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

        assertFalse(viewModel.viewState.value.isBalanceLoading)
        assertEquals("$2,000.00", viewModel.viewState.value.totalBalanceUsd)
    }
}
