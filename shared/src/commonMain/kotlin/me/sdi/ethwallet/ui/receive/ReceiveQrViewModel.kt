package me.sdi.ethwallet.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.ui.findKnownToken
import me.sdi.ethwallet.utils.ReceiveUtils
import java.math.BigDecimal

private const val TAG = "ReceiveQrViewModel"

// reasonable maximum length for an amount input
private const val AMOUNT_MAX_LENGTH = 50

class ReceiveQrViewModel(
    walletRepository: WalletRepository,
) : ViewModel() {
    val viewState: StateFlow<ReceiveQrViewState>
        field = MutableStateFlow(ReceiveQrViewState())

    private val activeWallet = walletRepository.observeActiveWallet()

    private val receiverAddress: Flow<EthAddress> = activeWallet.filterNotNull().map { it.address }

    private val selectedTokenContractAddress: Flow<EthAddress> = viewState
        .map { it.selectedTokenContract }
        .distinctUntilChanged()
        .onEach { Logger.debug(TAG) { "(flow) Selected token contract: $it" } }

    private val amount: Flow<BigDecimal?> = viewState
        .map { state -> state.amount.toBigDecimalOrNull()?.takeIf { state.amountError == null } }
        .distinctUntilChanged()
        .onEach { Logger.debug(TAG) { "(flow) Amount changed: $it" } }

    private val qrCodePayload = combine(
        receiverAddress,
        selectedTokenContractAddress,
        amount.onStart { emit(null) },
    ) { currentWalletAddress: EthAddress, selectedTokenContractAddress: EthAddress, amount: BigDecimal? ->
        ReceiveUtils.generateReceiveQrPayload(
            receiverEthAddress = currentWalletAddress,
            contractAddress = selectedTokenContractAddress,
            amount = amount,
        )
    }.distinctUntilChanged().onEach {
        Logger.debug(TAG) { "(flow) QR code payload: $it" }
    }

    val news: SharedFlow<ReceiveQrNews>
        field = MutableSharedFlow<ReceiveQrNews>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        news
            .onEach { Logger.debug(TAG) { "News: $it" } }
            .launchIn(viewModelScope)

        qrCodePayload
            .onEach { qrPayload -> viewState.update { vs -> vs.copy(qrPayload = qrPayload) } }
            .catch { e -> Logger.warn(TAG) { "Failed to generate QR code: ${e.message}" } }
            .launchIn(viewModelScope)

        receiverAddress
            .onEach { address -> viewState.update { vs -> vs.copy(walletAddress = address) } }
            .catch { e -> Logger.warn(TAG) { "Failed to load wallet address: ${e.message}" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ReceiveQrEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is ReceiveQrEvent.OnTokenSelected -> {
                handleTokenSelected(event.tokenContractAddress)
            }
            is ReceiveQrEvent.OnAmountChanged -> {
                handleAmountChanged(event.amount)
            }
            ReceiveQrEvent.OnClearAmount -> {
                handleClearAmount()
            }
            ReceiveQrEvent.OnCopyAddress -> {
                handleCopyAddress()
            }
            ReceiveQrEvent.OnBackPressed -> {
                // Back navigation is handled in the UI layer
            }
        }
    }

    private fun handleTokenSelected(contractAddress: EthAddress) {
        Logger.debug(TAG) {
            val selectedTokenName = when (contractAddress) {
                EthContractAddress -> "Eth"
                else -> findKnownToken(contractAddress)?.symbol ?: "Unknown"
            }

            "Selected $selectedTokenName"
        }

        viewState.update { it.copy(selectedTokenContract = contractAddress) }
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

        viewState.update { vs ->
            vs.copy(amount = formattedAmount)
        }
    }

    private fun handleClearAmount() {
        Logger.debug(TAG) { "Clear amount requested" }

        viewState.update { it.copy(amount = "") }
    }

    private fun handleCopyAddress() {
        Logger.debug(TAG) { "Copy address requested" }

        viewModelScope.launch {
            try {
                val address = activeWallet.first()?.address?.toString()
                if (address == null) {
                    news.emit(ReceiveQrNews.ShowToast("Address not available"))
                    return@launch
                }
                news.emit(ReceiveQrNews.CopyToClipboard(address))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG) { "Failed to copy address: ${e.message}" }
                news.emit(ReceiveQrNews.ShowToast("Failed to copy address"))
            }
        }
    }
}
