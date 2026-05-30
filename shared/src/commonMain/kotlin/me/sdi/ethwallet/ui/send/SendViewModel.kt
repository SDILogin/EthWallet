package me.sdi.ethwallet.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.TransferData
import me.sdi.ethwallet.core.transfer.TransferException
import me.sdi.ethwallet.core.transfer.TransferInteractor
import me.sdi.ethwallet.ui.KnownTokens
import java.math.BigDecimal
import java.math.BigInteger

private const val TAG = "SendViewModel"

private const val ETH_ADDRESS_LENGTH = 42

// reasonable maximum length for an amount input
private const val AMOUNT_MAX_LENGTH = 50

class SendViewModel(
    private val transferInteractor: TransferInteractor,
) : ViewModel() {
    private val _viewState = MutableStateFlow(SendViewState())
    val viewState = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<SendNews>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val news = _news.asSharedFlow()

    fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.OnTokenSelected -> handleTokenSelected(event.contractAddress)
            is SendEvent.OnReceiverAddressChanged -> handleReceiverAddressChanged(event.address)
            is SendEvent.OnAmountChanged -> handleAmountChanged(event.amount)
            SendEvent.OnTransferRequested -> handleTransferRequested()
            SendEvent.OnClearForm -> handleClearForm()
        }
    }

    private fun handleTokenSelected(contractAddress: EthAddress) {
        Logger.debug(TAG) {
            val tokenName = when (contractAddress) {
                EthContractAddress -> "Eth"
                else -> KnownTokens.entries.find { it.ethAddress == contractAddress }?.tokenName ?: "Unknown Token"
            }
            """
            Token selected: $tokenName
            """.trimIndent()
        }

        _viewState.update { currentState ->
            currentState.copy(
                selectedTokenContractAddress = contractAddress,
                // Re-validate amount with new token selection
                amount = currentState.amount.copy(
                    error = validateAmount(currentState.amount.value, contractAddress),
                ),
            )
        }
    }

    private fun handleReceiverAddressChanged(rawReceiverWalletAddress: String) {
        Logger.debug(TAG) { "Receiver address changed: $rawReceiverWalletAddress" }

        val trimmedAddress = rawReceiverWalletAddress.trim().take(ETH_ADDRESS_LENGTH)
        val error: String? = validateAddress(trimmedAddress)

        _viewState.update { currentState ->
            currentState.copy(
                receiverAddress = AddressInputState(
                    value = trimmedAddress,
                    error = error,
                ),
            )
        }
    }

    private fun handleAmountChanged(amount: String) {
        Logger.debug(TAG) { "Amount changed: $amount" }

        val trimmedAmount = amount.trim().take(AMOUNT_MAX_LENGTH)
        val formattedAmount = if (trimmedAmount.length == AMOUNT_MAX_LENGTH) {
            // maybe the last character is a dot
            trimmedAmount.dropLastWhile { char -> !char.isDigit() }
        } else {
            trimmedAmount
        }

        val currentToken = _viewState.value.selectedTokenContractAddress
        val error = validateAmount(formattedAmount, currentToken)

        _viewState.update { currentState ->
            currentState.copy(
                amount = AmountInputState(
                    value = formattedAmount,
                    error = error,
                ),
            )
        }
    }

    private fun handleTransferRequested() {
        Logger.debug(TAG) { "Transfer requested" }

        val currentState = _viewState.value

        if (!currentState.isTransferEnabled) {
            Logger.warn(TAG) { "Transfer requested but form is not valid" }
            _news.tryEmit(SendNews.ValidationError("Please fix all validation errors before proceeding"))
            return
        }

        // Final validation
        val receiverAddressValidation = validateAddress(currentState.receiverAddress.value)
        val amountValidation = validateAmount(currentState.amount.value, currentState.selectedTokenContractAddress)

        if (receiverAddressValidation != null || amountValidation != null) {
            _news.tryEmit(SendNews.ValidationError("Please fix all validation errors before proceeding"))
            return
        }

        _viewState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val transferData = createTransferData(currentState)
                val transactionHash = transferInteractor.transfer(transferData)

                Logger.debug(TAG) { "Transfer successful: $transactionHash" }
                _news.tryEmit(SendNews.TransferSuccess(transactionHash))

                // Clear form after successful transfer
                _viewState.update { SendViewState() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransferException) {
                Logger.warn(TAG) { "Transfer failed: ${e.message}" }
                val userFriendlyMessage = mapTransferExceptionToUserMessage(e)
                _news.tryEmit(SendNews.TransferError(userFriendlyMessage))
            } catch (e: Exception) {
                Logger.warn(TAG) { "Unexpected error during transfer: ${e.message}" }
                _news.tryEmit(SendNews.TransferError("An unexpected error occurred. Please try again."))
            } finally {
                _viewState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun handleClearForm() {
        Logger.debug(TAG) { "Clear form requested" }
        _viewState.update { SendViewState() }
    }

    private fun createTransferData(state: SendViewState): TransferData {
        val destinationAddress = EthAddress(state.receiverAddress.value)
        val amount = amountToBigInteger(
            amount = state.amount.value,
            tokenAddress = state.selectedTokenContractAddress,
        )
        checkNotNull(amount) {
            "Invalid amount conversion"
        }

        return TransferData(
            destinationAddress = destinationAddress,
            amount = amount,
            contractAddress = state.selectedTokenContractAddress,
            gasParams = state.gasParams,
        )
    }

    private fun mapTransferExceptionToUserMessage(exception: TransferException): String {
        return when (exception) {
            is TransferException.InsufficientBalance -> {
                "Insufficient balance. ${exception.message}"
            }
            is TransferException.InvalidAddress -> {
                "Invalid recipient address. Please check and try again."
            }
            is TransferException.InvalidAmount -> {
                "Invalid transfer amount. ${exception.message}"
            }
            is TransferException.NetworkError -> {
                "Network error occurred. Please check your connection and try again."
            }
            is TransferException.TransactionFailed -> {
                "Transaction failed. ${exception.message}"
            }
            is TransferException.GasPriceError -> {
                "Gas price error. ${exception.message}"
            }
        }
    }
}

private fun validateAddress(address: String): String? {
    return when {
        address.isBlank() -> null
        !address.startsWith("0x") -> "Address must start with 0x"
        address.length != ETH_ADDRESS_LENGTH -> "Address must be exactly 42 characters"
        !address.matches(Regex("^0x[0-9a-fA-F]{40}$")) -> "Invalid address format"
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

private fun getMaxDecimalPlaces(tokenAddress: EthAddress): Int {
    return KnownTokens.entries.find { it.ethAddress == tokenAddress }?.decimals ?: 18
}

private fun amountToBigInteger(amount: String, tokenAddress: EthAddress): BigInteger? {
    val bigDecimalAmount = amount.toBigDecimalOrNull() ?: return null
    val decimals = getMaxDecimalPlaces(tokenAddress)
    val multiplier = BigDecimal.TEN.pow(decimals)
    return bigDecimalAmount.multiply(multiplier).toBigInteger()
}
