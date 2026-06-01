package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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
import me.sdi.ethwallet.core.security.PinCodeStorage

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

    /**
     * Reset the view state to default empty state
     */
    data object Reset : SetPinCodeEvent
}

/**
 * ViewModel for the Set Pin Code screen.
 * Handles PIN code setting and validation using Unidirectional Data Flow pattern.
 *
 * @param pinCodeStorage The storage implementation for saving and verifying PIN codes
 */
class SetPinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
) : ViewModel() {
    private val _viewState = MutableStateFlow(SetPinCodeViewState())
    val viewState: StateFlow<SetPinCodeViewState> = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<SetPinCodeNews>()
    val news: SharedFlow<SetPinCodeNews> = _news.asSharedFlow()

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        news
            .onEach { Logger.debug(TAG) { "News: $it" } }
            .launchIn(viewModelScope)
    }

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: SetPinCodeEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is SetPinCodeEvent.OnDigitClick -> handleDigitClick(event.digit)
            is SetPinCodeEvent.OnDeleteClick -> handleDeleteClick()
            is SetPinCodeEvent.OnResetClick -> handleResetClick()
            is SetPinCodeEvent.Reset -> handleFullReset()
        }
    }

    private fun handleFullReset() {
        _viewState.value = SetPinCodeViewState()
    }

    private fun handleDigitClick(digit: Int) {
        val currentState = _viewState.value
        if (currentState.isSaving) {
            return
        }

        if (currentState.errorMessage != null) {
            _viewState.update { it.copy(errorMessage = null) }
        }

        if (currentState.isConfirmationMode) {
            val secondPin = currentState.secondPin
            if (secondPin.length < PIN_MAX_LENGTH) {
                val updatedPin = secondPin + digit.toString()
                _viewState.update { it.copy(secondPin = updatedPin) }
                if (updatedPin.length == PIN_MAX_LENGTH) {
                    viewModelScope.launch {
                        delay(300) // 300ms delay for animation
                        validatePins()
                    }.also { job ->
                        job.invokeOnCompletion {
                            if (it == null) {
                                Logger.debug(TAG) { "validatePinsTriggerJob successfully completed" }
                            } else {
                                Logger.warn(TAG, it) { "validatePinsTriggerJob completed with exception" }
                            }
                        }
                    }
                }
            }
        } else {
            val firstPin = currentState.firstPin
            if (firstPin.length < PIN_MAX_LENGTH) {
                val updatedPin = firstPin + digit.toString()
                _viewState.update { it.copy(firstPin = updatedPin) }
                if (updatedPin.length == PIN_MAX_LENGTH) {
                    viewModelScope.launch {
                        delay(300) // 300ms delay for animation
                        _viewState.update {
                            it.copy(isConfirmationMode = true)
                        }
                    }.also { job ->
                        job.invokeOnCompletion {
                            if (it == null) {
                                Logger.debug(TAG) { "confirmationModeDelayJob successfully completed" }
                            } else {
                                Logger.warn(TAG, it) { "confirmationModeDelayJob completed with exception" }
                            }
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
            _viewState.update { it.copy(isSaving = true) }

            viewModelScope.launch {
                try {
                    pinCodeStorage.storePinCode(firstPin).also {
                        Logger.debug(TAG) { "PIN code stored successfully" }
                    }

                    _viewState.update { it.copy(isSaving = false) }

                    _news.emit(SetPinCodeNews.PinCodeSet(firstPin))
                } catch (e: Exception) {
                    ensureActive()

                    Logger.debug(TAG) { "Error storing PIN code: ${e.message}" }
                    _viewState.update {
                        it.copy(
                            errorMessage = "Error: ${e.message}",
                            isSaving = false,
                        )
                    }
                    _news.emit(SetPinCodeNews.Error(e.message ?: "Unknown error"))
                }
            }.also { job ->
                job.invokeOnCompletion {
                    if (it == null) {
                        Logger.debug(TAG) { "storePinCodeJob successfully completed" }
                    } else {
                        Logger.warn(TAG, it) { "storePinCodeJob completed with exception" }
                    }
                }
            }
        } else {
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
    }
}
