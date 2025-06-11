package me.sdi.ethwallet.ui.wallet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletRepository: WalletRepository
    private lateinit var balanceProvider: BalanceProvider
    private lateinit var fiatRateProvider: FiatRateProvider
    private lateinit var viewModel: WalletViewModel

    private val testAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val testWallet = EthWallet(label = "Test Wallet", address = testAddress)

    private val testUsdFormatter: (BigDecimal) -> String = { amount ->
        "$${amount.setScale(2, RoundingMode.HALF_UP)}"
    }

    private fun mockEmptyTokenBalances() {
        val emptyBalances = KnownTokens.entries.associate { it.ethAddress to BigInteger.ZERO }
        coEvery { balanceProvider.getErc20TokensBalance(testAddress, any()) } returns emptyBalances
    }

    @Before
    fun setUp() {
        walletRepository = mockk()
        balanceProvider = mockk()
        fiatRateProvider = mockk()
        viewModel = WalletViewModel(
            walletRepository = walletRepository,
            balanceProvider = balanceProvider,
            fiatRateProvider = fiatRateProvider,
            usdFormatter = testUsdFormatter,
        )
    }

    @Test
    fun `should calculate correct ETH balance and USD value on successful loading`() =
        runTest {
            val oneEthInWei = BigInteger("1000000000000000000")
            val ethPrice = BigDecimal("2000.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns oneEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            mockEmptyTokenBalances()

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertEquals(
                "ETH balance should be formatted correctly",
                "1 ETH",
                currentState.totalBalanceEth,
            )
            assertEquals(
                "USD value should be calculated correctly",
                "$2000.00",
                currentState.totalBalanceUsd,
            )
            assertEquals(
                "Should have favorite tokens",
                3,
                currentState.favoriteTokens.size,
            )
        }

    @Test
    fun `should handle zero balance correctly`() =
        runTest {
            val zeroWei = BigInteger.ZERO
            val ethPrice = BigDecimal("3000.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns zeroWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            mockEmptyTokenBalances()

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
    fun `should handle fractional ETH amounts correctly`() =
        runTest {
            val halfEthInWei = BigInteger("500000000000000000")
            val ethPrice = BigDecimal("2500.50")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns halfEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            mockEmptyTokenBalances()

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertEquals(
                "Fractional ETH balance should be formatted correctly",
                "0.5 ETH",
                currentState.totalBalanceEth,
            )
            assertEquals(
                "Fractional USD value should be calculated correctly",
                "$1250.25",
                currentState.totalBalanceUsd,
            )
        }

    @Test
    fun `should handle Refresh event correctly`() =
        runTest {
            val twoEthInWei = BigInteger("2000000000000000000")
            val ethPrice = BigDecimal("1500.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns twoEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            mockEmptyTokenBalances()

            viewModel.onEvent(WalletEvent.Refresh)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()
            assertEquals(
                "ETH balance should be updated on refresh",
                "2 ETH",
                currentState.totalBalanceEth,
            )
            assertEquals(
                "USD value should be updated on refresh",
                "$3000.00",
                currentState.totalBalanceUsd,
            )
        }

    @Test
    fun `should fetch and display ERC-20 token balances correctly`() =
        runTest {
            val oneEthInWei = BigInteger("1000000000000000000")
            val ethPrice = BigDecimal("2000.00")

            // 100 USDT (6 decimals), 50 USDC (6 decimals), 200 DAI (18 decimals)
            val usdtBalance = BigInteger("100000000") // 100 USDT (6 decimals)
            val usdcBalance = BigInteger("50000000") // 50 USDC (6 decimals)
            val daiBalance = BigInteger("200000000000000000000") // 200 DAI (18 decimals)

            val tokenBalances = mapOf(
                KnownTokens.USDT.ethAddress to usdtBalance,
                KnownTokens.USDC.ethAddress to usdcBalance,
                KnownTokens.DAI.ethAddress to daiBalance,
            )

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns oneEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            coEvery { balanceProvider.getErc20TokensBalance(testAddress, any()) } returns tokenBalances

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()

            // ETH: $2000, Tokens: $350 (100+50+200), Total: $2350
            assertEquals(
                "Total USD should include ETH and token values",
                "$2350.00",
                currentState.totalBalanceUsd,
            )

            assertEquals(
                "Should have 3 favorite tokens",
                3,
                currentState.favoriteTokens.size,
            )

            val usdtToken = currentState.favoriteTokens.find { it.ticker == "USDT" }
            assertEquals("100", usdtToken?.amount)
            assertEquals("$100.00", usdtToken?.usdValue)
        }

    @Test
    fun `should handle zero ERC-20 token balances correctly`() =
        runTest {
            val oneEthInWei = BigInteger("1000000000000000000")
            val ethPrice = BigDecimal("2000.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns oneEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            mockEmptyTokenBalances()

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()

            assertEquals(
                "Total USD should only include ETH value",
                "$2000.00",
                currentState.totalBalanceUsd,
            )

            currentState.favoriteTokens.forEach { token ->
                assertEquals("Token amount should be 0", "0", token.amount)
                assertEquals("Token USD value should be $0.00", "$0.00", token.usdValue)
            }
        }

    @Test
    fun `should handle mixed token balances correctly`() =
        runTest {
            val zeroEth = BigInteger.ZERO
            val ethPrice = BigDecimal("2000.00")

            // Only USDT has balance
            val tokenBalances = mapOf(
                KnownTokens.USDT.ethAddress to BigInteger("50000000"), // 50 USDT (6 decimals)
                KnownTokens.USDC.ethAddress to BigInteger.ZERO,
                KnownTokens.DAI.ethAddress to BigInteger.ZERO,
            )

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns zeroEth
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            coEvery { balanceProvider.getErc20TokensBalance(testAddress, any()) } returns tokenBalances

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()

            assertEquals(
                "Total USD should only include USDT value",
                "$50.00",
                currentState.totalBalanceUsd,
            )

            val usdtToken = currentState.favoriteTokens.find { it.ticker == "USDT" }
            assertEquals("50", usdtToken?.amount)
            assertEquals("$50.00", usdtToken?.usdValue)

            val usdcToken = currentState.favoriteTokens.find { it.ticker == "USDC" }
            assertEquals("0", usdcToken?.amount)
            assertEquals("$0.00", usdcToken?.usdValue)
        }

    @Test
    fun `should handle ERC-20 token fetch failures gracefully`() =
        runTest {
            val oneEthInWei = BigInteger("1000000000000000000")
            val ethPrice = BigDecimal("2000.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns oneEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            coEvery { balanceProvider.getErc20TokensBalance(testAddress, any()) } throws RuntimeException("Network error")

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()

            assertEquals(
                "ETH balance should still work",
                "1 ETH",
                currentState.totalBalanceEth,
            )

            assertEquals(
                "Total USD should only include ETH value",
                "$2000.00",
                currentState.totalBalanceUsd,
            )

            currentState.favoriteTokens.forEach { token ->
                assertEquals("Token amount should fallback to 0", "0", token.amount)
                assertEquals("Token USD value should fallback to $0.00", "$0.00", token.usdValue)
            }
        }

    @Test
    fun `should calculate total USD value including stablecoins correctly`() =
        runTest {
            val halfEthInWei = BigInteger("500000000000000000") // 0.5 ETH
            val ethPrice = BigDecimal("3000.00") // $3000 per ETH

            // Various token amounts
            val tokenBalances = mapOf(
                KnownTokens.USDT.ethAddress to BigInteger("100000000"), // 100 USDT (6 decimals)
                KnownTokens.USDC.ethAddress to BigInteger("25000000"), // 25 USDC (6 decimals)
                KnownTokens.DAI.ethAddress to BigInteger("75000000000000000000"), // 75 DAI (18 decimals)
            )

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns halfEthInWei
            coEvery { fiatRateProvider.getUsdRates(any()) } returns mapOf(
                "ETH" to ethPrice,
                "USDT" to BigDecimal.ONE,
                "USDC" to BigDecimal.ONE,
                "DAI" to BigDecimal.ONE,
            )
            coEvery { balanceProvider.getErc20TokensBalance(testAddress, any()) } returns tokenBalances

            viewModel.onEvent(WalletEvent.Initialize)
            advanceUntilIdle()

            val currentState = viewModel.viewState.first()

            // ETH: 0.5 * $3000 = $1500, Tokens: $100 + $25 + $75 = $200, Total: $1700
            assertEquals(
                "Total USD should be ETH + all stablecoin values",
                "$1700.00",
                currentState.totalBalanceUsd,
            )

            assertEquals(
                "ETH balance should be correct",
                "0.5 ETH",
                currentState.totalBalanceEth,
            )
        }
}
