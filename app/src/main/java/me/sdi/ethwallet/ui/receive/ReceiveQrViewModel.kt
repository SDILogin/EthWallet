package me.sdi.ethwallet.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.utils.ReceiveUtils
import java.math.BigDecimal

private const val TAG = "ReceiveQrViewModel"

class ReceiveQrViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    private val _viewState = MutableStateFlow(ReceiveQrViewState())
    val viewState = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<ReceiveQrNews>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val news = _news.asSharedFlow()

    init {
        loadWalletAddress()
    }

    fun onEvent(event: ReceiveQrEvent) {
        when (event) {
            is ReceiveQrEvent.OnTokenSelected -> handleTokenSelected(event.token)
            is ReceiveQrEvent.OnAmountChanged -> handleAmountChanged(event.amount)
            ReceiveQrEvent.OnGenerateQrCode -> handleGenerateQrCode()
            ReceiveQrEvent.OnClearAmount -> handleClearAmount()
            ReceiveQrEvent.OnCopyAddress -> handleCopyAddress()
        }
    }

    private fun loadWalletAddress() {
        viewModelScope.launch {
            try {
                _viewState.update { it.copy(isLoading = true, error = null) }

                val activeWallet = walletRepository.getActiveWallet()
                if (activeWallet == null) {
                    _viewState.update {
                        it.copy(
                            isLoading = false,
                            error = "No active wallet found",
                        )
                    }
                    return@launch
                }

                _viewState.update {
                    it.copy(
                        receiverAddress = activeWallet.address,
                        isLoading = false,
                        error = null,
                    )
                }

                Logger.debug(TAG) { "Loaded wallet address: ${activeWallet.address}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG) { "Failed to load wallet address: ${e.message}" }
                _viewState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load wallet address",
                    )
                }
            }
        }
    }

    private fun handleTokenSelected(token: SelectedToken) {
        Logger.debug(TAG) { "Token selected: $token" }

        _viewState.update {
            it.copy(
                selectedToken = token,
                qrPayload = null,
                amountError = null,
            )
        }
    }

    private fun handleAmountChanged(amount: String) {
        Logger.debug(TAG) { "Amount changed: $amount" }

        val cleanAmount = amount.trim()
        _viewState.update {
            it.copy(
                amount = cleanAmount,
                amountError = null,
                qrPayload = null,
            )
        }

        if (cleanAmount.isNotEmpty()) {
            validateAmount(cleanAmount)
        }
    }

    private fun handleGenerateQrCode() {
        Logger.debug(TAG) { "Generate QR code requested" }

        val currentState = _viewState.value
        val address = currentState.receiverAddress

        if (address == null) {
            _viewState.update { it.copy(error = "Wallet address not available") }
            return
        }

        val amount = if (currentState.amount.isNotEmpty()) {
            try {
                BigDecimal(currentState.amount)
            } catch (e: Exception) {
                Logger.warn(TAG) { "Invalid amount format: ${currentState.amount}. ${e.stackTraceToString()}" }

                _viewState.update { it.copy(amountError = "Invalid amount format") }
                return
            }
        } else {
            null
        }

        if (amount != null && !isValidAmount(amount)) {
            return
        }

        generateQrCode(address, currentState.selectedToken, amount)
    }

    private fun handleClearAmount() {
        Logger.debug(TAG) { "Clear amount requested" }

        _viewState.update {
            it.copy(
                amount = "",
                amountError = null,
                qrPayload = null,
            )
        }
    }

    private fun handleCopyAddress() {
        Logger.debug(TAG) { "Copy address requested" }

        val address = _viewState.value.receiverAddress
        if (address != null) {
            _news.tryEmit(ReceiveQrNews.CopyToClipboard(address.toString()))
            _news.tryEmit(ReceiveQrNews.ShowToast("Address copied to clipboard"))
        } else {
            _news.tryEmit(ReceiveQrNews.ShowToast("Address not available"))
        }
    }

    private fun validateAmount(amountString: String) {
        try {
            val amount = BigDecimal(amountString)
            isValidAmount(amount)
        } catch (e: Exception) {
            Logger.warn(TAG) { "Invalid amount format: $amountString. ${e.stackTraceToString()}" }

            _viewState.update { it.copy(amountError = "Invalid number format") }
        }
    }

    private fun isValidAmount(amount: BigDecimal): Boolean {
        return when {
            amount <= BigDecimal.ZERO -> {
                _viewState.update { it.copy(amountError = "Amount must be greater than zero") }
                false
            }
            amount.scale() > getMaxDecimalPlaces() -> {
                _viewState.update {
                    it.copy(amountError = "Too many decimal places for selected token")
                }
                false
            }
            else -> {
                _viewState.update { it.copy(amountError = null) }
                true
            }
        }
    }

    private fun getMaxDecimalPlaces(): Int {
        return when (val token = _viewState.value.selectedToken) {
            SelectedToken.ETH -> 18
            is SelectedToken.ERC20 -> token.token.decimals
        }
    }

    private fun generateQrCode(
        receiverAddress: EthAddress,
        selectedToken: SelectedToken,
        amount: BigDecimal?,
    ) {
        viewModelScope.launch {
            try {
                val contractAddress = when (selectedToken) {
                    SelectedToken.ETH -> EthContractAddress
                    is SelectedToken.ERC20 -> selectedToken.token.ethAddress
                }

                val qrPayload = ReceiveUtils.generateReceiveQrPayload(
                    receiverEthAddress = receiverAddress,
                    contractAddress = contractAddress,
                    amount = amount,
                )

                _viewState.update { it.copy(qrPayload = qrPayload) }

                Logger.debug(TAG) { "Generated QR code URI: $qrPayload" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG) { "Failed to generate QR code: ${e.message}" }
                _viewState.update { it.copy(error = "Failed to generate QR code") }
            }
        }
    }
}
