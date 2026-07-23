package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.PinCodeStorage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "PinVerification"
private const val PIN_MAX_LENGTH = 6
private const val MAX_ATTEMPTS = 3
private const val LOCK_TIMEOUT_SECONDS = 30
private const val ERROR_CLEAR_DELAY_MS = 500

/**
 * ViewModel for the PIN code verification screen
 * Handles PIN verification, attempts counting, and timeouts
 *
 * @param pinCodeStorage Storage implementation for verifying PIN codes
 */
class VerifyPinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
    private val dataCleaner: DataCleaner,
) : ViewModel() {
    val viewState: StateFlow<VerifyPinCodeViewState>
        field = MutableStateFlow(VerifyPinCodeViewState())

    private val newsChannel = Channel<VerifyPinCodeNews>(Channel.BUFFERED)

    val news: Flow<VerifyPinCodeNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    private var countdownJob: Job? = null
    private var verificationJob: Job? = null
    private var errorClearJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    /**
     * Single entry point for handling all events in the ViewModel
     */
    fun onEvent(event: VerifyPinCodeEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        if (event is VerifyPinCodeEvent.Reset) {
            handleLogout()
            return
        }

        val currentState = viewState.value

        if (currentState.isVerifying || currentState.isLocked || currentState.isLoggingOut) {
            return
        }

        when (event) {
            is VerifyPinCodeEvent.OnDigitClick -> handleDigitClick(event.digit)
            is VerifyPinCodeEvent.OnDeleteClick -> handleDeleteClick()
        }
    }

    private fun handleLogout() {
        if (viewState.value.isLoggingOut) {
            return
        }

        verificationJob?.cancel()
        verificationJob = null
        countdownJob?.cancel()
        countdownJob = null
        errorClearJob?.cancel()
        errorClearJob = null
        viewState.update {
            it.copy(
                errorMessage = null,
                isVerifying = false,
                isLoggingOut = true,
            )
        }

        viewModelScope.launch {
            try {
                dataCleaner.clean()
                newsChannel.send(VerifyPinCodeNews.LoggedOut)
            } catch (e: Exception) {
                ensureActive()

                val errorMessage = e.message ?: "Unknown error"
                Logger.debug(TAG) { "Error clearing local wallet data: $errorMessage" }
                viewState.update {
                    it.copy(
                        errorMessage = "Error: $errorMessage",
                        isLoggingOut = false,
                    )
                }
                newsChannel.send(VerifyPinCodeNews.Error(errorMessage))
            }
        }
    }

    private fun handleDigitClick(digit: Int) {
        val enteredPin = viewState.value.enteredPin

        if (enteredPin.length == PIN_MAX_LENGTH) {
            errorClearJob?.cancel()
            viewState.update { it.copy(enteredPin = digit.toString(), errorMessage = null) }
            return
        }

        val updatedPin = enteredPin + digit.toString()
        viewState.update { it.copy(enteredPin = updatedPin, errorMessage = null) }

        if (updatedPin.length == PIN_MAX_LENGTH) {
            verifyPin(updatedPin)
        }
    }

    private fun handleDeleteClick() {
        val enteredPin = viewState.value.enteredPin

        if (enteredPin.length == PIN_MAX_LENGTH) {
            errorClearJob?.cancel()
            viewState.update { it.copy(enteredPin = "", errorMessage = null) }
            return
        }

        viewState.update { it.copy(enteredPin = enteredPin.dropLast(1), errorMessage = null) }
    }

    private fun verifyPin(pin: String) {
        viewState.update { it.copy(isVerifying = true) }

        verificationJob = viewModelScope.launch {
            try {
                delay(300.milliseconds)

                val isValid = pinCodeStorage.verifyPinCode(pin)
                ensureActive()

                if (isValid) {
                    newsChannel.send(VerifyPinCodeNews.PinVerified)
                } else {
                    handleInvalidPin()
                }
            } catch (e: Exception) {
                ensureActive()

                val errorMessage = e.message ?: "Unknown error"
                Logger.debug(TAG) { "Error verifying PIN: $errorMessage" }
                viewState.update {
                    it.copy(
                        enteredPin = "",
                        errorMessage = "Error: $errorMessage",
                        isVerifying = false,
                    )
                }
                newsChannel.send(VerifyPinCodeNews.Error(errorMessage))
            }
        }
    }

    private fun handleInvalidPin() {
        val currentState = viewState.value
        val newAttempts = currentState.remainingAttempts - 1

        if (newAttempts <= 0) {
            viewState.update {
                it.copy(
                    errorMessage = "Too many failed attempts",
                    remainingAttempts = 0,
                    isVerifying = false,
                    isLocked = true,
                    lockCountdown = LOCK_TIMEOUT_SECONDS,
                    failedAttemptId = it.failedAttemptId + 1,
                )
            }
            startLockCountdown()
        } else {
            viewState.update {
                it.copy(
                    errorMessage = "Incorrect PIN code",
                    remainingAttempts = newAttempts,
                    isVerifying = false,
                    failedAttemptId = it.failedAttemptId + 1,
                )
            }
        }

        scheduleErrorClear()
    }

    private fun scheduleErrorClear() {
        errorClearJob?.cancel()
        errorClearJob = viewModelScope.launch {
            delay(ERROR_CLEAR_DELAY_MS.milliseconds)
            viewState.update { it.copy(enteredPin = "") }
        }
    }

    private fun startLockCountdown() {
        countdownJob?.cancel()?.also {
            Logger.debug(TAG) { "Previous countdownJob has been cancelled" }
        }
        countdownJob = viewModelScope.launch {
            while (viewState.value.lockCountdown > 0) {
                delay(1.seconds)
                viewState.update {
                    it.copy(lockCountdown = it.lockCountdown - 1)
                }
            }
            viewState.update {
                it.copy(
                    isLocked = false,
                    remainingAttempts = MAX_ATTEMPTS,
                    errorMessage = null,
                )
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "countdownJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "countdownJob completed with exception" }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        verificationJob?.cancel()
        countdownJob?.cancel()
        errorClearJob?.cancel()
    }
}

/**
 * UI state for the Verify Pin Code screen.
 */
data class VerifyPinCodeViewState(
    val enteredPin: String = "",
    val errorMessage: String? = null,
    val remainingAttempts: Int = 3,
    val isVerifying: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isLocked: Boolean = false,
    val lockCountdown: Int = 0,
    val failedAttemptId: Int = 0,
)

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
 * Events that can be handled by the VerifyPinCodeViewModel.
 */
sealed interface VerifyPinCodeEvent {
    /**
     * User clicked a digit button
     */
    data class OnDigitClick(val digit: Int) : VerifyPinCodeEvent

    /**
     * User clicked the delete button
     */
    data object OnDeleteClick : VerifyPinCodeEvent

    /**
     * Confirm local data cleanup and logout.
     */
    data object Reset : VerifyPinCodeEvent
}
