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
import me.sdi.ethwallet.core.security.DataStorePinCodeStorage
import me.sdi.ethwallet.core.security.PinCodeStorage

/**
 * One-time UI events emitted by the ViewModel
 */
sealed interface VerifyPinCodeNews {
    /**
     * PIN was verified successfully
     */
    data object PinVerified : VerifyPinCodeNews

    /**
     * User chose to logout
     */
    data object LoggedOut : VerifyPinCodeNews

    /**
     * Error occurred
     */
    data class Error(val message: String) : VerifyPinCodeNews
}

/**
 * ViewModel for the PIN code verification screen
 * Handles PIN verification, attempts counting, and timeouts
 *
 * @param pinCodeStorage Storage implementation for verifying PIN codes
 */
class VerifyPinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
) : ViewModel() {
    private val _viewState = MutableStateFlow(VerifyPinCodeViewState())
    val viewState: StateFlow<VerifyPinCodeViewState> = _viewState.asStateFlow()

    private val _news = MutableSharedFlow<VerifyPinCodeNews>()
    val news: SharedFlow<VerifyPinCodeNews> = _news.asSharedFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    /**
     * Single entry point for handling all events in the ViewModel
     */
    fun onEvent(event: VerifyPinCodeEvent) {
        val currentState = _viewState.value

        if (currentState.isVerifying || currentState.isLocked) {
            return
        }

        when (event) {
            is VerifyPinCodeEvent.OnDigitClick -> handleDigitClick(event.digit)
            is VerifyPinCodeEvent.OnDeleteClick -> handleDeleteClick()
            is VerifyPinCodeEvent.OnLogoutClick -> handleLogoutClick()
        }
    }

    private fun handleDigitClick(digit: Int) {
        val currentState = _viewState.value
        val enteredPin = currentState.enteredPin

        if (currentState.errorMessage != null) {
            _viewState.update { it.copy(errorMessage = null) }
        }

        if (enteredPin.length < PIN_MAX_LENGTH) {
            val updatedPin = enteredPin + digit.toString()
            _viewState.update { it.copy(enteredPin = updatedPin) }

            // If PIN is complete, verify it
            if (updatedPin.length == PIN_MAX_LENGTH) {
                verifyPin(updatedPin)
            }
        }
    }

    private fun handleDeleteClick() {
        val currentState = _viewState.value
        val enteredPin = currentState.enteredPin

        if (currentState.errorMessage != null) {
            _viewState.update { it.copy(errorMessage = null) }
        }

        if (enteredPin.isNotEmpty()) {
            _viewState.update { it.copy(enteredPin = enteredPin.dropLast(1)) }
        }
    }

    private fun handleLogoutClick() {
        viewModelScope.launch {
            pinCodeStorage.clearPinCode()
            _news.emit(VerifyPinCodeNews.LoggedOut)
        }
    }

    private fun verifyPin(pin: String) {
        _viewState.update { it.copy(isVerifying = true) }

        viewModelScope.launch {
            delay(300) // Small delay for animation

            try {
                val isValid = pinCodeStorage.verifyPinCode(pin)

                if (isValid) {
                    _news.emit(VerifyPinCodeNews.PinVerified)
                } else {
                    handleInvalidPin()
                }
            } catch (e: Exception) {
                Logger.debug(TAG) { "Error verifying PIN: ${e.message}" }
                _viewState.update {
                    it.copy(
                        errorMessage = "Error: ${e.message}",
                        isVerifying = false,
                    )
                }
                _news.emit(VerifyPinCodeNews.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun handleInvalidPin() {
        val currentState = _viewState.value
        val newAttempts = currentState.remainingAttempts - 1

        if (newAttempts <= 0) {
            // No attempts left, lock the screen
            _viewState.update {
                it.copy(
                    enteredPin = "",
                    errorMessage = "Too many failed attempts",
                    remainingAttempts = 0,
                    isVerifying = false,
                    isLocked = true,
                    lockCountdown = LOCK_TIMEOUT_SECONDS,
                )
            }
            startLockCountdown()
        } else {
            // Still have attempts left
            _viewState.update {
                it.copy(
                    enteredPin = "",
                    errorMessage = "Incorrect PIN code",
                    remainingAttempts = newAttempts,
                    isVerifying = false,
                )
            }
        }
    }

    private fun startLockCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_viewState.value.lockCountdown > 0) {
                delay(1000)
                _viewState.update {
                    it.copy(lockCountdown = it.lockCountdown - 1)
                }
            }
            _viewState.update {
                it.copy(
                    isLocked = false,
                    remainingAttempts = MAX_ATTEMPTS,
                    errorMessage = null,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    companion object {
        private const val TAG = "VerifyPinCodeViewModel"
        private const val PIN_MAX_LENGTH = 6
        private const val MAX_ATTEMPTS = 3
        private const val LOCK_TIMEOUT_SECONDS = 30

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val context = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    val pinCodeStorage = DataStorePinCodeStorage(context)
                    VerifyPinCodeViewModel(pinCodeStorage)
                }
            }
    }
}
