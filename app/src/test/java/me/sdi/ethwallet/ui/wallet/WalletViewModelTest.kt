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
import me.sdi.ethwallet.core.EthPriceProvider
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
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
    private lateinit var ethPriceProvider: EthPriceProvider
    private lateinit var viewModel: WalletViewModel

    private val testAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val testWallet = EthWallet(label = "Test Wallet", address = testAddress)

    private val testUsdFormatter: (BigDecimal) -> String = { amount ->
        "$${amount.setScale(2, RoundingMode.HALF_UP)}"
    }

    @Before
    fun setUp() {
        walletRepository = mockk()
        balanceProvider = mockk()
        ethPriceProvider = mockk()
        viewModel = WalletViewModel(
            walletRepository = walletRepository,
            balanceProvider = balanceProvider,
            ethPriceProvider = ethPriceProvider,
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
            coEvery { ethPriceProvider.getPrice() } returns ethPrice

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
        }

    @Test
    fun `should handle zero balance correctly`() =
        runTest {
            val zeroWei = BigInteger.ZERO
            val ethPrice = BigDecimal("3000.00")

            coEvery { walletRepository.getActiveWallet() } returns testWallet
            coEvery { balanceProvider.getEthBalance(testAddress) } returns zeroWei
            coEvery { ethPriceProvider.getPrice() } returns ethPrice

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
            coEvery { ethPriceProvider.getPrice() } returns ethPrice

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
            coEvery { ethPriceProvider.getPrice() } returns ethPrice

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
}
