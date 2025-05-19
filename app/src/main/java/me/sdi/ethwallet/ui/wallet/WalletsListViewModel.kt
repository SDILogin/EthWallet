package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.ui.navigation.Destination

private const val TAG = "WalletsListViewModel"

data class WalletsListUiState(
    val wallets: List<WalletEntity> = emptyList(),
    val isLoading: Boolean = false,
    val navigateTo: Destination? = null,
)

class WalletsListViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletsListUiState(isLoading = true))
    val uiState: StateFlow<WalletsListUiState> = _uiState.asStateFlow()

    init {
        loadWallets()
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
            }
        }
    }

    fun setActiveWallet(address: String) {
        viewModelScope.launch {
            try {
                walletRepository.setActiveWallet(address)
                loadWallets()
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error setting active wallet\n ${e.printStackTrace()}"
                }
            }
        }
    }

    fun deleteWallet(address: String) {
        viewModelScope.launch {
            try {
                walletRepository.deleteWallet(address)

                val hasWallets = walletRepository.hasWallets()
                if (!hasWallets) {
                    _uiState.update { it.copy(navigateTo = Destination.OnBoardingScreen) }
                } else {
                    loadWallets()
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG) {
                    "Error deleting wallet\n ${e.printStackTrace()}"
                }
            }
        }
    }

    fun navigateToWalletDetails() {
        _uiState.update { it.copy(navigateTo = Destination.WalletScreen) }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateTo = null) }
    }
}
