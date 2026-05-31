package me.sdi.ethwallet.ui.send

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.TransferException
import me.sdi.ethwallet.fakes.FakeTransferInteractor
import me.sdi.ethwallet.ui.KnownTokens
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var transferInteractor: FakeTransferInteractor
    private lateinit var viewModel: SendViewModel
    private val collectedNews = mutableListOf<SendNews>()
    private val viewStates = mutableListOf<SendViewState>()

    private val validEthAddress = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressNoPrefix = "742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressWrongLength = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB"
    private val invalidAddressWrongFormat = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB1G"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transferInteractor = FakeTransferInteractor()
        viewModel = SendViewModel(transferInteractor)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_initialize_with_default_state() = runTest(testDispatcher) {
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
    fun should_update_token_selection_to_native_ETH() = runTest(testDispatcher) {
        viewModel.onEvent(SendEvent.OnTokenSelected(EthContractAddress))

        val selectedTokenAddress = viewModel.viewState.value.selectedTokenContractAddress
        assertEquals(EthContractAddress, selectedTokenAddress, "Selected token should be ETH")
    }

    @Test
    fun should_update_token_selection_to_ERC20_token() = runTest(testDispatcher) {
        val usdtAddress = KnownTokens.USDT.ethAddress
        viewModel.onEvent(SendEvent.OnTokenSelected(usdtAddress))

        val currentState = viewModel.viewState.value
        assertEquals(usdtAddress, currentState.selectedTokenContractAddress, "Selected token should be USDT")
    }

    @Test
    fun should_update_receiver_address_with_valid_address() = runTest(testDispatcher) {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))

        val currentState = viewModel.viewState.value
        assertEquals(validEthAddress, currentState.receiverAddress.value, "Receiver address should be updated")
        assertNull(currentState.receiverAddress.error, "Receiver address error should be null for valid address")
        assertTrue(currentState.receiverAddress.isValid, "Receiver address should be valid")
    }

    @Test
    fun should_validate_receiver_address_without_0x_prefix() = runTest(testDispatcher) {
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
    fun should_validate_receiver_address_with_wrong_length() = runTest(testDispatcher) {
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
    fun should_validate_receiver_address_with_invalid_format() = runTest(testDispatcher) {
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
    fun should_trim_receiver_address_input() = runTest(testDispatcher) {
        val addressWithSpaces = "  $validEthAddress  "
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(addressWithSpaces))

        val currentState = viewModel.viewState.value
        assertEquals(validEthAddress, currentState.receiverAddress.value, "Address should be trimmed")
        assertNull(currentState.receiverAddress.error, "Trimmed valid address should have no error")
    }

    @Test
    fun should_update_amount_with_valid_value() = runTest(testDispatcher) {
        val validAmount = "1.5"
        viewModel.onEvent(SendEvent.OnAmountChanged(validAmount))

        val currentState = viewModel.viewState.value
        assertEquals(validAmount, currentState.amount.value, "Amount should be updated")
        assertNull(currentState.amount.error, "Amount error should be null for valid amount")
        assertTrue(currentState.amount.isValid, "Amount should be valid")
    }

    @Test
    fun should_validate_negative_amount() = runTest(testDispatcher) {
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
    fun should_validate_zero_amount() = runTest(testDispatcher) {
        val zeroAmount = "0"
        viewModel.onEvent(SendEvent.OnAmountChanged(zeroAmount))

        val currentState = viewModel.viewState.value
        assertEquals("Amount must be greater than zero", currentState.amount.error, "Should have error for zero amount")
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun should_validate_invalid_amount_format() = runTest(testDispatcher) {
        val invalidAmount = "abc"
        viewModel.onEvent(SendEvent.OnAmountChanged(invalidAmount))

        val currentState = viewModel.viewState.value
        assertEquals("Invalid amount format", currentState.amount.error, "Should have error for invalid format")
        assertFalse(currentState.amount.isValid, "Amount should not be valid")
    }

    @Test
    fun should_validate_too_many_decimal_places_for_USDT() = runTest(testDispatcher) {
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
    fun should_allow_valid_decimal_places_for_USDT() = runTest(testDispatcher) {
        val validUsdtAmount = "1.123456"

        viewModel.onEvent(SendEvent.OnTokenSelected(KnownTokens.USDT.ethAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged(validUsdtAmount))

        val currentState = viewModel.viewState.value
        assertNull(currentState.amount.error, "Valid USDT amount should have no error")
        assertTrue(currentState.amount.isValid, "Amount should be valid")
    }

    @Test
    fun should_revalidate_amount_when_token_selection_changes() = runTest(testDispatcher) {
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
    fun should_trim_amount_input() = runTest(testDispatcher) {
        val amountWithSpaces = "  1.5  "
        viewModel.onEvent(SendEvent.OnAmountChanged(amountWithSpaces))

        val amountInputState = viewModel.viewState.value.amount
        assertEquals("1.5", amountInputState.value, "Amount should be trimmed")
        assertNull(amountInputState.error, "Trimmed valid amount should have no error")
    }

    @Test
    fun should_enable_transfer_when_form_is_valid() = runTest(testDispatcher) {
        input1Eth()

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertTrue(isTransferEnabled, "Transfer should be enabled with valid form")
    }

    @Test
    fun should_disable_transfer_when_receiver_address_is_invalid() = runTest(testDispatcher) {
        input1Eth()
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(invalidAddressNoPrefix))
        viewModel.onEvent(SendEvent.OnAmountChanged("1.0"))

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertFalse(isTransferEnabled, "Transfer should be disabled with invalid address")
    }

    @Test
    fun should_disable_transfer_when_amount_is_invalid() = runTest(testDispatcher) {
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged("-1.0"))

        val isTransferEnabled = viewModel.viewState.value.isTransferEnabled
        assertFalse(isTransferEnabled, "Transfer should be disabled with invalid amount")
    }

    @Test
    fun should_disable_transfer_when_loading() = runTest(testDispatcher) {
        transferInteractor.transferResult = Result.success("0xTransactionHash")
        collectNews()
        collectViewStates()

        input1Eth()
        clickSendButton()

        val viewStateBeforeTransferCompletion = viewStates.dropLast(1).last()
        assertTrue(viewStateBeforeTransferCompletion.isLoading, "Should be loading")
        assertFalse(viewStateBeforeTransferCompletion.isTransferEnabled, "Transfer should be disabled while loading")
    }

    @Test
    fun should_emit_validation_error_when_transfer_requested_with_invalid_form() = runTest(testDispatcher) {
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
    fun should_perform_successful_transfer() = runTest(testDispatcher) {
        val expectedHash = "0xTransactionHash123"
        transferInteractor.transferResult = Result.success(expectedHash)
        collectNews()

        input1Eth()
        clickSendButton()

        assertNotNull(transferInteractor.lastTransferData, "Transfer should be invoked")
        val transactionHash = collectedNews.filterIsInstance<SendNews.TransferSuccess>().first().transactionHash
        assertEquals(expectedHash, transactionHash, "Should have correct transaction hash")

        val viewState = viewModel.viewState.value
        assertFalse(viewState.isLoading, "Should not be loading after success")
        assertEquals("", viewState.receiverAddress.value, "Form should be cleared after success")
        assertEquals("", viewState.amount.value, "Amount should be cleared after success")
    }

    @Test
    fun should_handle_insufficient_balance_error() = runTest(testDispatcher) {
        val errorMessage = "Not enough ETH"
        transferInteractor.transferResult = Result.failure(TransferException.InsufficientBalance(errorMessage))
        collectNews()

        input1Eth()
        clickSendButton()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Insufficient balance. $errorMessage", transferErrorMessage, "Should have correct error message")

        val loading = viewModel.viewState.value.isLoading
        assertFalse(loading, "Should not be loading after error")
    }

    @Test
    fun should_handle_network_error() = runTest(testDispatcher) {
        val errorMessage = "Connection timeout"
        transferInteractor.transferResult = Result.failure(
            TransferException.NetworkError(
                errorMessage,
                RuntimeException(),
            ),
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
    fun should_handle_transaction_failed_error() = runTest(testDispatcher) {
        val errorMessage = "Transaction reverted"
        transferInteractor.transferResult = Result.failure(TransferException.TransactionFailed(errorMessage))

        input1Eth()
        collectNews()
        viewModel.onEvent(SendEvent.OnTransferRequested)
        advanceUntilIdle()

        val transferErrorMessage = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Transaction failed. $errorMessage", transferErrorMessage, "Should have transaction failed message")
    }

    @Test
    fun should_handle_unexpected_error() = runTest(testDispatcher) {
        transferInteractor.transferResult = Result.failure(RuntimeException("Unexpected error"))
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
    fun should_clear_form_when_requested() = runTest(testDispatcher) {
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
    fun should_create_correct_transfer_data_for_ETH_transfer() = runTest(testDispatcher) {
        val expectedHash = "0xTransactionHash"
        transferInteractor.transferResult = Result.success(expectedHash)

        input1Eth()
        clickSendButton()

        val captured = transferInteractor.lastTransferData
        assertNotNull(captured, "Transfer data should be captured")
        assertEquals(
            validEthAddress,
            captured.destinationAddress.toString(),
            "Destination address should match",
        )
        assertEquals(EthContractAddress, captured.contractAddress, "Contract address should be ETH")
        assertNotNull(captured.amount, "Amount should not be null")
    }

    @Test
    fun should_create_correct_transfer_data_for_ERC20_transfer() = runTest(testDispatcher) {
        val expectedHash = "0xTransactionHash"
        transferInteractor.transferResult = Result.success(expectedHash)

        val usdtAddress = KnownTokens.USDT.ethAddress
        viewModel.onEvent(SendEvent.OnTokenSelected(usdtAddress))
        viewModel.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        viewModel.onEvent(SendEvent.OnAmountChanged("10.5"))
        clickSendButton()

        val captured = transferInteractor.lastTransferData
        assertNotNull(captured, "Transfer data should be captured")
        assertEquals(usdtAddress, captured.contractAddress, "Contract address should be USDT")
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
