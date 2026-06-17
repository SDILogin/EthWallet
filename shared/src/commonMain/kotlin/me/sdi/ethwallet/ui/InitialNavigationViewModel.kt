package me.sdi.ethwallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.database.dao.WalletDao

private const val TAG = "InitialNavigationViewModel"

/**
 * Represents the initial route the app should navigate to.
 * Note: SetPinCode is not an initial route - it's shown via explicit navigation
 * after wallet creation in onboarding flow.
 */
enum class InitialRoute {
    OnBoarding,
    VerifyPinCode,
}

/**
 * Initial state for navigation, including the initial route
 */
data class InitialNavigationState(
    val numberOfWallets: Int = -1,
    val isPinCodeSet: Boolean = false,
    val initialRoute: InitialRoute? = null,
)

class InitialNavigationViewModel(
    private val walletDao: WalletDao,
    private val pinCodeStorage: PinCodeStorage,
) : ViewModel() {
    val state: StateFlow<InitialNavigationState>
        field = MutableStateFlow(InitialNavigationState())

    init {
        state
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            try {
                val wallets = walletDao.getAllWallets()

                val initialRoute = if (wallets.isEmpty()) {
                    // No wallets - clear any stale PIN and show onboarding
                    pinCodeStorage.clearPinCode()
                    InitialRoute.OnBoarding
                } else if (pinCodeStorage.hasPinCode()) {
                    // Wallets exist and PIN is set - show unlock screen
                    InitialRoute.VerifyPinCode
                } else {
                    // Wallets exist but no PIN - invalid state, clear and restart
                    InitialRoute.OnBoarding
                }

                state.value = InitialNavigationState(
                    numberOfWallets = wallets.size,
                    isPinCodeSet = initialRoute == InitialRoute.VerifyPinCode,
                    initialRoute = initialRoute,
                )
            } catch (e: Exception) {
                ensureActive()
                Logger.warn("Db") { e.stackTraceToString() }

                runCatching { pinCodeStorage.clearPinCode() }

                state.value = InitialNavigationState(
                    numberOfWallets = 0,
                    isPinCodeSet = false,
                    initialRoute = InitialRoute.OnBoarding,
                )
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "initializationJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "initializationJob completed with exception" }
                }
            }
        }
    }
}
