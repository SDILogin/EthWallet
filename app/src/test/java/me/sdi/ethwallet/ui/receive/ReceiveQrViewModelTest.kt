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
    fun `initial state should load wallet address successfully`() = runTest {
        val state = viewModel.viewState.first()

        assertEquals("Wallet address should be loaded", testAddress, state.receiverAddress)
        assertEquals("Initial token should be ETH", SelectedToken.ETH, state.selectedToken)
        assertEquals("Initial amount should be empty", "", state.amount)
        assertNull("Initial amount error should be null", state.amountError)
        assertNull("Initial QR code URI should be null", state.qrPayload)
        assertFalse("Should not be loading after initialization", state.isLoading)
        assertNull("Initial error should be null", state.error)
    }

    @Test
    fun `initial state should handle no active wallet gracefully`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns null
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("Receiver address should be null when no active wallet", finalState.receiverAddress)
        assertFalse("Should not be loading after error", finalState.isLoading)
        assertEquals("Should show no active wallet error", "No active wallet found", finalState.error)
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
        assertNull("Receiver address should be null when exception occurs", finalState.receiverAddress)
        assertFalse("Should not be loading after error", finalState.isLoading)
        assertEquals("Should show generic error message", "Failed to load wallet address", finalState.error)
    }

    @Test
    fun `onTokenSelected should update selected token and clear QR code`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.USDC)))

        val finalState = collector.last()
        assertEquals("Selected token should be USDC", SelectedToken.ERC20(KnownTokens.USDC), finalState.selectedToken)
        assertNull("QR code URI should be cleared when token changes", finalState.qrPayload)
        assertNull("Amount error should be cleared when token changes", finalState.amountError)
    }

    @Test
    fun `onAmountChanged should update amount and clear QR code`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "1.5", finalState.amount)
        assertNull("QR code URI should be cleared when amount changes", finalState.qrPayload)
        assertNull("Amount error should be cleared for valid amount", finalState.amountError)
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
        assertNull("QR code should not be generated for negative amount", finalState.qrPayload)
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
        assertNull("QR code should not be generated for zero amount", finalState.qrPayload)
    }

    @Test
    fun `onAmountChanged should validate excessive decimal places for USDC`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.USDC)))

        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.1234567"))

        val finalState = collector.last()
        assertEquals("Amount should be updated", "1.1234567", finalState.amount)
        assertEquals("Should show decimal places error for USDC", "Too many decimal places for selected token", finalState.amountError)
        assertNull("QR code should not be generated with excessive decimals", finalState.qrPayload)
    }

    @Test
    fun `onAmountChanged should accept valid decimal places for USDC`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.USDC)))

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
        assertNull("QR code should not be automatically generated", finalState.qrPayload)
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
        assertEquals("Should show number format error", "Invalid number format", finalState.amountError)
        assertNull("QR code should not be generated for invalid format", finalState.qrPayload)
    }

    @Test
    fun `onGenerateQrCode should generate ETH QR code without amount`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
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
    fun `onGenerateQrCode should generate ETH QR code with amount`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertTrue("QR code URI should be generated", finalState.qrPayload?.isNotEmpty() == true)
        assertTrue("QR code should contain ETH address", finalState.qrPayload?.contains(testAddress.toString()) == true)
        assertTrue("QR code should contain amount", finalState.qrPayload?.contains("value=1.5") == true)
    }

    @Test
    fun `onGenerateQrCode should generate USDC QR code with amount`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.USDC)))
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("100.5"))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertTrue("QR code URI should be generated", finalState.qrPayload?.isNotEmpty() == true)
        assertTrue(
            "QR code should contain USDC contract address",
            finalState.qrPayload?.contains(KnownTokens.USDC.ethAddress.toString()) == true,
        )
        assertTrue("QR code should contain transfer function", finalState.qrPayload?.contains("/transfer") == true)
        assertTrue("QR code should contain recipient address", finalState.qrPayload?.contains("address=$testAddress") == true)
        assertTrue("QR code should contain amount in minimal units", finalState.qrPayload?.contains("uint256=100500000") == true)
    }

    @Test
    fun `onGenerateQrCode should handle invalid amount format`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("invalid"))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Should show invalid amount format error", "Invalid amount format", finalState.amountError)
        assertNull("QR code should not be generated for invalid amount", finalState.qrPayload)
    }

    @Test
    fun `onGenerateQrCode should handle missing wallet address`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns null
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Should show wallet address error", "No active wallet found", finalState.error)
        assertNull("QR code should not be generated without address", finalState.qrPayload)
    }

    @Test
    fun `onClearAmount should clear amount and QR code`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))
        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)

        val collector = mutableListOf<ReceiveQrViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnClearAmount)

        val finalState = collector.last()
        assertEquals("Amount should be cleared", "", finalState.amount)
        assertNull("Amount error should be cleared", finalState.amountError)
        assertNull("QR code should be cleared", finalState.qrPayload)
    }

    @Test
    fun `onCopyAddress should emit copy news with wallet address`() = runTest {
        val newsCollector = mutableListOf<ReceiveQrNews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { newsCollector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnCopyAddress)

        assertTrue("Should emit copy to clipboard news", newsCollector.any { it is ReceiveQrNews.CopyToClipboard })
        assertTrue("Should emit toast news", newsCollector.any { it is ReceiveQrNews.ShowToast })

        val copyNews = newsCollector.find { it is ReceiveQrNews.CopyToClipboard } as ReceiveQrNews.CopyToClipboard
        assertEquals("Should copy wallet address", testAddress.toString(), copyNews.text)

        val toastNews = newsCollector.find { it is ReceiveQrNews.ShowToast } as ReceiveQrNews.ShowToast
        assertEquals("Should show success message", "Address copied to clipboard", toastNews.message)
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

        assertTrue("Should emit error toast", newsCollector.any { it is ReceiveQrNews.ShowToast })
        val toastNews = newsCollector.find { it is ReceiveQrNews.ShowToast } as ReceiveQrNews.ShowToast
        assertEquals("Should show address not available message", "Address not available", toastNews.message)
    }

    @Test
    fun `should clear QR code when token changes`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))
        viewModel.onEvent(ReceiveQrEvent.OnGenerateQrCode)
        
        testScheduler.advanceUntilIdle()
        
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.USDC)))
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("QR code should be cleared when token changes", finalState.qrPayload)
        assertEquals("Selected token should be updated", SelectedToken.ERC20(KnownTokens.USDC), finalState.selectedToken)
    }

    @Test
    fun `should update amount without automatically generating QR code`() = runTest {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("2.5"))
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("Amount should be updated", "2.5", finalState.amount)
        assertNull("QR code should not be automatically generated", finalState.qrPayload)
    }

    @Test
    fun `should handle different token decimal precision correctly`() = runTest {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(SelectedToken.ERC20(KnownTokens.DAI)))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1." + "0".repeat(18)))
        
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull("Should accept 18 decimal places for DAI", finalState.amountError)
        assertNull("QR code should not be automatically generated", finalState.qrPayload)
    }
}
