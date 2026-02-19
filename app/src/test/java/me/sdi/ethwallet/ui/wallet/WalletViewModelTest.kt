package me.sdi.ethwallet.ui.wallet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: WalletViewModel

    private val testAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val testWallet = EthWallet(label = "Test Wallet", address = testAddress)
    private val chainConfig = ChainConfig(
        rpcUrl = "https://eth.llamarpc.com",
        chainId = 1L,
        explorerBaseUrl = "https://etherscan.io",
    )

    // Flow state holders for mocking providers
    private val balanceFlow = MutableStateFlow<Map<EthAddress, BigInteger>>(emptyMap())
    private val fiatRateFlow = MutableStateFlow<Map<EthAddress, BigDecimal>>(emptyMap())

    private val balanceProvider: BalanceProvider = object : BalanceProvider {
        override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> = balanceFlow

        override suspend fun forceUpdate(wallet: EthWallet) = Unit
    }
    private val fiatRateProvider: FiatRateProvider = object : FiatRateProvider {
        override fun observe(): Flow<Map<EthAddress, BigDecimal>> = fiatRateFlow

        override suspend fun forceUpdate() = Unit
    }

    @Before
    fun setUp() {
        walletRepository = mockk()
        coEvery { walletRepository.getActiveWallet() } returns testWallet
    }

    private fun createViewModel() {
        viewModel = WalletViewModel(
            walletRepository = walletRepository,
            balanceProvider = balanceProvider,
            fiatRateProvider = fiatRateProvider,
            chainConfig = chainConfig,
        )
    }

    private fun advanceUntilIdle() {
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `should observe balance and fiat rate flows and update state correctly`() = runTest {
        val oneEthInWei = BigInteger("1000000000000000000")
        val ethPrice = BigDecimal("2000.00")

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to oneEthInWei,
            KnownTokens.USDT.ethAddress to BigInteger.ZERO,
            KnownTokens.USDC.ethAddress to BigInteger.ZERO,
            KnownTokens.DAI.ethAddress to BigInteger.ZERO,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            KnownTokens.USDT.ethAddress to BigDecimal.ONE,
            KnownTokens.USDC.ethAddress to BigDecimal.ONE,
            KnownTokens.DAI.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        viewModel.onEvent(WalletEvent.Initialize)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertFalse("Should not be loading after data is available", currentState.isLoading)
        assertEquals("Chain ID should be exposed in state", 1L, currentState.chainId)
        assertEquals(
            "ETH balance should be formatted correctly",
            "1 ETH",
            currentState.totalBalanceEth,
        )
        assertEquals(
            "USD value should be calculated correctly",
            "$2,000.00",
            currentState.totalBalanceUsd,
        )
        assertEquals(
            "Should have favorite tokens",
            3,
            currentState.favoriteTokens.size,
        )
    }

    @Test
    fun `should handle zero balance correctly`() = runTest {
        val zeroWei = BigInteger.ZERO
        val ethPrice = BigDecimal("3000.00")

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to zeroWei,
            KnownTokens.USDT.ethAddress to BigInteger.ZERO,
            KnownTokens.USDC.ethAddress to BigInteger.ZERO,
            KnownTokens.DAI.ethAddress to BigInteger.ZERO,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            KnownTokens.USDT.ethAddress to BigDecimal.ONE,
            KnownTokens.USDC.ethAddress to BigDecimal.ONE,
            KnownTokens.DAI.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        viewModel.onEvent(WalletEvent.Initialize)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()
        assertEquals(
            "Zero ETH balance should be formatted correctly",
            "0 ETH",
            currentState.totalBalanceEth,
        )
        assertEquals(
            "Zero USD value should be calculated correctly",
            "$0.00",
            currentState.totalBalanceUsd,
        )
    }

    @Test
    fun `should handle ERC-20 token balances correctly`() = runTest {
        val oneEthInWei = BigInteger("1000000000000000000")
        val ethPrice = BigDecimal("2000.00")

        // 100 USDT (6 decimals), 50 USDC (6 decimals), 200 DAI (18 decimals)
        val usdtBalance = BigInteger("100000000") // 100 USDT (6 decimals)
        val usdcBalance = BigInteger("50000000") // 50 USDC (6 decimals)
        val daiBalance = BigInteger("200000000000000000000") // 200 DAI (18 decimals)

        // Set up flow values BEFORE creating the ViewModel
        balanceFlow.value = mapOf(
            EthContractAddress to oneEthInWei,
            KnownTokens.USDT.ethAddress to usdtBalance,
            KnownTokens.USDC.ethAddress to usdcBalance,
            KnownTokens.DAI.ethAddress to daiBalance,
        )

        fiatRateFlow.value = mapOf(
            EthContractAddress to ethPrice,
            KnownTokens.USDT.ethAddress to BigDecimal.ONE,
            KnownTokens.USDC.ethAddress to BigDecimal.ONE,
            KnownTokens.DAI.ethAddress to BigDecimal.ONE,
        )

        createViewModel()
        viewModel.onEvent(WalletEvent.Initialize)
        advanceUntilIdle()

        val currentState = viewModel.viewState.first()

        // ETH: $2000, Tokens: $350 (100+50+200), Total: $2350
        assertEquals(
            "Total USD should include ETH and token values",
            "$2,350.00",
            currentState.totalBalanceUsd,
        )

        assertEquals(
            "Should have 3 favorite tokens",
            3,
            currentState.favoriteTokens.size,
        )

        val usdtToken = currentState.favoriteTokens.find { it.ticker == "USDT" }
        assertEquals("100", usdtToken?.amount)
        assertEquals("$100.00", usdtToken?.usdValueFormatted)
    }
}
