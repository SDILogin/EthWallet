package me.sdi.ethwallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock

private const val TAG = "SettingsViewModel"
private const val VIEW_STATE_STOP_TIMEOUT_MS = 5_000L

/**
 * UI state for the Settings screen.
 */
data class SettingsViewState(
    val isBiometricEnabled: Boolean = false,
    val isBiometricSwitchEnabled: Boolean = false,
    val biometricHint: BiometricSettingsHint = BiometricSettingsHint.None,
    val appLockMethod: AppLockMethod = AppLockMethod.Pin,
)

enum class BiometricSettingsHint { None, NotSupported, NotEnrolled, Invalidated }

/**
 * Events that can be handled by the SettingsViewModel.
 */
sealed interface SettingsEvent {
    /**
     * User flipped the biometric unlock switch
     */
    data class OnBiometricToggle(val enabled: Boolean) : SettingsEvent

    /**
     * User asked to lock the app
     */
    data object OnLockClick : SettingsEvent
}

class SettingsViewModel(
    private val biometricUnlock: BiometricUnlock,
    private val pinSession: PinSession,
    private val appLockMethod: AppLockMethod,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsViewState(appLockMethod = appLockMethod))

    val viewState: StateFlow<SettingsViewState> = state
        .onStart {
            if (appLockMethod == AppLockMethod.Pin) {
                refreshBiometricState()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_STATE_STOP_TIMEOUT_MS), state.value)

    private var toggleJob: Job? = null

    init {
        state
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: SettingsEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is SettingsEvent.OnBiometricToggle -> handleBiometricToggle(event.enabled)
            SettingsEvent.OnLockClick -> pinSession.clear()
        }
    }

    private fun handleBiometricToggle(enabled: Boolean) {
        if (toggleJob?.isActive == true) {
            return
        }

        toggleJob = viewModelScope.launch {
            if (enabled) {
                val result = biometricUnlock.enable()
                Logger.debug(TAG) { "Biometric unlock enable result: $result" }
            } else {
                biometricUnlock.disable()
            }

            refreshBiometricState()
        }
    }

    private suspend fun refreshBiometricState() {
        state.value = when (biometricUnlock.status()) {
            BiometricStatus.Ready -> SettingsViewState(
                isBiometricEnabled = true,
                isBiometricSwitchEnabled = true,
            )
            BiometricStatus.NotSetUp -> SettingsViewState(isBiometricSwitchEnabled = true)
            BiometricStatus.NotEnrolled -> SettingsViewState(
                biometricHint = BiometricSettingsHint.NotEnrolled,
            )
            BiometricStatus.NotSupported -> SettingsViewState(
                biometricHint = BiometricSettingsHint.NotSupported,
            )
            BiometricStatus.Invalidated -> SettingsViewState(
                isBiometricSwitchEnabled = true,
                biometricHint = BiometricSettingsHint.Invalidated,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        toggleJob?.cancel()
    }
}
