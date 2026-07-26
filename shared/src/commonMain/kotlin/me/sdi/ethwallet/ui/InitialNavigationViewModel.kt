package me.sdi.ethwallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.database.dao.WalletDao

private const val TAG = "InitialNavigationViewModel"

/**
 * Represents the initial route the app should navigate to.
 * Note: SetPinCode is not an initial route - it's shown via explicit navigation
 * after wallet creation in onboarding flow.
 */
enum class InitialRoute {
    OnBoarding,
    Wallet,
}

/**
 * Initial state for navigation, including the initial route.
 *
 * @param isLocked the PIN screen covers the whole app while this is `true`.
 */
data class InitialNavigationState(
    val numberOfWallets: Int = -1,
    val isPinCodeSet: Boolean = false,
    val isLocked: Boolean = false,
    val initialRoute: InitialRoute? = null,
)

/**
 * Events that can be handled by the InitialNavigationViewModel.
 */
sealed interface InitialNavigationEvent {
    /**
     * The last wallet is gone, either deleted by the user or wiped together with the PIN on logout.
     */
    data object WalletsCleared : InitialNavigationEvent
}

class InitialNavigationViewModel(
    private val walletDao: WalletDao,
    private val pinCodeStorage: PinCodeStorage,
    private val dataCleaner: DataCleaner,
    private val pinSession: PinSession,
) : ViewModel() {
    val state: StateFlow<InitialNavigationState>
        field = MutableStateFlow(InitialNavigationState())

    init {
        state
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        pinSession.isUnlocked
            .onEach { isUnlocked ->
                state.update {
                    val isPinCodeSet = it.isPinCodeSet || isUnlocked
                    it.copy(
                        isPinCodeSet = isPinCodeSet,
                        isLocked = isPinCodeSet && !isUnlocked,
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            try {
                val wallets = walletDao.getAllWallets()

                val initialRoute = if (wallets.isEmpty()) {
                    cleanQuietly()
                    InitialRoute.OnBoarding
                } else if (pinCodeStorage.hasPinCode()) {
                    InitialRoute.Wallet
                } else {
                    cleanQuietly()
                    InitialRoute.OnBoarding
                }

                val isPinCodeSet = initialRoute == InitialRoute.Wallet

                state.value = InitialNavigationState(
                    numberOfWallets = wallets.size,
                    isPinCodeSet = isPinCodeSet,
                    isLocked = isPinCodeSet && !pinSession.isUnlocked.value,
                    initialRoute = initialRoute,
                )
            } catch (e: Exception) {
                ensureActive()
                Logger.warn("Db", e) { "Failed to check auth state" }

                cleanQuietly()

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

    /**
     * Single entry point for handling all events in the ViewModel
     */
    fun onEvent(event: InitialNavigationEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        when (event) {
            is InitialNavigationEvent.WalletsCleared -> state.value = InitialNavigationState(
                numberOfWallets = 0,
                isPinCodeSet = false,
                isLocked = false,
                initialRoute = InitialRoute.OnBoarding,
            )
        }
    }

    private suspend fun cleanQuietly() {
        try {
            dataCleaner.clean()
        } catch (e: Exception) {
            yield()

            Logger.warn("Db", e) { "Failed to clean local data" }
        }
    }
}
