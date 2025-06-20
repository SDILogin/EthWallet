package me.sdi.ethwallet.ui.receive

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveQrViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: ReceiveQrViewModel

    private val testAddress = EthAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")
    private val testWallet = EthWallet(label = "Test Wallet", address = testAddress)

    @Before
    fun setUp() {
        walletRepository = mockk()
        coEvery { walletRepository.getActiveWallet() } returns testWallet
        viewModel = ReceiveQrViewModel(walletRepository)
    }

    @Test
    fun `initial state should have correct default values`() = runTest {
        val state = viewModel.viewState.first()

        assertNull("Initial token should be null", state.selectedTokenContract)
        assertEquals("Initial amount should be empty", "", state.amount)
        assertNull("Initial amount error should be null", state.amountError)
        assertNull("Initial QR code URI should be null", state.qrPayload)
    }

    @Test
    fun `should handle no active wallet gracefully`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns null
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("Initial token should be null", finalState.selectedTokenContract)
        assertEquals("Initial amount should be empty", "", finalState.amount)
        assertNull("QR payload should be null without wallet", finalState.qrPayload)
    }

    @Test
    fun `should handle wallet repository exception during initialization`() = runTest {
        coEvery { walletRepository.getActiveWallet() } throws RuntimeException("Database error")
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("Token should be null when exception occurs", finalState.selectedTokenContract)
        assertEquals("Amount should be empty", "", finalState.amount)
        assertNull("QR payload should be null on error", finalState.qrPayload)
    }

    @Test
    fun `onTokenSelected should update selected token`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.USDC.ethAddress))

        val finalState = collector.last()
        assertEquals("Selected token should be USDC", KnownTokens.USDC.ethAddress, finalState.selectedTokenContract)
        assertNull("Amount error should remain null", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should update amount`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "1.5", finalState.amount)
        assertNull("Amount error should be null for valid amount", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should validate negative amounts`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("-1.5"))

        val finalState = collector.last()
        assertEquals("Amount should be updated even if invalid", "-1.5", finalState.amount)
        assertEquals("Should show error for negative amount", "Amount must be greater than zero", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should validate zero amounts`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("0"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "0", finalState.amount)
        assertEquals("Should show error for zero amount", "Amount must be greater than zero", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should validate excessive decimal places for USDC`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.USDC.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.1234567"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "1.1234567", finalState.amount)
        assertEquals("Should show decimal places error for USDC", "Too many decimal places for selected token", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should accept valid decimal places for USDC`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.USDC.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.123456"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Amount should be updated", "1.123456", finalState.amount)
        assertNull("Amount error should be null for valid USDC amount", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should handle invalid number format`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("abc"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "abc", finalState.amount)
        assertNull("Amount error should be null for non-numeric string", finalState.amountError)
    }

    @Test
    fun `should generate ETH QR code automatically when token selected`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(EthContractAddress))

        // Wait for async operations to complete
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertTrue("QR code URI should be generated", finalState.qrPayload?.isNotEmpty() == true)
        assertTrue("QR code should contain ETH address", finalState.qrPayload?.contains(testAddress.toString()) == true)
        assertTrue("QR code should be ETH format", finalState.qrPayload?.startsWith("ethereum:") == true)
        assertFalse("QR code should not contain amount parameter", finalState.qrPayload?.contains("value=") == true)
    }

    @Test
    fun `should generate QR code when token and amount are selected`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        // Select token first
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(EthContractAddress))
        testScheduler.advanceUntilIdle()

        // Then set amount
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))
        testScheduler.advanceUntilIdle()

        job.cancel()

        val finalState = collector.last()
        assertEquals("Token should be selected", EthContractAddress, finalState.selectedTokenContract)
        assertEquals("Amount should be set", "1.5", finalState.amount)
        assertNull("Amount should be valid", finalState.amountError)
        // QR code should be generated - exact content may vary based on implementation
        assertTrue(
            "QR code should be generated when token and amount are valid",
            finalState.qrPayload?.isNotEmpty() == true,
        )
    }

    @Test
    fun `should generate USDC QR code automatically`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.USDC.ethAddress))
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("100.5"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Token should be USDC", KnownTokens.USDC.ethAddress, finalState.selectedTokenContract)
        assertEquals("Amount should be set", "100.5", finalState.amount)
        assertNull("Amount should be valid for USDC", finalState.amountError)
        assertTrue("QR code should be generated", finalState.qrPayload?.isNotEmpty() == true)
    }

    @Test
    fun `onClearAmount should clear amount`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnClearAmount)

        val finalState = collector.last()
        assertEquals("Amount should be cleared", "", finalState.amount)
        assertNull("Amount error should be cleared", finalState.amountError)
    }

    @Test
    fun `onCopyAddress should emit copy news with wallet address`() = runTest {
        val newsCollector = mutableListOf<ReceiveQrNews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { newsCollector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnCopyAddress)
        testScheduler.advanceUntilIdle()

        assertTrue("Should emit copy to clipboard news", newsCollector.any { it is ReceiveQrNews.CopyToClipboard })

        val copyNews = newsCollector.find { it is ReceiveQrNews.CopyToClipboard } as ReceiveQrNews.CopyToClipboard
        assertEquals("Should copy wallet address", testAddress.toString(), copyNews.text)
    }

    @Test
    fun `onCopyAddress should handle missing wallet address`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns null
        val viewModel = ReceiveQrViewModel(walletRepository)

        val newsCollector = mutableListOf<ReceiveQrNews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { newsCollector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnCopyAddress)
        testScheduler.advanceUntilIdle()

        assertTrue("Should emit error news", newsCollector.any { it is ReceiveQrNews.ShowToast })
        val toastNews = newsCollector.find { it is ReceiveQrNews.ShowToast } as ReceiveQrNews.ShowToast
        assertEquals("Should show address not available message", "Address not available", toastNews.message)
    }

    @Test
    fun `should update QR code when token changes`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(EthContractAddress))
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.USDC.ethAddress))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Selected token should be updated", KnownTokens.USDC.ethAddress, finalState.selectedTokenContract)
        assertTrue(
            "QR code should be updated for new token",
            finalState.qrPayload?.contains(KnownTokens.USDC.ethAddress.toString()) == true,
        )
    }

    @Test
    fun `should update amount correctly`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("2.5"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Amount should be updated", "2.5", finalState.amount)
        assertNull("Amount error should be null for valid amount", finalState.amountError)
    }

    @Test
    fun `should handle different token decimal precision correctly`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(KnownTokens.DAI.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1." + "0".repeat(18)))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("Should accept 18 decimal places for DAI", finalState.amountError)
    }
}
