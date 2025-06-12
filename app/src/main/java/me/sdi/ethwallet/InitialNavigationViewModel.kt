package me.sdi.ethwallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.ui.navigation.Destination

/**
 * Initial state for navigation, including the initial route
 */
data class InitialNavigationState(
    val numberOfWallets: Int = -1,
    val isPinCodeSet: Boolean = false,
    val initialRoute: Destination? = null,
)

class InitialNavigationViewModel(
    private val walletDao: WalletDao,
    private val pinCodeStorage: PinCodeStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(InitialNavigationState())
    val state: StateFlow<InitialNavigationState> = _state.asStateFlow()

    // For backward compatibility
    val numberOfWallets: StateFlow<Int> get() = MutableStateFlow(_state.value.numberOfWallets).asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val wallets = walletDao.getAllWallets()
                val hasPin = pinCodeStorage.hasPinCode()

                val initialRoute = when {
                    wallets.isEmpty() -> Destination.OnBoardingScreen
                    !hasPin -> Destination.SetPinCodeScreen
                    else -> Destination.VerifyPinCodeScreen
                }

                _state.value = InitialNavigationState(
                    numberOfWallets = wallets.size,
                    isPinCodeSet = hasPin,
                    initialRoute = initialRoute,
                )
            } catch (e: Exception) {
                ensureActive()
                Logger.warn("Db") { e.stackTraceToString() }

                _state.value = InitialNavigationState(
                    numberOfWallets = 0,
                    isPinCodeSet = false,
                    initialRoute = Destination.OnBoardingScreen,
                )
            }
        }
    }
}
