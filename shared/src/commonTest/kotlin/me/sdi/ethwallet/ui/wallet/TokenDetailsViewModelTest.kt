package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.fakes.FakeFiatRateProvider
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdcOnMainnet
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TokenDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val walletAddress = EthAddress("0x1234567890123456789012345678901234567890")
    private val balances = MutableStateFlow<Map<EthAddress, BigInteger>>(emptyMap())
    private val provider = object : BalanceProvider {
        override fun observe(address: EthAddress): MutableStateFlow<Map<EthAddress, BigInteger>> {
            assertEquals(walletAddress, address)
            return balances
        }

        override suspend fun forceUpdate(address: EthAddress) = error("Details must only observe cached balances")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun depegUpdatesFiatValueWithoutChangingBalance() = runTest(dispatcher) {
        val fiatProvider = FakeFiatRateProvider()
        val viewModel = TokenDetailsViewModel(
            walletAddress = walletAddress,
            tokenAddress = usdcOnMainnet.ethAddress,
            balanceProvider = provider,
            chainConfig = ChainConfig("", 1L, null, ""),
            fiatRateProvider = fiatProvider,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        balances.value = mapOf(usdcOnMainnet.ethAddress to BigInteger("100123456"))
        runCurrent()
        assertEquals("100.123456", viewModel.viewState.value.balance)
        assertNull(viewModel.viewState.value.fiatValue)

        fiatProvider.rates.value = mapOf(usdcOnMainnet.ethAddress to BigDecimal("0.87"))
        runCurrent()
        assertEquals("100.123456", viewModel.viewState.value.balance)
        assertEquals("$87.11", viewModel.viewState.value.fiatValue)

        fiatProvider.rates.value = mapOf(usdcOnMainnet.ethAddress to BigDecimal("1.02"))
        runCurrent()
        assertEquals("100.123456", viewModel.viewState.value.balance)
        assertEquals("$102.13", viewModel.viewState.value.fiatValue)

        balances.value = emptyMap()
        runCurrent()
        assertNull(viewModel.viewState.value.balance)
        assertNull(viewModel.viewState.value.fiatValue)
    }

    @Test
    fun selectedTokenBalanceUpdatesWithoutChangingMetadata() = runTest(dispatcher) {
        val viewModel = TokenDetailsViewModel(
            walletAddress = walletAddress,
            tokenAddress = usdcOnMainnet.ethAddress,
            balanceProvider = provider,
            fiatRateProvider = FakeFiatRateProvider(),
            chainConfig = ChainConfig("", 1L, null, ""),
        )
        val initial = viewModel.viewState.value
        assertEquals("USD Coin", initial.name)
        assertEquals("USDC", initial.symbol)
        assertEquals(6, initial.decimals)
        assertEquals(usdcOnMainnet.ethAddress.toString(), initial.address)
        assertNull(initial.balance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.viewState.collect {} }
        runCurrent()

        balances.value = mapOf(
            usdcOnMainnet.ethAddress to BigInteger("500123456"),
            daiOnMainnet.ethAddress to BigInteger.TEN,
        )
        runCurrent()
        assertEquals(initial.copy(balance = "500.123456"), viewModel.viewState.value)

        balances.value = mapOf(usdcOnMainnet.ethAddress to BigInteger.ZERO)
        runCurrent()
        assertEquals(initial.copy(balance = "0"), viewModel.viewState.value)

        balances.value = mapOf(daiOnMainnet.ethAddress to BigInteger.TEN)
        runCurrent()
        assertEquals(initial, viewModel.viewState.value)
    }
}
