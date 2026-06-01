package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository

private const val TAG = "WalletsListViewModel"

data class WalletsListUiState(
    val activeWallet: EthWallet? = null,
    val otherWallets: List<EthWallet> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface WalletScreenNews {
    data object NavigateToWalletDetails : WalletScreenNews

    data object NavigateToOnBoarding : WalletScreenNews

    data class ShowErrorMessage(val error: Throwable) : WalletScreenNews
}

sealed interface WalletsListEvent {
    data object Start : WalletsListEvent

    data class SetActiveWallet(val address: EthAddress) : WalletsListEvent

    data class DeleteWallet(val address: EthAddress) : WalletsListEvent

    data object OnBackPress : WalletsListEvent
}

class WalletsListViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletsListUiState(isLoading = true))
    val uiState: StateFlow<WalletsListUiState> = _uiState.asStateFlow()

    private val _news =
        MutableSharedFlow<WalletScreenNews>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val news: SharedFlow<WalletScreenNews> = _news.asSharedFlow()

    init {
        uiState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        news
            .onEach { Logger.debug(TAG) { "News: $it" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: WalletsListEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is WalletsListEvent.Start -> loadWallets()
            is WalletsListEvent.SetActiveWallet -> setActiveWallet(event.address)
            is WalletsListEvent.DeleteWallet -> deleteWallet(event.address)
            is WalletsListEvent.OnBackPress -> navigateToWalletDetails()
        }
    }

    private fun setActiveWallet(address: EthAddress) {
        viewModelScope.launch {
            try {
                walletRepository.setActiveWallet(address)
                loadWallets()
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error setting active wallet\n ${e.printStackTrace()}"
                }

                _news.emit(WalletScreenNews.ShowErrorMessage(e))
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "setActiveWalletJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "setActiveWalletJob completed with exception" }
                }
            }
        }
    }

    private fun deleteWallet(address: EthAddress) {
        viewModelScope.launch {
            try {
                val wasActive = uiState.value.activeWallet?.address == address
                walletRepository.deleteWallet(address)

                val remainingWallets = walletRepository.getAllWallets()
                if (remainingWallets.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            activeWallet = null,
                            otherWallets = emptyList(),
                            isLoading = false,
                        )
                    }
                    _news.emit(WalletScreenNews.NavigateToOnBoarding)
                } else {
                    if (wasActive) {
                        val newActiveAddress = remainingWallets.first().address
                        walletRepository.setActiveWallet(newActiveAddress)
                    }
                    loadWallets()
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error deleting wallet\n ${e.printStackTrace()}"
                }

                _news.emit(WalletScreenNews.ShowErrorMessage(e))
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "deleteWalletJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "deleteWalletJob completed with exception" }
                }
            }
        }
    }

    private fun navigateToWalletDetails() {
        viewModelScope.launch {
            _news.emit(WalletScreenNews.NavigateToWalletDetails)
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "navigateToWalletDetailsJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "navigateToWalletDetailsJob completed with exception" }
                }
            }
        }
    }

    private fun loadWallets() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val activeWallet = walletRepository.getActiveWallet()
                val allWallets = walletRepository.getAllWallets()
                val otherWallets = if (activeWallet != null) {
                    allWallets.filterNot { it.address == activeWallet.address }
                } else {
                    allWallets
                }

                _uiState.update {
                    it.copy(
                        activeWallet = activeWallet,
                        otherWallets = otherWallets,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error loading wallets\n ${e.printStackTrace()}"
                }
                _uiState.update { it.copy(isLoading = false) }

                _news.emit(WalletScreenNews.ShowErrorMessage(e))
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "loadWalletsJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "loadWalletsJob completed with exception" }
                }
            }
        }
    }
}
