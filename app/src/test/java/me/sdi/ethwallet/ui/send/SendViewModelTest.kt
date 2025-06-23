package me.sdi.ethwallet.ui.send

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.TransferData
import me.sdi.ethwallet.core.transfer.TransferException
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var transferInteractor: TransferInteractor
    private lateinit var viewModel: SendViewModel
    private val collectedNews = mutableListOf<SendNews>()
    private val viewStates = mutableListOf<SendViewState>()

    private val validEthAddress = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressNoPrefix = "742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressWrongLength = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB"
    private val invalidAddressWrongFormat = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB1G"

    @Before
    fun setUp() {
        transferInteractor = mockk()
        viewModel = SendViewModel(transferInteractor)
    }

    @Test
    fun `should initialize with default state`() = runTest {
        val initialState = viewModel.viewState.value

        assertEquals(EthContractAddress, initialState.selectedTokenContractAddress, "Initial token should be ETH")
        assertEquals("", initialState.receiverAddress.value, "Initial receiver address should be empty")
        assertNull(initialState.receiverAddress.error, "Initial receiver address error should be null")
        assertEquals("", initialState.amount.value, "Initial amount should be empty")
        assertNull(initialState.amount.error, "Initial amount error should be null")
        assertFalse(initialState.isLoading, "Initial loading state should be false")
        assertFalse(initialState.isTransferEnabled, "Transfer should not be enabled initially")
    }

    @Test
    fun `should update token selection to native ETH`() = runTest {
        viewModel.onEvent(SendEvent.OnTokenSelected(EthContractAddress))

        val selectedTokenAddress = viewModel.viewState.value.selectedTokenContractAddress
        assertEquals(EthContractAddress, selectedTokenAddress, "Selected token should be ETH")
    }

    @Test
    fun `should update token selection to ERC20 token`() = runTest {
        val usdtAddress = KnownTokens.USDT.ethAddress
        viewModel.onEvent(SendEvent.OnTokenSelected(usdtAddress))

        val currentState = viewModel.viewState.value
        assertEquals(usdtAddress, currentState.selectedTokenContractAddress, "Selected token should be USDT")
    }

    @Test
    fun `should update receiver address with valid address`() = runTest {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))

        val currentState = viewModel.viewState.value
        assertEquals(validEthAddress, currentState.receiverAddress.value, "Receiver address should be updated")
        assertNull(currentState.receiverAddress.error, "Receiver address error should be null for valid address")
        assertTrue(currentState.receiverAddress.isValid, "Receiver address should be valid")
    }

    @Test
    fun `should validate receiver address without 0x prefix`() = runTest {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(invalidAddressNoPrefix))

        val currentState = viewModel.viewState.value
        assertEquals(
            invalidAddressNoPrefix,
            currentState.receiverAddress.value,
            "Receiver address value should be updated",
        )
        assertEquals(
            "Address must start with 0x",
            currentState.receiverAddress.error,
            "Should have error for missing 0x prefix",
        )
        assertFalse(currentState.receiverAddress.isValid, "Receiver address should not be valid")
    }

    @Test
    fun `should validate receiver address with wrong length`() = runTest {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(invalidAddressWrongLength))

        val currentState = viewModel.viewState.value
        assertEquals(
            "Address must be exactly 42 characters",
            currentState.receiverAddress.error,
            "Should have error for wrong length",
        )
        assertFalse(currentState.receiverAddress.isValid, "Receiver address should not be valid")
    }

    @Test
    fun `should validate receiver address with invalid format`() = runTest {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(invalidAddressWrongFormat))

        val currentState = viewModel.viewState.value
        assertEquals(
            "Invalid address format",
            currentState.receiverAddress.error,
            "Should have error for invalid format",
        )
        assertFalse(currentState.receiverAddress.isValid, "Receiver address should not be valid")
    }

    @Test
    fun `should trim receiver address input`() = runTest {
        val addressWithSpaces = "  $validEthAddress  "
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(addressWithSpaces))

        val currentState = viewModel.viewState.value
        assertEquals(validEthAddress, currentState.receiverAddress.value, "Address should be trimmed")
        assertNull(currentState.receiverAddress.error, "Trimmed valid address should have no error")
    }

    @Test
    fun `should update amount with valid value`() = runTest {
        val validAmount = "1.5"
        viewModel.onEvent(SendEvent.OnAmountChanged(validAmount))

        val currentState = viewModel.viewState.value
        assertEquals(validAmount, currentState.amount.value, "Amount should be updated")
        assertNull(currentState.amount.error, "Amount error should be null for valid amount")
        assertTrue(currentState.amount.isValid, "Amount should be valid")
    }

    @Test
    fun `should validate negative amount`() = runTest {
        val negativeAmount = "-1.5"
        viewModel.onEvent(SendEvent.OnAmountChanged(negativeAmount))

        val currentState = viewModel.viewState.value
        assertEquals(negativeAmount, currentState.amount.value, "Amount value should be updated")
        assertEquals(
            "Amount must be greater than zero",
            currentState.amount.error,
            "Should have error for negative amount",
        )
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun `should validate zero amount`() = runTest {
        val zeroAmount = "0"
        viewModel.onEvent(SendEvent.OnAmountChanged(zeroAmount))

        val currentState = viewModel.viewState.value
        assertEquals("Amount must be greater than zero", currentState.amount.error, "Should have error for zero amount")
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun `should validate invalid amount format`() = runTest {
        val invalidAmount = "abc"
        viewModel.onEvent(SendEvent.OnAmountChanged(invalidAmount))

        val currentState = viewModel.viewState.value
        assertEquals("Invalid amount format", currentState.amount.error, "Should have error for invalid format")
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun `should validate too many decimal places for USDT`() = runTest {
        val amountWithTooManyDecimals = "1.1234567"

        viewModel.onEvent(SendEvent.OnTokenSelected(KnownTokens.USDT.ethAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged(amountWithTooManyDecimals))

        val currentState = viewModel.viewState.value
        assertEquals(
            "Too many decimal places for selected token",
            currentState.amount.error,
            "Should have error for too many decimal places",
        )
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun `should allow valid decimal places for USDT`() = runTest {
        val validUsdtAmount = "1.123456"

        viewModel.onEvent(SendEvent.OnTokenSelected(KnownTokens.USDT.ethAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged(validUsdtAmount))

        val currentState = viewModel.viewState.value
        assertNull(currentState.amount.error, "Valid USDT amount should have no error")
        assertTrue(currentState.amount.isValid, "Amount should be valid")
    }

    @Test
    fun `should revalidate amount when token selection changes`() = runTest {
        val amountWithManyDecimals = "1.123456789012345678"
        viewModel.onEvent(SendEvent.OnAmountChanged(amountWithManyDecimals))
        val ethError = viewModel.viewState.value.amount.error
        viewModel.onEvent(SendEvent.OnTokenSelected(KnownTokens.USDT.ethAddress))
        val usdtError = viewModel.viewState.value.amount.error

        assertNull(ethError, "ETH amount with many decimals should be valid")
        assertNotNull(usdtError, "Amount should be invalid for USDT due to too many decimals")
        assertEquals(
            "Too many decimal places for selected token",
            usdtError,
            "Should have decimal places error",
        )
    }

    @Test
    fun `should trim amount input`() = runTest {
        val amountWithSpaces = "  1.5  "
        viewModel.onEvent(SendEvent.OnAmountChanged(amountWithSpaces))

        val amountInputState = viewModel.viewState.value.amount
        assertEquals("1.5", amountInputState.value, "Amount should be trimmed")
        assertNull(amountInputState.error, "Trimmed valid amount should have no error")
    }

    @Test
    fun `should enable transfer when form is valid`() = runTest {
        input1Eth()

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertTrue(isTransferEnabled, "Transfer should be enabled with valid form")
    }

    @Test
    fun `should disable transfer when receiver address is invalid`() = runTest {
        input1Eth()
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(invalidAddressNoPrefix))
        viewModel.onEvent(SendEvent.OnAmountChanged("1.0"))

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertFalse(isTransferEnabled, "Transfer should be disabled with invalid address")
    }

    @Test
    fun `should disable transfer when amount is invalid`() = runTest {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged("-1.0"))

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertFalse(isTransferEnabled, "Transfer should be disabled with invalid amount")
    }

    @Test
    fun `should disable transfer when loading`() = runTest {
        coEvery { transferInteractor.transfer(any()) } returns "0xTransactionHash"
        collectNews()
        collectViewStates()

        input1Eth()
        clickSendButton()

        val viewStateBeforeTransferCompletion = viewStates.dropLast(1).last()
        assertTrue(viewStateBeforeTransferCompletion.isLoading, "Should be loading")
        assertFalse(viewStateBeforeTransferCompletion.isTransferEnabled, "Transfer should be disabled while loading")
    }

    @Test
    fun `should emit validation error when transfer requested with invalid form`() = runTest {
        collectNews()

        clickSendButton()

        val validationErrorMessage = collectedNews.filterIsInstance<SendNews.ValidationError>().first().message
        assertEquals(
            "Please fix all validation errors before proceeding",
            validationErrorMessage,
            "Should have correct validation message",
        )
    }

    @Test
    fun `should perform successful transfer`() = runTest {
        val expectedHash = "0xTransactionHash123"
        coEvery { transferInteractor.transfer(any()) } returns expectedHash
        collectNews()

        input1Eth()
        clickSendButton()

        coVerify { transferInteractor.transfer(any()) }
        val transactionHash = collectedNews.filterIsInstance<SendNews.TransferSuccess>().first().transactionHash
        assertEquals(expectedHash, transactionHash, "Should have correct transaction hash")

        val viewState = viewModel.viewState.value
        assertFalse(viewState.isLoading, "Should not be loading after success")
        assertEquals("", viewState.receiverAddress.value, "Form should be cleared after success")
        assertEquals("", viewState.amount.value, "Amount should be cleared after success")
    }

    @Test
    fun `should handle insufficient balance error`() = runTest {
        val errorMessage = "Not enough ETH"
        coEvery { transferInteractor.transfer(any()) } throws TransferException.InsufficientBalance(errorMessage)
        collectNews()

        input1Eth()
        clickSendButton()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Insufficient balance. $errorMessage", transferErrorMessage, "Should have correct error message")

        val loading = viewModel.viewState.value.isLoading
        assertFalse(loading, "Should not be loading after error")
    }

    @Test
    fun `should handle network error`() = runTest {
        val errorMessage = "Connection timeout"
        coEvery { transferInteractor.transfer(any()) } throws TransferException.NetworkError(
            errorMessage,
            RuntimeException(),
        )
        collectNews()

        input1Eth()
        clickSendButton()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals(
            "Network error occurred. Please check your connection and try again.",
            transferErrorMessage,
            "Should have network error message",
        )
    }

    @Test
    fun `should handle transaction failed error`() = runTest {
        val errorMessage = "Transaction reverted"
        coEvery { transferInteractor.transfer(any()) } throws TransferException.TransactionFailed(errorMessage)

        input1Eth()
        collectNews()
        viewModel.onEvent(SendEvent.OnTransferRequested)
        advanceUntilIdle()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Transaction failed. $errorMessage", transferErrorMessage, "Should have transaction failed message")
    }

    @Test
    fun `should handle unexpected error`() = runTest {
        coEvery { transferInteractor.transfer(any()) } throws RuntimeException("Unexpected error")
        collectNews()

        input1Eth()
        clickSendButton()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals(
            "An unexpected error occurred. Please try again.",
            transferErrorMessage,
            "Should have generic error message",
        )
    }

    @Test
    fun `should clear form when requested`() = runTest {
        input1Eth()
        viewModel.onEvent(SendEvent.OnClearForm)

        val currentState = viewModel.viewState.value
        assertEquals(EthContractAddress, currentState.selectedTokenContractAddress, "Token should be reset to ETH")
        assertEquals("", currentState.receiverAddress.value, "Receiver address should be cleared")
        assertEquals("", currentState.amount.value, "Amount should be cleared")
        assertNull(currentState.receiverAddress.error, "Receiver address error should be null")
        assertNull(currentState.amount.error, "Amount error should be null")
        assertFalse(currentState.isLoading, "Loading should be false")
        assertFalse(currentState.isTransferEnabled, "Transfer should not be enabled")
    }

    @Test
    fun `should create correct transfer data for ETH transfer`() = runTest {
        val expectedHash = "0xTransactionHash"
        val transferDataSlot = slot<TransferData>()
        coEvery { transferInteractor.transfer(capture(transferDataSlot)) } returns expectedHash

        input1Eth()
        clickSendButton()

        assertTrue(transferDataSlot.isCaptured, "Transfer data should be captured")
        assertEquals(
            validEthAddress,
            transferDataSlot.captured.destinationAddress.toString(),
            "Destination address should match",
        )
        assertEquals(EthContractAddress, transferDataSlot.captured.contractAddress, "Contract address should be ETH")
        assertNotNull(transferDataSlot.captured.amount, "Amount should not be null")
    }

    @Test
    fun `should create correct transfer data for ERC20 transfer`() = runTest {
        val expectedHash = "0xTransactionHash"
        val transferDataSlot = slot<TransferData>()
        coEvery { transferInteractor.transfer(capture(transferDataSlot)) } returns expectedHash

        val usdtAddress = KnownTokens.USDT.ethAddress
        viewModel.onEvent(SendEvent.OnTokenSelected(usdtAddress))
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged("10.5"))
        clickSendButton()

        assertTrue(transferDataSlot.isCaptured, "Transfer data should be captured")
        assertEquals(usdtAddress, transferDataSlot.captured.contractAddress, "Contract address should be USDT")
    }

    private fun input1Eth() {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged("1.0"))
    }

    private fun TestScope.collectViewStates() {
        viewStates.clear()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { viewState ->
                viewStates.add(viewState)
            }
        }
    }

    private fun TestScope.collectNews() {
        collectedNews.clear()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { news ->
                collectedNews.add(news)
            }
        }
    }

    private fun TestScope.clickSendButton() {
        viewModel.onEvent(SendEvent.OnTransferRequested)
        advanceUntilIdle()
    }
}
