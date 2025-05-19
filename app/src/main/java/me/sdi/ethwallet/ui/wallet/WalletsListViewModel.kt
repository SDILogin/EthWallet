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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity

private const val TAG = "WalletsListViewModel"

data class WalletsListUiState(
    val wallets: List<WalletEntity> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface WalletScreenNews {
    data object NavigateToWalletDetails : WalletScreenNews

    data object NavigateToOnBoarding : WalletScreenNews

    data class ShowErrorMessage(val error: Throwable) : WalletScreenNews
}

sealed interface WalletsListEvent {
    data object Start : WalletsListEvent

    data class SetActiveWallet(val address: String) : WalletsListEvent

    data class DeleteWallet(val address: String) : WalletsListEvent

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

    fun onEvent(event: WalletsListEvent) {
        when (event) {
            is WalletsListEvent.Start -> loadWallets()
            is WalletsListEvent.SetActiveWallet -> setActiveWallet(event.address)
            is WalletsListEvent.DeleteWallet -> deleteWallet(event.address)
            is WalletsListEvent.OnBackPress -> navigateToWalletDetails()
        }
    }

    private fun setActiveWallet(address: String) {
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
        }
    }

    private fun deleteWallet(address: String) {
        viewModelScope.launch {
            try {
                walletRepository.deleteWallet(address)

                val onScreenWallets = uiState.value.wallets.filterNot { it.address == address }

                _uiState.update { vs -> vs.copy(wallets = onScreenWallets) }

                if (onScreenWallets.isEmpty()) {
                    _news.emit(WalletScreenNews.NavigateToOnBoarding)
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error deleting wallet\n ${e.printStackTrace()}"
                }

                _news.emit(WalletScreenNews.ShowErrorMessage(e))
            }
        }
    }

    private fun navigateToWalletDetails() {
        viewModelScope.launch {
            _news.emit(WalletScreenNews.NavigateToWalletDetails)
        }
    }

    private fun loadWallets() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val wallets = walletRepository.getAllWallets()
                _uiState.update {
                    it.copy(
                        wallets = wallets,
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
        }
    }
}
