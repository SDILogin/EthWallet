package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger

/**
 * UI state for the Set Pin Code screen.
 */
sealed interface SetPinCodeViewState {
    /**
     * Loading state shown during PIN processing.
     */
    data object Loading : SetPinCodeViewState

    /**
     * Success state when PIN is set successfully.
     */
    data object Success : SetPinCodeViewState

    /**
     * Error state when PIN setting fails.
     * @param throwable The error that occurred
     */
    data class Error(val throwable: Throwable) : SetPinCodeViewState
}

/**
 * Events that can be handled by the SetPinCodeViewModel.
 */
sealed interface SetPinCodeEvent {
    /** Triggered to set a PIN code */
    data class SetPinCode(val pinCode: String) : SetPinCodeEvent
}

/**
 * ViewModel for the Set Pin Code screen.
 * Handles PIN code setting and validation.
 */
class SetPinCodeViewModel : ViewModel() {
    private val _viewState = MutableStateFlow<SetPinCodeViewState>(SetPinCodeViewState.Loading)
    val viewState: StateFlow<SetPinCodeViewState> = _viewState.asStateFlow()

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: SetPinCodeEvent) {
        when (event) {
            is SetPinCodeEvent.SetPinCode -> setPinCode(event.pinCode)
        }
    }

    private fun setPinCode(pinCode: String) {
        _viewState.update { SetPinCodeViewState.Loading }

        viewModelScope.launch {
            val vs: SetPinCodeViewState =
                try {
                    // Here you would save the PIN code securely
                    // For example, using the KeyStoreEncryptor or similar
                    // For now, we'll just simulate success

                    Logger.debug("SetPinCodeViewModel") {
                        "PIN code set successfully"
                    }

                    SetPinCodeViewState.Success
                } catch (e: Exception) {
                    Logger.debug("SetPinCodeViewModel") {
                        "Error setting PIN code: ${e.message}"
                    }
                    SetPinCodeViewState.Error(e)
                }

            _viewState.update { vs }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    // In a real implementation, you would inject dependencies here
                    SetPinCodeViewModel()
                }
            }
    }
}
