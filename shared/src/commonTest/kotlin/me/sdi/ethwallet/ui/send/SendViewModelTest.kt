package me.sdi.ethwallet.ui.send

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.core.transfer.TransferException
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.fakes.FakeFiatRateProvider
import me.sdi.ethwallet.fakes.FakeTransferInteractor
import me.sdi.ethwallet.ui.usdtOnMainnet
import me.sdi.ethwallet.util.formatUsdCurrency
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var transferInteractor: FakeTransferInteractor
    private lateinit var fiatRateProvider: FakeFiatRateProvider
    private lateinit var viewModel: SendViewModel
    private val activeViewModels = mutableListOf<SendViewModel>()
    private val collectedNews = mutableListOf<SendNews>()
    private val viewStates = mutableListOf<SendViewState>()

    private val baseFeeWei = BigInteger("20000000000")
    private val priorityFeeWei = BigInteger("2000000000")
    private val feeSuggestion = FeeSuggestion(Wei(baseFeeWei), Wei(priorityFeeWei))
    private val ethRate = BigDecimal("2000.00")

    private val validEthAddress = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressNoPrefix = "742d35cc6634C0532925a3b8d73a726FbCF3eB11"
    private val invalidAddressWrongLength = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB"
    private val invalidAddressWrongFormat = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB1G"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transferInteractor = FakeTransferInteractor()
        fiatRateProvider = FakeFiatRateProvider()
        activeViewModels.clear()
        viewModel = newViewModel()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): SendViewModel =
        SendViewModel(transferInteractor, fiatRateProvider, savedStateHandle).also { activeViewModels += it }

    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest(testDispatcher, timeout = 10.seconds) {
        try {
            body()
        } finally {
            activeViewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_initialize_with_default_state() = vmTest {
        val initialState = viewModel.viewState.value

        assertEquals(EthContractAddress, initialState.selectedTokenContractAddress, "Initial token should be ETH")
        assertEquals("", initialState.receiverAddress.value, "Initial receiver address should be empty")
        assertNull(initialState.receiverAddress.error, "Initial receiver address error should be null")
        assertEquals("", initialState.transferAmountInputState.value, "Initial amount should be empty")
        assertNull(initialState.transferAmountInputState.error, "Initial amount error should be null")
        assertFalse(initialState.transferring, "Initial loading state should be false")
        assertFalse(initialState.isTransferEnabled, "Transfer should not be enabled initially")
        assertEquals(
            GasOption.Default,
            initialState.gas.chips.first { it.selected }.option,
            "Default gas option should be selected initially",
        )
    }

    @Test
    fun should_update_token_selection_to_native_ETH() = vmTest {
        sendEvent(SendEvent.OnTokenSelected(EthContractAddress))

        assertEquals(
            EthContractAddress,
            viewModel.viewState.value.selectedTokenContractAddress,
            "Selected token should be ETH",
        )
    }

    @Test
    fun should_update_token_selection_to_ERC20_token() = vmTest {
        val usdtAddress = usdtOnMainnet.ethAddress
        sendEvent(SendEvent.OnTokenSelected(usdtAddress))

        assertEquals(
            usdtAddress,
            viewModel.viewState.value.selectedTokenContractAddress,
            "Selected token should be USDT",
        )
    }

    @Test
    fun should_update_receiver_address_with_valid_address() = vmTest {
        sendEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))

        val state = viewModel.viewState.value
        assertEquals(validEthAddress, state.receiverAddress.value, "Receiver address should be updated")
        assertNull(state.receiverAddress.error, "Receiver address error should be null for valid address")
        assertTrue(state.receiverAddress.isValid, "Receiver address should be valid")
    }

    @Test
    fun should_validate_receiver_address_without_0x_prefix() = vmTest {
        sendEvent(SendEvent.OnReceiverAddressChanged(invalidAddressNoPrefix))

        val state = viewModel.viewState.value
        assertEquals(invalidAddressNoPrefix, state.receiverAddress.value, "Receiver address value should be updated")
        assertEquals("Invalid address format", state.receiverAddress.error, "Should have error for missing 0x prefix")
        assertFalse(state.receiverAddress.isValid, "Receiver address should not be valid")
    }

    @Test
    fun should_validate_receiver_address_with_wrong_length() = vmTest {
        sendEvent(SendEvent.OnReceiverAddressChanged(invalidAddressWrongLength))

        assertEquals(
            "Invalid address format",
            viewModel.viewState.value.receiverAddress.error,
            "Should have error for wrong length",
        )
    }

    @Test
    fun should_validate_receiver_address_with_invalid_format() = vmTest {
        sendEvent(SendEvent.OnReceiverAddressChanged(invalidAddressWrongFormat))

        assertEquals(
            "Invalid address format",
            viewModel.viewState.value.receiverAddress.error,
            "Should have error for invalid format",
        )
    }

    @Test
    fun should_trim_receiver_address_input() = vmTest {
        sendEvent(SendEvent.OnReceiverAddressChanged("  $validEthAddress  "))

        val state = viewModel.viewState.value
        assertEquals(validEthAddress, state.receiverAddress.value, "Address should be trimmed")
        assertNull(state.receiverAddress.error, "Trimmed valid address should have no error")
    }

    @Test
    fun should_update_amount_with_valid_value() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("1.5"))

        val state = viewModel.viewState.value
        assertEquals("1.5", state.transferAmountInputState.value, "Amount should be updated")
        assertNull(state.transferAmountInputState.error, "Amount error should be null for valid amount")
        assertTrue(state.transferAmountInputState.isValid, "Amount should be valid")
    }

    @Test
    fun should_validate_negative_amount() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("-1.5"))

        assertEquals(
            "Amount must be greater than zero",
            viewModel.viewState.value.transferAmountInputState.error,
            "Should have error for negative amount",
        )
    }

    @Test
    fun should_validate_zero_amount() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("0"))

        assertEquals(
            "Amount must be greater than zero",
            viewModel.viewState.value.transferAmountInputState.error,
            "Should have error for zero amount",
        )
    }

    @Test
    fun should_validate_invalid_amount_format() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("abc"))

        assertEquals(
            "Invalid amount format",
            viewModel.viewState.value.transferAmountInputState.error,
            "Should have error for invalid format",
        )
    }

    @Test
    fun should_validate_too_many_decimal_places_for_USDT() = vmTest {
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("1.1234567"))

        assertEquals(
            "Too many decimal places for selected token",
            viewModel.viewState.value.transferAmountInputState.error,
            "Should have error for too many decimal places",
        )
    }

    @Test
    fun should_allow_valid_decimal_places_for_USDT() = vmTest {
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("1.123456"))

        assertNull(viewModel.viewState.value.transferAmountInputState.error, "Valid USDT amount should have no error")
    }

    @Test
    fun should_revalidate_amount_when_token_selection_changes() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("1.123456789012345678"))
        val ethError = viewModel.viewState.value.transferAmountInputState.error
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))
        val usdtError = viewModel.viewState.value.transferAmountInputState.error

        assertNull(ethError, "ETH amount with many decimals should be valid")
        assertEquals(
            "Too many decimal places for selected token",
            usdtError,
            "Should have decimal places error for USDT",
        )
    }

    @Test
    fun should_trim_amount_input() = vmTest {
        sendEvent(SendEvent.OnTransferAmountChanged("  1.5  "))

        assertEquals("1.5", viewModel.viewState.value.transferAmountInputState.value, "Amount should be trimmed")
    }

    @Test
    fun should_enable_transfer_when_form_is_valid() = vmTest {
        seedFees()
        input1Eth()

        assertTrue(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be enabled with valid form and preset fee",
        )
    }

    @Test
    fun should_disable_transfer_when_receiver_address_is_invalid() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnReceiverAddressChanged(invalidAddressNoPrefix))
        sendEvent(SendEvent.OnTransferAmountChanged("1.0"))

        assertFalse(viewModel.viewState.value.isTransferEnabled, "Transfer should be disabled with invalid address")
    }

    @Test
    fun should_disable_transfer_when_amount_is_invalid() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("-1.0"))

        assertFalse(viewModel.viewState.value.isTransferEnabled, "Transfer should be disabled with invalid amount")
    }

    @Test
    fun should_disable_transfer_when_loading() = vmTest {
        transferInteractor.transferResult = Result.success("0xTransactionHash")
        seedFees()
        collectViewStates()

        input1Eth()
        clickSend()

        val loadingState = viewStates.firstOrNull { it.transferring }
        assertNotNull(loadingState, "A loading state should be emitted during the transfer")
        assertFalse(loadingState.isTransferEnabled, "Transfer should be disabled while loading")
    }

    @Test
    fun should_ignore_transfer_request_when_form_invalid() = vmTest {
        collectNews()

        clickSend()

        assertTrue(collectedNews.isEmpty(), "No news should be emitted when the form is invalid")
        assertNull(transferInteractor.lastTransferData, "No transfer should be attempted when the form is invalid")
    }

    @Test
    fun should_perform_successful_transfer() = vmTest {
        val expectedHash = "0xTransactionHash123"
        transferInteractor.transferResult = Result.success(expectedHash)
        seedFees()
        collectNews()

        input1Eth()
        clickSend()

        assertNotNull(transferInteractor.lastTransferData, "Transfer should be invoked")
        val transactionHash = collectedNews.filterIsInstance<SendNews.TransferSuccess>().first().transactionHash
        assertEquals(expectedHash, transactionHash, "Should have correct transaction hash")

        val state = viewModel.viewState.value
        assertFalse(state.transferring, "Should not be loading after success")
        assertEquals("", state.receiverAddress.value, "Form should be cleared after success")
        assertEquals("", state.transferAmountInputState.value, "Amount should be cleared after success")
    }

    @Test
    fun should_handle_insufficient_balance_error() = vmTest {
        val errorMessage = "Not enough ETH"
        transferInteractor.transferResult = Result.failure(TransferException.InsufficientBalance(errorMessage))
        seedFees()
        collectNews()

        input1Eth()
        clickSend()

        val message = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Insufficient balance. $errorMessage", message, "Should have correct error message")
    }

    @Test
    fun should_handle_network_error() = vmTest {
        transferInteractor.transferResult =
            Result.failure(TransferException.NetworkError("Connection timeout", RuntimeException()))
        seedFees()
        collectNews()

        input1Eth()
        clickSend()

        val message = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals(
            "Network error occurred. Please check your connection and try again.",
            message,
            "Should have network error message",
        )
    }

    @Test
    fun should_handle_transaction_failed_error() = vmTest {
        val errorMessage = "Transaction reverted"
        transferInteractor.transferResult = Result.failure(TransferException.TransactionFailed(errorMessage))
        seedFees()
        collectNews()

        input1Eth()
        clickSend()

        val message = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("Transaction failed. $errorMessage", message, "Should have transaction failed message")
    }

    @Test
    fun should_handle_unexpected_error() = vmTest {
        transferInteractor.transferResult = Result.failure(RuntimeException("Unexpected error"))
        seedFees()
        collectNews()

        input1Eth()
        clickSend()

        val message = collectedNews.filterIsInstance<SendNews.TransferError>().first().message
        assertEquals("An unexpected error occurred. Please try again.", message, "Should have generic error message")
    }

    @Test
    fun should_clear_form_when_requested() = vmTest {
        input1Eth()
        sendEvent(SendEvent.OnClearFormClick)

        val state = viewModel.viewState.value
        assertEquals(EthContractAddress, state.selectedTokenContractAddress, "Token should be reset to ETH")
        assertEquals("", state.receiverAddress.value, "Receiver address should be cleared")
        assertEquals("", state.transferAmountInputState.value, "Amount should be cleared")
        assertFalse(state.isTransferEnabled, "Transfer should not be enabled")
    }

    @Test
    fun should_default_to_default_gas_option() = vmTest {
        val selected = viewModel.viewState.value.gas.chips.first { it.selected }
        assertEquals(GasOption.Default, selected.option, "Default gas option should be selected by default")
    }

    @Test
    fun should_update_selection_when_gas_option_selected() = vmTest {
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.LowFees))
        assertEquals(
            GasOption.LowFees,
            viewModel.viewState.value.gas.chips.first { it.selected }.option,
            "Selecting Low should mark it as the selected option",
        )

        sendEvent(SendEvent.OnGasOptionSelected(GasOption.FastTransfer))
        assertEquals(
            GasOption.FastTransfer,
            viewModel.viewState.value.gas.chips.first { it.selected }.option,
            "Selecting Fast should mark it as the selected option",
        )
    }

    @Test
    fun should_compute_preset_chip_fee_usd_for_ETH() = vmTest {
        seedFees()

        val chips = viewModel.viewState.value.gas.chips
        assertEquals(4, chips.size, "There should be four gas chips (Low/Default/Fast/Custom)")
        assertEquals(
            listOf(GasOption.LowFees, GasOption.Default, GasOption.FastTransfer, GasOption.CustomFees),
            chips.map { it.option },
            "Gas chips should be in Low/Default/Fast/Custom order",
        )
        assertEquals(
            expectedFeeUsd(presetMaxFeeWei(GasOption.LowFees), ETH_GAS_UNITS),
            chips[0].feeUsd,
            "Low chip should show the worst-case fee on the 21000 ETH basis",
        )
        assertEquals(
            expectedFeeUsd(presetMaxFeeWei(GasOption.Default), ETH_GAS_UNITS),
            chips[1].feeUsd,
            "Default chip should show the worst-case fee on the 21000 ETH basis",
        )
        assertEquals(
            expectedFeeUsd(presetMaxFeeWei(GasOption.FastTransfer), ETH_GAS_UNITS),
            chips[2].feeUsd,
            "Fast chip should show the worst-case fee on the 21000 ETH basis",
        )
        assertTrue(
            presetMaxFeeWei(GasOption.LowFees) < presetMaxFeeWei(GasOption.Default) &&
                presetMaxFeeWei(GasOption.Default) < presetMaxFeeWei(GasOption.FastTransfer),
            "Preset max fees should strictly increase from Low to Fast",
        )
    }

    @Test
    fun should_switch_preset_basis_to_erc20_when_token_selected() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))

        val defaultChip = viewModel.viewState.value.gas.chips.first { it.option == GasOption.Default }
        assertEquals(
            expectedFeeUsd(presetMaxFeeWei(GasOption.Default), ERC20_GAS_UNITS),
            defaultChip.feeUsd,
            "Selecting an ERC-20 token should switch the preset fee basis to 65000 gas units",
        )
    }

    @Test
    fun should_hide_chip_fee_usd_when_rate_absent() = vmTest {
        transferInteractor.feeSuggestionToReturn = feeSuggestion
        fiatRateProvider.rates.value = emptyMap()
        runCurrent()

        val chips = viewModel.viewState.value.gas.chips
        assertTrue(chips.all { it.feeUsd == null }, "Without an ETH rate no chip should carry a fee estimate")
    }

    @Test
    fun should_disable_presets_and_block_gate_when_suggestion_absent() = vmTest {
        collectNews()
        input1Eth()

        val chips = viewModel.viewState.value.gas.chips
        assertTrue(
            chips.filter { it.option != GasOption.CustomFees }.none { it.enabled },
            "Preset chips should be disabled while the fee suggestion is unavailable",
        )
        assertTrue(
            chips.first { it.option == GasOption.CustomFees }.enabled,
            "The Custom chip should stay enabled while the fee suggestion is unavailable",
        )
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be blocked when a preset is selected but no fee suggestion is available",
        )

        clickSend()
        assertTrue(collectedNews.isEmpty(), "No news should be emitted when the preset transfer is blocked")
        assertNull(transferInteractor.lastTransferData, "No transfer should be attempted without a fee suggestion")
    }

    @Test
    fun should_prefill_custom_fees_on_first_suggestion_emission() = vmTest {
        seedFees()

        val gas = viewModel.viewState.value.gas
        assertEquals("30", gas.baseFee.value, "Base fee should prefill to the Default preset max base fee (baseFee x 3/2)")
        assertEquals("2", gas.priorityFee.value, "Priority fee should prefill to the Default preset priority fee")
        assertEquals("", gas.gasUnits.value, "Gas units input stays blank for ETH")
        assertFalse(gas.gasUnitsEditable, "Gas units should not be editable for ETH")
        assertEquals(
            GasOption.Default,
            gas.chips.first { it.selected }.option,
            "Prefill happens on emission regardless of the selected option",
        )
    }

    @Test
    fun should_show_equal_custom_and_default_fee_usd_before_typing() = vmTest {
        seedFees()

        val chips = viewModel.viewState.value.gas.chips
        val defaultFeeUsd = chips.first { it.option == GasOption.Default }.feeUsd
        val customFeeUsd = chips.first { it.option == GasOption.CustomFees }.feeUsd
        assertNotNull(defaultFeeUsd, "Default chip should show a fee once the suggestion and rate are available")
        assertEquals(defaultFeeUsd, customFeeUsd, "The prefilled custom chip should show the same USD as the Default chip")
    }

    @Test
    fun should_prefill_gas_units_for_erc20_after_token_switch() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))

        val gas = viewModel.viewState.value.gas
        assertEquals("30", gas.baseFee.value, "Base fee prefill should survive the token switch")
        assertEquals("2", gas.priorityFee.value, "Priority fee prefill should survive the token switch")
        assertEquals("65000", gas.gasUnits.value, "Switching to an ERC-20 token should fill the blank gas units with its default")
        assertTrue(gas.gasUnitsEditable, "Gas units should be editable for an ERC-20 token")
    }

    @Test
    fun should_fill_gas_units_on_switch_to_erc20_when_blank() = vmTest {
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))

        val gas = viewModel.viewState.value.gas
        assertEquals("65000", gas.gasUnits.value, "Switching to an ERC-20 token should fill blank gas units even before a suggestion")
        assertEquals("", gas.baseFee.value, "Base fee stays blank until a suggestion arrives")
    }

    @Test
    fun should_not_prefill_custom_fees_when_suggestion_absent() = vmTest {
        runCurrent()

        val gas = viewModel.viewState.value.gas
        assertEquals("", gas.baseFee.value, "Base fee should stay blank when no suggestion is available")
        assertEquals("", gas.priorityFee.value, "Priority fee should stay blank when no suggestion is available")
    }

    @Test
    fun should_not_overwrite_typed_custom_fees_on_further_emissions() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnBaseFeeChanged("50"))

        transferInteractor.feeSuggestionToReturn = FeeSuggestion(Wei(BigInteger("40000000000")), Wei(BigInteger("3000000000")))
        advanceTimeBy(31.seconds)

        assertEquals(
            "50",
            viewModel.viewState.value.gas.baseFee.value,
            "A typed base fee should survive later suggestion emissions",
        )
    }

    @Test
    fun should_reprefill_custom_fees_after_clear_form_on_next_emission() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnClearFormClick)
        assertEquals("", viewModel.viewState.value.gas.baseFee.value, "Clear form should blank the custom fees")

        transferInteractor.feeSuggestionToReturn = FeeSuggestion(Wei(BigInteger("40000000000")), Wei(BigInteger("3000000000")))
        advanceTimeBy(31.seconds)

        val gas = viewModel.viewState.value.gas
        assertEquals("60", gas.baseFee.value, "The next suggestion emission should re-prefill the Default max base fee")
        assertEquals("3", gas.priorityFee.value, "The next suggestion emission should re-prefill the Default priority fee")
    }

    @Test
    fun should_compute_custom_fee_usd_for_ETH() = vmTest {
        seedFees()

        assertEquals(
            expectedFeeUsd(BigInteger("32000000000"), ETH_GAS_UNITS),
            viewModel.viewState.value.gas.customFeeUsd,
            "The prefilled custom fee estimate should match the Default preset fee on the 21000 ETH basis",
        )
    }

    @Test
    fun should_compute_custom_fee_usd_for_ERC20() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))

        assertEquals(
            expectedFeeUsd(BigInteger("32000000000"), ERC20_GAS_UNITS),
            viewModel.viewState.value.gas.customFeeUsd,
            "The prefilled custom fee estimate should match the Default preset fee on the 65000 ERC-20 basis",
        )
    }

    @Test
    fun should_show_zero_based_custom_estimate_when_base_fee_cleared() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnBaseFeeChanged(""))

        val gas = viewModel.viewState.value.gas
        assertEquals("", gas.baseFee.value, "A cleared base fee stays empty; 0 is never written into the input")
        assertEquals(
            expectedFeeUsd(BigInteger("2000000000"), ETH_GAS_UNITS),
            gas.customFeeUsd,
            "The estimate treats the blank base fee as 0, so USD reflects only the priority fee",
        )
        assertNotNull(
            gas.chips.first { it.option == GasOption.CustomFees }.feeUsd,
            "The custom chip keeps its USD line when a field is cleared",
        )
    }

    @Test
    fun should_keep_gate_strict_when_custom_base_fee_cleared() = vmTest {
        seedFees()
        input1Eth()
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        assertTrue(viewModel.viewState.value.isTransferEnabled, "Prefilled custom fees should enable Send")

        sendEvent(SendEvent.OnBaseFeeChanged(""))
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "The gate stays strict: a blank base fee disables Send even though the estimate still shows",
        )
    }

    @Test
    fun should_show_custom_chip_fee_usd_with_all_blank_custom_fields() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnBaseFeeChanged(""))
        sendEvent(SendEvent.OnPriorityFeeChanged(""))

        assertNotNull(
            viewModel.viewState.value.gas.chips.first { it.option == GasOption.CustomFees }.feeUsd,
            "The custom chip keeps a USD line with all fields blank while the rate is present",
        )
    }

    @Test
    fun should_reject_non_numeric_custom_base_fee() = vmTest {
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("abc"))

        assertEquals(
            "Invalid gas price format",
            viewModel.viewState.value.gas.baseFee.error,
            "Non-numeric base fee should be rejected",
        )
    }

    @Test
    fun should_reject_zero_custom_base_fee() = vmTest {
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("0"))

        assertEquals(
            "Base fee must be greater than zero",
            viewModel.viewState.value.gas.baseFee.error,
            "Zero base fee should be rejected",
        )
    }

    @Test
    fun should_reject_custom_base_fee_with_too_many_decimals() = vmTest {
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("1.1234567890"))

        assertEquals(
            "Too many decimal places for gwei",
            viewModel.viewState.value.gas.baseFee.error,
            "More than 9 decimal places should be rejected",
        )
    }

    @Test
    fun should_gate_transfer_on_custom_input_validity_for_ETH() = vmTest {
        input1Eth()
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be disabled while custom fees are blank",
        )

        sendEvent(SendEvent.OnBaseFeeChanged("-1"))
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be disabled while the custom base fee is invalid",
        )

        sendEvent(SendEvent.OnBaseFeeChanged("30"))
        sendEvent(SendEvent.OnPriorityFeeChanged("2"))
        assertTrue(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be enabled once valid custom fees are entered",
        )
    }

    @Test
    fun should_gate_transfer_on_custom_gas_units_for_ERC20() = vmTest {
        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))
        sendEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("10.5"))
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("30"))
        sendEvent(SendEvent.OnPriorityFeeChanged("2"))

        sendEvent(SendEvent.OnGasUnitsChanged(""))
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be disabled while the ERC-20 gas units are blank",
        )

        sendEvent(SendEvent.OnGasUnitsChanged("100"))
        assertFalse(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be disabled while the gas units are below the minimum",
        )

        sendEvent(SendEvent.OnGasUnitsChanged("65000"))
        assertTrue(
            viewModel.viewState.value.isTransferEnabled,
            "Transfer should be enabled once valid gas units are entered",
        )
    }

    @Test
    fun should_pass_selected_preset_fees_in_transfer_data() = vmTest {
        transferInteractor.transferResult = Result.success("0xTransactionHash")
        seedFees()

        input1Eth()
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.FastTransfer))
        clickSend()

        val captured = transferInteractor.lastTransferData
        assertNotNull(captured, "Transfer data should be captured")
        assertEquals(
            Wei(presetMaxFeeWei(GasOption.FastTransfer)),
            captured.maxFeePerGas,
            "Fast max fee should be signed",
        )
        assertEquals(
            Wei(priorityFeeWei.multiply(BigInteger.valueOf(2))),
            captured.maxPriorityFeePerGas,
            "Fast priority fee should be signed",
        )
        assertEquals(BigInteger.valueOf(ETH_GAS_UNITS), captured.gasUnits, "ETH transfer should use 21000 gas units")
        assertEquals(EthContractAddress, captured.contractAddress, "Contract address should be ETH")
    }

    @Test
    fun should_pass_custom_fees_in_transfer_data() = vmTest {
        transferInteractor.transferResult = Result.success("0xTransactionHash")

        input1Eth()
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("30"))
        sendEvent(SendEvent.OnPriorityFeeChanged("2"))
        clickSend()

        val captured = transferInteractor.lastTransferData
        assertNotNull(captured, "Transfer data should be captured")
        assertEquals(
            Wei(BigInteger("32000000000")),
            captured.maxFeePerGas,
            "Custom max fee should be base + priority in wei",
        )
        assertEquals(
            Wei(BigInteger("2000000000")),
            captured.maxPriorityFeePerGas,
            "Custom priority fee should be signed in wei",
        )
        assertEquals(
            BigInteger.valueOf(ETH_GAS_UNITS),
            captured.gasUnits,
            "ETH custom transfer should use 21000 gas units",
        )
    }

    @Test
    fun should_pass_custom_gas_units_for_erc20_in_transfer_data() = vmTest {
        transferInteractor.transferResult = Result.success("0xTransactionHash")

        sendEvent(SendEvent.OnTokenSelected(usdtOnMainnet.ethAddress))
        sendEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("10.5"))
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("30"))
        sendEvent(SendEvent.OnPriorityFeeChanged("2"))
        sendEvent(SendEvent.OnGasUnitsChanged("70000"))
        clickSend()

        val captured = transferInteractor.lastTransferData
        assertNotNull(captured, "Transfer data should be captured")
        assertEquals(
            BigInteger.valueOf(70_000),
            captured.gasUnits,
            "ERC-20 custom transfer should use the typed gas units",
        )
        assertEquals(usdtOnMainnet.ethAddress, captured.contractAddress, "Contract address should be USDT")
    }

    @Test
    fun should_refetch_fee_suggestion_after_polling_interval() = vmTest {
        transferInteractor.feeSuggestionToReturn = feeSuggestion
        runCurrent()
        assertEquals(
            1,
            transferInteractor.fetchFeeSuggestionCallCount,
            "The suggestion should be fetched once on start",
        )

        advanceTimeBy(31.seconds)
        assertEquals(
            2,
            transferInteractor.fetchFeeSuggestionCallCount,
            "The suggestion should be refetched after 30 seconds",
        )
    }

    @Test
    fun should_reset_gas_option_and_custom_fees_on_clear_form() = vmTest {
        seedFees()
        sendEvent(SendEvent.OnGasOptionSelected(GasOption.CustomFees))
        sendEvent(SendEvent.OnBaseFeeChanged("30"))
        sendEvent(SendEvent.OnClearFormClick)

        val gas = viewModel.viewState.value.gas
        assertEquals(
            GasOption.Default,
            gas.chips.first { it.selected }.option,
            "Clear form should reset to the Default option",
        )
        assertEquals("", gas.baseFee.value, "Clear form should clear the custom base fee")
        assertEquals("", gas.priorityFee.value, "Clear form should clear the custom priority fee")
    }

    @Test
    fun should_restore_user_input_from_saved_state_handle() = vmTest {
        val stored = UserInput(
            token = usdtOnMainnet.ethAddress.toString(),
            amount = "2.5",
            receiverAddress = validEthAddress,
            selectedGasOption = GasOption.FastTransfer,
            customFees = CustomFeesInput(baseFeeGwei = "30", priorityFeeGwei = "3", gasUnits = "70000"),
        )
        val handle = SavedStateHandle(mapOf(KEY_USER_INPUT to Json.encodeToString(stored)))
        val restored = newViewModel(handle)
        runCurrent()

        val state = restored.viewState.value
        assertEquals(usdtOnMainnet.ethAddress, state.selectedTokenContractAddress, "Selected token should be restored")
        assertEquals(validEthAddress, state.receiverAddress.value, "Receiver address should be restored")
        assertEquals("2.5", state.transferAmountInputState.value, "Amount should be restored")
        assertEquals(
            GasOption.FastTransfer,
            state.gas.chips.first { it.selected }.option,
            "Gas option should be restored",
        )
        assertEquals("30", state.gas.baseFee.value, "Custom base fee should be restored")
        assertEquals("70000", state.gas.gasUnits.value, "Custom gas units should be restored")
    }

    @Test
    fun should_fall_back_to_default_input_on_corrupt_saved_state() = vmTest {
        val handle = SavedStateHandle(mapOf(KEY_USER_INPUT to "not json"))
        val restored = newViewModel(handle)
        runCurrent()

        val state = restored.viewState.value
        assertEquals(EthContractAddress, state.selectedTokenContractAddress, "Corrupt state should fall back to ETH")
        assertEquals("", state.receiverAddress.value, "Corrupt state should fall back to a blank receiver")
        assertEquals(
            GasOption.Default,
            state.gas.chips.first { it.selected }.option,
            "Corrupt state should fall back to Default",
        )
    }

    @Test
    fun should_persist_user_input_through_saved_state_handle() = vmTest {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.onEvent(SendEvent.OnTransferAmountChanged("1.5"))
        vm.onEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        runCurrent()

        val json = handle.get<String>(KEY_USER_INPUT)
        assertNotNull(json, "The user input should be persisted as JSON in the SavedStateHandle")
        val decoded = Json.decodeFromString<UserInput>(json)
        assertEquals("1.5", decoded.amount, "Amount should be persisted")
        assertEquals(validEthAddress, decoded.receiverAddress, "Receiver address should be persisted")
    }

    private fun presetMaxFeeWei(option: GasOption): BigInteger = when (option) {
        GasOption.LowFees -> {
            baseFeeWei.multiply(BigInteger.valueOf(11)).divide(BigInteger.TEN)
                .add(priorityFeeWei.multiply(BigInteger.valueOf(8)).divide(BigInteger.TEN))
        }
        GasOption.Default -> {
            baseFeeWei.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(2)).add(priorityFeeWei)
        }
        GasOption.FastTransfer -> {
            baseFeeWei.multiply(BigInteger.valueOf(2)).add(priorityFeeWei.multiply(BigInteger.valueOf(2)))
        }
        GasOption.CustomFees -> {
            error("Custom option has no preset fee")
        }
    }

    private fun expectedFeeUsd(maxFeeWei: BigInteger, gasUnits: Long): String {
        val totalWei = maxFeeWei.multiply(BigInteger.valueOf(gasUnits))
        val feeEth = Convert.fromWei(totalWei.toString(), Convert.Unit.ETHER)
        return formatUsdCurrency(feeEth.multiply(ethRate))
    }

    private fun TestScope.seedFees() {
        transferInteractor.feeSuggestionToReturn = feeSuggestion
        fiatRateProvider.rates.value = mapOf(EthContractAddress to ethRate)
        runCurrent()
    }

    private fun TestScope.input1Eth() {
        sendEvent(SendEvent.OnReceiverAddressChanged(validEthAddress))
        sendEvent(SendEvent.OnTransferAmountChanged("1.0"))
    }

    private fun TestScope.collectViewStates() {
        viewStates.clear()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewState.collect { viewStates.add(it) }
        }
    }

    private fun TestScope.collectNews() {
        collectedNews.clear()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.news.collect { collectedNews.add(it) }
        }
    }

    private fun TestScope.clickSend() = sendEvent(SendEvent.OnSendClick)

    private fun TestScope.sendEvent(event: SendEvent) {
        viewModel.onEvent(event)
        runCurrent()
    }
}

private const val ETH_GAS_UNITS = 21_000L
private const val ERC20_GAS_UNITS = 65_000L
