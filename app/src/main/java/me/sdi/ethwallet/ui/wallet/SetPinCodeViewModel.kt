package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger

/**
 * UI state for the Set Pin Code screen.
 */
data class SetPinCodeViewState(
    val firstPin: String = "",
    val secondPin: String = "",
    val isConfirmationMode: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

/**
 * One-time UI events emitted by the ViewModel
 */
sealed interface SetPinCodeNews {
    /**
     * Pin code has been successfully set
     */
    data class PinCodeSet(val pinCode: String) : SetPinCodeNews

    /**
     * Error has occurred
     */
    data class Error(val message: String) : SetPinCodeNews
}

/**
 * Events that can be handled by the SetPinCodeViewModel.
 */
sealed interface SetPinCodeEvent {
    /**
     * User clicked a digit button
     */
    data class OnDigitClick(val digit: Int) : SetPinCodeEvent

    /**
     * User clicked the delete button
     */
    data object OnDeleteClick : SetPinCodeEvent

    /**
     * User clicked the reset button
     */
    data object OnResetClick : SetPinCodeEvent
}

/**
 * ViewModel for the Set Pin Code screen.
 * Handles PIN code setting and validation using Unidirectional Data Flow pattern.
 */
class SetPinCodeViewModel : ViewModel() {
    private val _viewState = MutableStateFlow(SetPinCodeViewState())
    val viewState: StateFlow<SetPinCodeViewState> = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<SetPinCodeNews>()
    val news: SharedFlow<SetPinCodeNews> = _news.asSharedFlow()

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: SetPinCodeEvent) {
        when (event) {
            is SetPinCodeEvent.OnDigitClick -> handleDigitClick(event.digit)
            is SetPinCodeEvent.OnDeleteClick -> handleDeleteClick()
            is SetPinCodeEvent.OnResetClick -> handleResetClick()
        }
    }

    private fun handleDigitClick(digit: Int) {
        val currentState = _viewState.value

        if (currentState.isSaving) {
            return
        }

        // Clear any error message when user inputs a new digit
        if (currentState.errorMessage != null) {
            _viewState.update { it.copy(errorMessage = null) }
        }

        if (currentState.isConfirmationMode) {
            // Handle confirmation mode
            val secondPin = currentState.secondPin
            if (secondPin.length < PIN_MAX_LENGTH) {
                val updatedPin = secondPin + digit.toString()

                _viewState.update { it.copy(secondPin = updatedPin) }

                // Check if PIN is complete and perform validation
                if (updatedPin.length == PIN_MAX_LENGTH) {
                    // Add delay before validation to allow the user to see the complete PIN
                    viewModelScope.launch {
                        delay(300) // 300ms delay for animation
                        validatePins()
                    }
                }
            }
        } else {
            // Handle initial PIN entry
            val firstPin = currentState.firstPin
            if (firstPin.length < PIN_MAX_LENGTH) {
                val updatedPin = firstPin + digit.toString()

                _viewState.update { it.copy(firstPin = updatedPin) }

                // When the first PIN is complete, switch to confirmation mode
                if (updatedPin.length == PIN_MAX_LENGTH) {
                    // Add delay before switching to confirmation mode
                    viewModelScope.launch {
                        delay(300) // 300ms delay for animation
                        _viewState.update {
                            it.copy(isConfirmationMode = true)
                        }
                    }
                }
            }
        }
    }

    private fun handleDeleteClick() {
        val currentState = _viewState.value

        if (currentState.isSaving) {
            return
        }

        // Clear any error message when user presses delete
        if (currentState.errorMessage != null) {
            _viewState.update { it.copy(errorMessage = null) }
        }

        if (currentState.isConfirmationMode) {
            val secondPin = currentState.secondPin
            if (secondPin.isNotEmpty()) {
                _viewState.update { it.copy(secondPin = secondPin.dropLast(1)) }
            }
        } else {
            val firstPin = currentState.firstPin
            if (firstPin.isNotEmpty()) {
                _viewState.update { it.copy(firstPin = firstPin.dropLast(1)) }
            }
        }
    }

    private fun handleResetClick() {
        _viewState.update {
            it.copy(
                isConfirmationMode = false,
                secondPin = "",
                errorMessage = null,
            )
        }
    }

    private fun validatePins() {
        val currentState = _viewState.value
        val firstPin = currentState.firstPin
        val secondPin = currentState.secondPin

        if (firstPin == secondPin) {
            // PINs match, handle success
            _viewState.update { it.copy(isSaving = true) }

            viewModelScope.launch {
                // In a real implementation, we would store the PIN securely here
                Logger.debug(TAG) { "PIN code set successfully: $firstPin" }

                // Emit success event
                _news.emit(SetPinCodeNews.PinCodeSet(firstPin))
            }
        } else {
            // PINs don't match, show error
            _viewState.update {
                it.copy(
                    errorMessage = "PINs don't match. Try again.",
                    isConfirmationMode = false,
                    firstPin = "",
                    secondPin = "",
                )
            }
        }
    }

    companion object {
        private const val TAG = "SetPinCodeViewModel"
        private const val PIN_MAX_LENGTH = 6

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SetPinCodeViewModel()
                }
            }
    }
}
