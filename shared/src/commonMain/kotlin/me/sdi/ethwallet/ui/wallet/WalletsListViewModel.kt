package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository

private const val TAG = "WalletsListViewModel"

class WalletsListViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val uiState: StateFlow<WalletsListUiState>
        field = MutableStateFlow(WalletsListUiState(isLoading = true))

    private val newsChannel = Channel<WalletScreenNews>(Channel.BUFFERED)
    val news = newsChannel
        .receiveAsFlow()
        .onEach { Logger.debug(TAG) { "News: $it" } }

    init {
        uiState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        walletRepository
            .observeActiveWallet()
            .catch {
                Logger.warn(TAG, it) { "Error observing active wallet" }
            }
            .onEach { activeWallet ->
                if (activeWallet?.address != uiState.value.selected?.address) {
                    activeWallet?.let { wallet ->
                        newsChannel.send(WalletScreenNews.ShowWalletDetails(wallet.address))
                    }
                }

                uiState.update {
                    it.copy(selected = activeWallet)
                }
            }
            .launchIn(viewModelScope)

        walletRepository
            .observeAllWallets()
            .onStart { uiState.update { it.copy(isLoading = true) } }
            .catch {
                Logger.warn(TAG, it) { "Error observing all wallets" }
                uiState.update { uiState -> uiState.copy(isLoading = false) }
            }
            .onEach { allWallets ->
                uiState.update {
                    it.copy(wallets = allWallets, isLoading = false)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: WalletsListEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is WalletsListEvent.OnWalletSelected -> setActiveWallet(event.address)
            is WalletsListEvent.OnDeleteWalletClick -> deleteWallet(event.address)
        }
    }

    private fun setActiveWallet(address: EthAddress) {
        viewModelScope.launch {
            suspendableTryCatch(
                block = {
                    walletRepository.setActiveWallet(address)
                },
                onFailure = { e ->
                    Logger.warn(TAG, e) { "Error setting active wallet" }
                    newsChannel.send(WalletScreenNews.ShowErrorMessage)
                },
            )
        }
    }

    private fun deleteWallet(address: EthAddress) {
        viewModelScope.launch {
            suspendableTryCatch(
                block = {
                    walletRepository.deleteWallet(address)
                    if (address == uiState.value.selected?.address) {
                        newsChannel.send(WalletScreenNews.SelectedWalletDeleted)
                    }
                },
                onFailure = { e ->
                    Logger.warn(TAG, e) { "Error deleting wallet" }

                    newsChannel.send(WalletScreenNews.ShowErrorMessage)
                },
            )
        }
    }
}

private suspend fun suspendableTryCatch(
    block: suspend () -> Unit,
    onFailure: suspend (Exception) -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        yield()

        onFailure(e)
    }
}

data class WalletsListUiState(
    val selected: EthWallet? = null,
    val wallets: List<EthWallet> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface WalletScreenNews {
    data class ShowWalletDetails(val ethAddress: EthAddress) : WalletScreenNews
    data object ShowErrorMessage : WalletScreenNews
    data object SelectedWalletDeleted : WalletScreenNews
}

sealed interface WalletsListEvent {
    data class OnWalletSelected(val address: EthAddress) : WalletsListEvent
    data class OnDeleteWalletClick(val address: EthAddress) : WalletsListEvent
}

