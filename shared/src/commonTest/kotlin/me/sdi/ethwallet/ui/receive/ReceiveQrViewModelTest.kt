package me.sdi.ethwallet.ui.receive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.ui.daiOnMainnet
import me.sdi.ethwallet.ui.usdcOnMainnet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveQrViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: ReceiveQrViewModel

    private val testAddress = EthAddress("0x742d35Cc6634C0532925a3b8D2A9C0Eeb5D6d0f2")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        ethManager = FakeEthManager()
        privateKeyEncryptor = FakePrivateKeyEncryptor()

        walletDao.wallets = listOf(
            WalletEntity(
                address = testAddress.toString(),
                encryptedPrivateKeyBase64 = "encrypted_key_1",
                label = "Test Wallet",
            ),
        )
        walletDao.currentWalletAddress = testAddress.toString()
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = privateKeyEncryptor,
        )
        viewModel = ReceiveQrViewModel(walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_should_have_correct_default_values() = runTest(testDispatcher) {
        val state = viewModel.viewState.first()

        assertEquals(EthContractAddress, state.selectedTokenContract, "Initial token should be EthContractAddress")
        assertEquals("", state.amount, "Initial amount should be empty")
        assertNull(state.amountError, "Initial amount error should be null")
        assertNull(state.qrPayload, "Initial QR code URI should be null")
    }

    @Test
    fun should_handle_no_active_wallet_gracefully() = runTest(testDispatcher) {
        walletDao.wallets = emptyList()
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(EthContractAddress, finalState.selectedTokenContract, "Initial token should be EthContractAddress")
        assertEquals("", finalState.amount, "Initial amount should be empty")
        assertNull(finalState.qrPayload, "QR payload should be null without wallet")
    }

    @Test
    fun should_handle_wallet_repository_exception_during_initialization() = runTest(testDispatcher) {
        walletDao.shouldThrowException = true
        val viewModel = ReceiveQrViewModel(walletRepository)

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(EthContractAddress, finalState.selectedTokenContract, "Token should be EthContractAddress even when exception occurs")
        assertEquals("", finalState.amount, "Amount should be empty")
        assertNull(finalState.qrPayload, "QR payload should be null on error")
    }

    @Test
    fun onTokenSelected_should_update_selected_token() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(usdcOnMainnet.ethAddress))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(usdcOnMainnet.ethAddress, finalState.selectedTokenContract, "Selected token should be USDC")
        assertNull(finalState.amountError, "Amount error should remain null")
    }

    @Test
    fun onAmountChanged_should_update_amount() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("1.5", finalState.amount, "Amount should be updated")
        assertNull(finalState.amountError, "Amount error should be null for valid amount")
    }

    @Test
    fun onAmountChanged_should_validate_negative_amounts() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("-1.5"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("-1.5", finalState.amount, "Amount should be updated even if invalid")
        assertEquals("Amount must be greater than zero", finalState.amountError, "Should show error for negative amount")
    }

    @Test
    fun onAmountChanged_should_validate_zero_amounts() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("0"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("0", finalState.amount, "Amount should be updated")
        assertEquals("Amount must be greater than zero", finalState.amountError, "Should show error for zero amount")
    }

    @Test
    fun onAmountChanged_should_validate_excessive_decimal_places_for_USDC() = runTest(testDispatcher) {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(usdcOnMainnet.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.1234567"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("1.1234567", finalState.amount, "Amount should be updated")
        assertEquals("Too many decimal places for selected token", finalState.amountError, "Should show decimal places error for USDC")
    }

    @Test
    fun onAmountChanged_should_accept_valid_decimal_places_for_USDC() = runTest(testDispatcher) {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(usdcOnMainnet.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.123456"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("1.123456", finalState.amount, "Amount should be updated")
        assertNull(finalState.amountError, "Amount error should be null for valid USDC amount")
    }

    @Test
    fun onAmountChanged_should_handle_invalid_number_format() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("abc"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("abc", finalState.amount, "Amount should be updated")
        assertEquals("Invalid amount", finalState.amountError, "Amount error should show invalid amount for non-numeric string")
    }

    @Test
    fun should_generate_ETH_QR_code_automatically_when_token_selected() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(EthContractAddress))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(finalState.qrPayload?.isNotEmpty(), true, "QR code URI should be generated")
        assertEquals(finalState.qrPayload?.contains(testAddress.toString()), true, "QR code should contain ETH address")
        assertEquals(finalState.qrPayload?.startsWith("ethereum:"), true, "QR code should be ETH format")
        assertNotEquals(finalState.qrPayload?.contains("value="), true, "QR code should not contain amount parameter")
    }

    @Test
    fun should_generate_QR_code_when_token_and_amount_are_selected() = runTest(testDispatcher) {
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
        assertEquals(EthContractAddress, finalState.selectedTokenContract, "Token should be selected")
        assertEquals("1.5", finalState.amount, "Amount should be set")
        assertNull(finalState.amountError, "Amount should be valid")
        assertEquals(
            finalState.qrPayload?.isNotEmpty(),
            true,
            "QR code should be generated when token and amount are valid"
        )
    }

    @Test
    fun should_generate_USDC_QR_code_automatically() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(usdcOnMainnet.ethAddress))
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("100.5"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(usdcOnMainnet.ethAddress, finalState.selectedTokenContract, "Token should be USDC")
        assertEquals("100.5", finalState.amount, "Amount should be set")
        assertNull(finalState.amountError, "Amount should be valid for USDC")
        assertTrue(finalState.qrPayload?.isNotEmpty() == true, "QR code should be generated")
    }

    @Test
    fun onClearAmount_should_clear_amount() = runTest(testDispatcher) {
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))
        testScheduler.advanceUntilIdle()

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnClearAmount)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("", finalState.amount, "Amount should be cleared")
        assertNull(finalState.amountError, "Amount error should be cleared")
    }

    @Test
    fun onCopyAddress_should_emit_copy_news_with_wallet_address() = runTest(testDispatcher) {
        val newsCollector = mutableListOf<ReceiveQrNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { newsCollector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnCopyAddress)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(newsCollector.any { it is ReceiveQrNews.CopyToClipboard }, "Should emit copy to clipboard news")

        val copyNews = newsCollector.find { it is ReceiveQrNews.CopyToClipboard } as ReceiveQrNews.CopyToClipboard
        assertEquals(testAddress.toString(), copyNews.text, "Should copy wallet address")
    }

    @Test
    fun onCopyAddress_should_handle_missing_wallet_address() = runTest(testDispatcher) {
        walletDao.wallets = emptyList()
        val viewModel = ReceiveQrViewModel(walletRepository)

        val newsCollector = mutableListOf<ReceiveQrNews>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { newsCollector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnCopyAddress)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(newsCollector.any { it is ReceiveQrNews.ShowToast }, "Should emit error news")
        val toastNews = newsCollector.find { it is ReceiveQrNews.ShowToast } as ReceiveQrNews.ShowToast
        assertEquals("Address not available", toastNews.message, "Should show address not available message")
    }

    @Test
    fun should_update_QR_code_when_token_changes() = runTest(testDispatcher) {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(EthContractAddress))
        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1.5"))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(usdcOnMainnet.ethAddress))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals(usdcOnMainnet.ethAddress, finalState.selectedTokenContract, "Selected token should be updated")
        assertTrue(
            finalState.qrPayload?.contains(usdcOnMainnet.ethAddress.toString()) == true,
            "QR code should be updated for new token",
        )
    }

    @Test
    fun should_update_amount_correctly() = runTest(testDispatcher) {
        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("2.5"))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertEquals("2.5", finalState.amount, "Amount should be updated")
        assertNull(finalState.amountError, "Amount error should be null for valid amount")
    }

    @Test
    fun should_handle_different_token_decimal_precision_correctly() = runTest(testDispatcher) {
        viewModel.onEvent(ReceiveQrEvent.OnTokenSelected(daiOnMainnet.ethAddress))

        val collector = mutableListOf<ReceiveQrViewState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { collector.add(it) }
        }

        viewModel.onEvent(ReceiveQrEvent.OnAmountChanged("1." + "0".repeat(18)))

        testScheduler.advanceUntilIdle()
        job.cancel()

        val finalState = collector.last()
        assertNull(finalState.amountError, "Should accept 18 decimal places for DAI")
    }
}
