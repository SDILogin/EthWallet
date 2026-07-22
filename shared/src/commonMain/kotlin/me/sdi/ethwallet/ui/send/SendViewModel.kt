package me.sdi.ethwallet.ui.send

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.transfer.ETH_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.core.transfer.Gwei
import me.sdi.ethwallet.core.transfer.TransferData
import me.sdi.ethwallet.core.transfer.TransferException
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.core.transfer.toEth
import me.sdi.ethwallet.core.transfer.toGwei
import me.sdi.ethwallet.core.transfer.toUsd
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.core.transfer.wei
import me.sdi.ethwallet.ui.findKnownToken
import me.sdi.ethwallet.util.formatUsdCurrency
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SendViewModel"

private const val ETH_ADDRESS_LENGTH = 42

private const val AMOUNT_MAX_LENGTH = 50

private const val GWEI_MAX_DECIMALS = 9

private val GAS_UNITS_MAX: BigInteger = BigInteger.valueOf(1_000_000)

internal const val KEY_USER_INPUT = "user_input"
private const val KEY_BASE_FEE = "base_fee_wei"
private const val KEY_PRIORITY_FEE = "priority_fee_wei"
private const val KEY_ETH_USD_RATE = "eth_usd_rate"

class SendViewModel(
    private val transferInteractor: TransferInteractor,
    private val fiatRateProvider: FiatRateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val userInput = MutableStateFlow(restoreUserInput())

    private val isTransferring = MutableStateFlow(false)

    private val feeSuggestion: StateFlow<FeeSuggestion?> = flow {
        val cachedBaseFee = savedStateHandle.get<String>(KEY_BASE_FEE)
        val cachedPriorityFee = savedStateHandle.get<String>(KEY_PRIORITY_FEE)
        if (cachedBaseFee != null && cachedPriorityFee != null) {
            emit(FeeSuggestion(cachedBaseFee.wei, cachedPriorityFee.wei))
        }
        while (true) {
            val fresh = transferInteractor.fetchFeeSuggestion()
            savedStateHandle[KEY_BASE_FEE] = fresh.baseFee.value.toString()
            savedStateHandle[KEY_PRIORITY_FEE] = fresh.priorityFee.value.toString()
            emit(fresh)
            delay(30.seconds)
        }
    }
        .retry { error ->
            Logger.warn(TAG) { "Failed to fetch fee suggestion, retrying: ${error.message}" }
            delay(5.seconds)
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private val ethUsdRate: StateFlow<BigDecimal?> = flow {
        savedStateHandle.get<String>(KEY_ETH_USD_RATE)?.let { emit(it.toBigDecimal()) }
        while (true) {
            fiatRateProvider.forceUpdate()
            val fresh = fiatRateProvider.observe().first().getValue(EthContractAddress)
            savedStateHandle[KEY_ETH_USD_RATE] = fresh.toPlainString()
            emit(fresh)
            delay(30.seconds)
        }
    }
        .retry { error ->
            Logger.warn(TAG) { "Failed to fetch ETH/USD rate, retrying: ${error.message}" }
            delay(5.seconds)
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private val newsChannel = Channel<SendNews>(Channel.BUFFERED)

    val news: Flow<SendNews> = newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    val viewState: StateFlow<SendViewState> = combine(
        userInput,
        feeSuggestion,
        ethUsdRate,
        isTransferring,
    ) { input, suggestion, rate, transferring ->
        val selectedToken = EthAddress(input.token)
        SendViewState(
            selectedTokenContractAddress = selectedToken,
            receiverAddress = TextInputState(
                value = input.receiverAddress,
                error = validateAddress(input.receiverAddress),
            ),
            transferAmountInputState = TextInputState(
                value = input.amount,
                error = validateAmount(input.amount, selectedToken),
            ),
            transferring = transferring,
            gas = buildGasViewState(input, selectedToken, suggestion, rate),
        )
    }
        .onEach { Logger.debug(TAG) { "ViewState: $it" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SendViewState())

    init {
        userInput
            .onEach { savedStateHandle[KEY_USER_INPUT] = Json.encodeToString(it) }
            .launchIn(viewModelScope)
        feeSuggestion
            .filterNotNull()
            .onEach(::prefillCustomFees)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SendEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is SendEvent.OnTokenSelected -> {
                userInput.update { current ->
                    val newToken = event.contractAddress
                    val gasUnits = if (current.customFees.gasUnits.isBlank() && newToken != EthContractAddress) {
                        newToken.gasUnits.toString()
                    } else {
                        current.customFees.gasUnits
                    }
                    current.copy(
                        token = newToken.toString(),
                        customFees = current.customFees.copy(gasUnits = gasUnits),
                    )
                }
            }
            is SendEvent.OnReceiverAddressChanged -> {
                userInput.update { it.copy(receiverAddress = event.address.trim().take(ETH_ADDRESS_LENGTH)) }
            }
            is SendEvent.OnTransferAmountChanged -> {
                userInput.update { it.copy(amount = truncateNumericInput(event.amount)) }
            }
            is SendEvent.OnGasOptionSelected -> {
                userInput.update { it.copy(selectedGasOption = event.option) }
            }
            is SendEvent.OnBaseFeeChanged -> {
                userInput.update { it.copy(customFees = it.customFees.copy(baseFeeGwei = truncateNumericInput(event.value))) }
            }
            is SendEvent.OnPriorityFeeChanged -> {
                userInput.update { it.copy(customFees = it.customFees.copy(priorityFeeGwei = truncateNumericInput(event.value))) }
            }
            is SendEvent.OnGasUnitsChanged -> {
                userInput.update { it.copy(customFees = it.customFees.copy(gasUnits = truncateNumericInput(event.value))) }
            }
            SendEvent.OnSendClick -> {
                handleTransferRequested()
            }
            SendEvent.OnClearFormClick -> {
                userInput.value = UserInput()
            }
        }
    }

    private fun prefillCustomFees(suggestion: FeeSuggestion) {
        userInput.update { current ->
            if (!current.customFees.isBlank()) return@update current
            val token = EthAddress(current.token)
            val default = suggestion.preset(GasOption.Default, token.gasUnits)
            val maxBaseFee = Wei(default.maxFee.value.subtract(default.priorityFeeWei.value))
            current.copy(
                customFees = CustomFeesInput(
                    baseFeeGwei = maxBaseFee.toGweiInput(),
                    priorityFeeGwei = default.priorityFeeWei.toGweiInput(),
                    gasUnits = if (token == EthContractAddress) "" else token.gasUnits.toString(),
                ),
            )
        }
    }

    private fun handleTransferRequested() {
        if (!viewState.value.isTransferEnabled) return

        isTransferring.value = true

        viewModelScope.launch {
            try {
                val transferData = viewState.value.createTransferData()
                val transactionHash = transferInteractor.transfer(transferData)

                Logger.debug(TAG) { "Transfer successful: $transactionHash" }
                newsChannel.trySend(SendNews.TransferSuccess(transactionHash))

                userInput.value = UserInput()
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransferException) {
                Logger.warn(TAG) { "Transfer failed: ${e.message}" }
                newsChannel.trySend(SendNews.TransferError(e.asString()))
            } catch (e: Exception) {
                Logger.warn(TAG) { "Unexpected error during transfer: ${e.message}" }
                newsChannel.trySend(SendNews.TransferError("An unexpected error occurred. Please try again."))
            } finally {
                isTransferring.value = false
            }
        }
    }

    private fun restoreUserInput(): UserInput = runCatching {
        val json = savedStateHandle.get<String>(KEY_USER_INPUT) ?: return@runCatching UserInput()
        Json.decodeFromString<UserInput>(json)
    }.getOrDefault(UserInput())
}

private data class ResolvedFee(
    val maxFee: Wei,
    val priorityFeeWei: Wei,
    val gasUnits: BigInteger,
)

private fun buildGasViewState(
    input: UserInput,
    selectedToken: EthAddress,
    suggestion: FeeSuggestion?,
    ethUsdRate: BigDecimal?,
): GasViewState {
    val presetGasUnits = selectedToken.gasUnits
    val gasUnitsEditable = selectedToken != EthContractAddress

    val custom = input.customFees
    val customFee = resolveCustomFee(custom, gasUnitsEditable, selectedToken)
    val customFeeUsd = customEstimateUsd(custom, gasUnitsEditable, selectedToken, ethUsdRate)

    val chips = GasOption.entries.map { option ->
        GasChip(
            option = option,
            labelRes = option.labelRes(),
            feeUsd = if (option == GasOption.CustomFees) {
                customFeeUsd
            } else {
                suggestion?.preset(option, presetGasUnits)?.let { fee ->
                    if (ethUsdRate != null) feeUsd(fee.maxFee, fee.gasUnits, ethUsdRate) else null
                }
            },
            selected = input.selectedGasOption == option,
            enabled = if (option == GasOption.CustomFees) true else suggestion != null,
        )
    }

    val resolved = if (input.selectedGasOption == GasOption.CustomFees) {
        customFee
    } else {
        suggestion?.preset(input.selectedGasOption, presetGasUnits)
    }

    return GasViewState(
        chips = chips,
        baseFee = TextInputState(custom.baseFeeGwei, validateBaseFeeGwei(custom.baseFeeGwei)),
        priorityFee = TextInputState(custom.priorityFeeGwei, validatePriorityFeeGwei(custom.priorityFeeGwei)),
        gasUnits = TextInputState(custom.gasUnits, if (gasUnitsEditable) validateGasUnits(custom.gasUnits) else null),
        gasUnitsEditable = gasUnitsEditable,
        customFeeUsd = customFeeUsd,
        maxFee = resolved?.maxFee,
        priorityFeeWei = resolved?.priorityFeeWei,
        gasUnitsValue = resolved?.gasUnits,
    )
}

private fun resolveCustomFee(
    custom: CustomFeesInput,
    gasUnitsEditable: Boolean,
    selectedToken: EthAddress,
): ResolvedFee? {
    val baseFeeGwei = parseBaseFeeGwei(custom.baseFeeGwei) ?: return null
    val priorityFeeGwei = parsePriorityFeeGwei(custom.priorityFeeGwei) ?: return null
    val gasUnits = if (gasUnitsEditable) parseGasUnits(custom.gasUnits) ?: return null else selectedToken.gasUnits
    return ResolvedFee(
        maxFee = Gwei(baseFeeGwei.add(priorityFeeGwei)).toWei(),
        priorityFeeWei = Gwei(priorityFeeGwei).toWei(),
        gasUnits = gasUnits,
    )
}

/**
 * Lenient worst-case estimate shown in the UI: a blank or unparseable field counts as 0 so the
 * displayed USD never disappears while editing. Validation and the Send gate stay strict.
 */
private fun customEstimateUsd(
    custom: CustomFeesInput,
    gasUnitsEditable: Boolean,
    selectedToken: EthAddress,
    ethUsdRate: BigDecimal?,
): String? {
    if (ethUsdRate == null) return null
    val baseFeeGwei = custom.baseFeeGwei.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val priorityFeeGwei = custom.priorityFeeGwei.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasUnits = if (gasUnitsEditable) custom.gasUnits.toBigIntegerOrNull() ?: BigInteger.ZERO else selectedToken.gasUnits
    return feeUsd(Gwei(baseFeeGwei.add(priorityFeeGwei)).toWei(), gasUnits, ethUsdRate)
}

private fun FeeSuggestion.preset(option: GasOption, gasUnits: BigInteger): ResolvedFee {
    val (maxBaseFeeRatio, priorityRatio) = when (option) {
        GasOption.LowFees -> (11 to 10) to (8 to 10)
        GasOption.Default -> (3 to 2) to (1 to 1)
        GasOption.FastTransfer -> (2 to 1) to (2 to 1)
        GasOption.CustomFees -> error("Custom option has no preset fee")
    }
    val maxBaseFee = baseFee.value.scaleBy(maxBaseFeeRatio)
    val optionPriorityFee = priorityFee.value.scaleBy(priorityRatio)
    return ResolvedFee(
        maxFee = Wei(maxBaseFee.add(optionPriorityFee)),
        priorityFeeWei = Wei(optionPriorityFee),
        gasUnits = gasUnits,
    )
}

private fun BigInteger.scaleBy(ratio: Pair<Int, Int>): BigInteger =
    multiply(ratio.first.toBigInteger()).divide(ratio.second.toBigInteger())

private fun feeUsd(maxFeePerGasUnit: Wei, gasUnits: BigInteger, ethUsdRate: BigDecimal): String {
    val totalFee = Wei(maxFeePerGasUnit.value.multiply(gasUnits))
    return formatUsdCurrency(totalFee.toEth().toUsd(ethUsdRate))
}

private fun Wei.toGweiInput(): String {
    val gwei = toGwei().value.setScale(2, RoundingMode.HALF_UP)
    return if (gwei.signum() == 0) "0" else gwei.stripTrailingZeros().toPlainString()
}

private fun CustomFeesInput.isBlank(): Boolean =
    baseFeeGwei.isBlank() && priorityFeeGwei.isBlank() && gasUnits.isBlank()

private fun parseBaseFeeGwei(value: String): BigDecimal? =
    value.toBigDecimalOrNull()?.takeIf { validateBaseFeeGwei(value) == null }

private fun parsePriorityFeeGwei(value: String): BigDecimal? =
    value.toBigDecimalOrNull()?.takeIf { validatePriorityFeeGwei(value) == null }

private fun parseGasUnits(value: String): BigInteger? =
    value.toBigIntegerOrNull()?.takeIf { validateGasUnits(value) == null }

private fun SendViewState.createTransferData(): TransferData {
    val amount = amountToBigInteger(transferAmountInputState.value, selectedTokenContractAddress)
    checkNotNull(amount) {
        "Invalid amount conversion"
    }

    return TransferData(
        destinationAddress = EthAddress(receiverAddress.value),
        amount = amount,
        contractAddress = selectedTokenContractAddress,
        maxFeePerGas = checkNotNull(gas.maxFee) { "Max fee must be resolved when the transfer gate passed" },
        maxPriorityFeePerGas = checkNotNull(gas.priorityFeeWei) { "Priority fee must be resolved when the transfer gate passed" },
        gasUnits = checkNotNull(gas.gasUnitsValue) { "Gas units must be resolved when the transfer gate passed" },
    )
}

private fun TransferException.asString(): String = when (this) {
    is TransferException.InsufficientBalance -> "Insufficient balance. $message"
    is TransferException.InvalidAddress -> "Invalid recipient address. Please check and try again."
    is TransferException.InvalidAmount -> "Invalid transfer amount. $message"
    is TransferException.NetworkError -> "Network error occurred. Please check your connection and try again."
    is TransferException.TransactionFailed -> "Transaction failed. $message"
    is TransferException.GasPriceError -> "Gas price error. $message"
}

private fun validateAddress(address: String): String? {
    return when {
        address.isBlank() -> null
        !EthAddress.isValidFormat(address) -> "Invalid address format"
        else -> null
    }
}

private fun validateAmount(amount: String, tokenAddress: EthAddress): String? {
    val bigDecimalAmount = amount.toBigDecimalOrNull()
    return when {
        amount.isBlank() -> null
        bigDecimalAmount == null -> "Invalid amount format"
        bigDecimalAmount <= BigDecimal.ZERO -> "Amount must be greater than zero"
        bigDecimalAmount.scale() > getMaxDecimalPlaces(tokenAddress) -> "Too many decimal places for selected token"
        else -> null
    }
}

private fun validateBaseFeeGwei(value: String): String? {
    val parsed = value.toBigDecimalOrNull()
    return when {
        value.isBlank() -> null
        parsed == null -> "Invalid gas price format"
        parsed <= BigDecimal.ZERO -> "Base fee must be greater than zero"
        parsed.scale() > GWEI_MAX_DECIMALS -> "Too many decimal places for gwei"
        else -> null
    }
}

private fun validatePriorityFeeGwei(value: String): String? {
    val parsed = value.toBigDecimalOrNull()
    return when {
        value.isBlank() -> null
        parsed == null -> "Invalid gas price format"
        parsed < BigDecimal.ZERO -> "Priority fee must not be negative"
        parsed.scale() > GWEI_MAX_DECIMALS -> "Too many decimal places for gwei"
        else -> null
    }
}

private fun validateGasUnits(value: String): String? {
    val parsed = value.toBigIntegerOrNull()
    return when {
        value.isBlank() -> null
        parsed == null -> "Invalid gas units format"
        parsed < ETH_TRANSFER_GAS_UNITS -> "Gas units must be at least $ETH_TRANSFER_GAS_UNITS"
        parsed > GAS_UNITS_MAX -> "Gas units must not exceed $GAS_UNITS_MAX"
        else -> null
    }
}

private fun truncateNumericInput(input: String): String {
    val trimmed = input.trim().take(AMOUNT_MAX_LENGTH)
    return if (trimmed.length == AMOUNT_MAX_LENGTH) {
        trimmed.dropLastWhile { char -> !char.isDigit() }
    } else {
        trimmed
    }
}

private fun getMaxDecimalPlaces(tokenAddress: EthAddress): Int {
    return findKnownToken(tokenAddress)?.decimals ?: 18
}

private fun amountToBigInteger(amount: String, tokenAddress: EthAddress): BigInteger? {
    val bigDecimalAmount = amount.toBigDecimalOrNull() ?: return null
    val decimals = getMaxDecimalPlaces(tokenAddress)
    val multiplier = BigDecimal.TEN.pow(decimals)
    return bigDecimalAmount.multiply(multiplier).toBigInteger()
}
