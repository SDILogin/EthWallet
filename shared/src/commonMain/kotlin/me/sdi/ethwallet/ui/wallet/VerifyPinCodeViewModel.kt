package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "PinVerification"
private const val PIN_MAX_LENGTH = 6
private const val ERROR_CLEAR_DELAY_MS = 500
private const val BIOMETRIC_INVALIDATED_MESSAGE = "Biometrics changed. Biometric unlock was reset."
private const val BIOMETRIC_ERROR_MESSAGE = "Biometric unlock failed. Use your PIN."

/**
 * ViewModel for the PIN code verification screen.
 *
 * Verifies entered PIN codes and delegates failure counting and lockout timing to
 * [PinAttemptsGuard], whose [PinLockState] results are the single source of truth for the
 * remaining attempts and lock countdown shown on screen.
 */
class VerifyPinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
    private val dataCleaner: DataCleaner,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val biometricUnlock: BiometricUnlock,
) : ViewModel() {
    val viewState: StateFlow<VerifyPinCodeViewState>
        field = MutableStateFlow(VerifyPinCodeViewState())

    private val newsChannel = Channel<VerifyPinCodeNews>(Channel.BUFFERED)

    val news: Flow<VerifyPinCodeNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    private var countdownJob: Job? = null
    private var verificationJob: Job? = null
    private var errorClearJob: Job? = null
    private var biometricJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            when (val status = pinAttemptsGuard.status()) {
                is PinLockState.Tampered -> {
                    handleTamperDetected()
                }
                is PinLockState.Active -> {
                    val isBiometricAvailable = biometricUnlock.status() == BiometricStatus.Ready
                    if (status.lockRemaining > Duration.ZERO) {
                        viewState.update {
                            it.copy(
                                isInitializing = false,
                                isLocked = true,
                                lockCountdown = status.lockRemaining.inWholeSeconds.toInt(),
                                remainingAttempts = status.remainingInWindow,
                                isBiometricAvailable = isBiometricAvailable,
                            )
                        }
                        startLockCountdown()
                    } else {
                        viewState.update {
                            it.copy(
                                isInitializing = false,
                                remainingAttempts = status.remainingInWindow,
                                isBiometricAvailable = isBiometricAvailable,
                            )
                        }
                    }
                }
            }
        }
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

        if (currentState.isInitializing || currentState.isVerifying || currentState.isLoggingOut) {
            return
        }

        when (event) {
            is VerifyPinCodeEvent.OnDigitClick -> if (!currentState.isLocked) handleDigitClick(event.digit)
            is VerifyPinCodeEvent.OnDeleteClick -> if (!currentState.isLocked) handleDeleteClick()
            is VerifyPinCodeEvent.OnBiometricClick -> handleBiometricClick()
        }
    }

    private fun handleTamperDetected() {
        Logger.warn(TAG) { "PIN guard reported tampering; wiping local data" }
        handleLogout()
    }

    private fun handleLogout() {
        if (viewState.value.isLoggingOut) {
            return
        }

        viewModelScope.coroutineContext.cancelChildren()

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

                Logger.warn(TAG, e) { "Error clearing local wallet data" }
                viewState.update {
                    it.copy(
                        errorMessage = "Something went wrong",
                        showRemainingAttempts = false,
                        isLoggingOut = false,
                    )
                }
                newsChannel.send(VerifyPinCodeNews.Error)
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

    private fun handleBiometricClick() {
        viewState.update { it.copy(isVerifying = true) }

        biometricJob = viewModelScope.launch {
            try {
                when (val result = biometricUnlock.unlock()) {
                    is BiometricResult.Success -> {
                        onUnlocked()
                    }
                    is BiometricResult.Cancelled -> {
                        Unit
                    }
                    is BiometricResult.Invalidated -> {
                        viewState.update {
                            it.copy(
                                errorMessage = BIOMETRIC_INVALIDATED_MESSAGE,
                                showRemainingAttempts = false,
                                isBiometricAvailable = false,
                            )
                        }
                        scheduleErrorClear()
                    }
                    is BiometricResult.Error -> {
                        Logger.debug(TAG) { "Biometric unlock failed: ${result.message}" }
                        viewState.update {
                            it.copy(
                                errorMessage = BIOMETRIC_ERROR_MESSAGE,
                                showRemainingAttempts = false,
                            )
                        }
                        scheduleErrorClear()
                    }
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Biometric unlock failed unexpectedly" }
                viewState.update {
                    it.copy(
                        errorMessage = BIOMETRIC_ERROR_MESSAGE,
                        showRemainingAttempts = false,
                        isVerifying = false,
                    )
                }
                scheduleErrorClear()
            }
        }.also { job ->
            job.invokeOnCompletion {
                viewState.update { it.copy(isVerifying = false) }
            }
        }
    }

    private fun verifyPin(pin: String) {
        viewState.update { it.copy(isVerifying = true) }

        verificationJob = viewModelScope.launch {
            try {
                coroutineScope {
                    launch(CoroutineName("IndicatorAnimation")) { delay(300.milliseconds) }
                    pinCodeStorage.verifyPinCode(pin).also { isValid ->
                        if (isValid) {
                            onUnlocked()
                        } else {
                            handleInvalidPin()
                        }
                    }
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error verifying PIN" }

                viewState.update {
                    it.copy(
                        enteredPin = "",
                        errorMessage = "Something went wrong",
                        showRemainingAttempts = false,
                        isVerifying = false,
                    )
                }
                newsChannel.send(VerifyPinCodeNews.Error)
            }
        }
    }

    private suspend fun onUnlocked() {
        pinAttemptsGuard.onSuccess()
        newsChannel.send(VerifyPinCodeNews.Unlocked)
    }

    private suspend fun handleInvalidPin() {
        when (val result = pinAttemptsGuard.onFailedAttempt()) {
            is PinLockState.Tampered -> {
                handleTamperDetected()
            }
            is PinLockState.Active -> {
                val locked = result.lockRemaining > Duration.ZERO
                viewState.update {
                    it.copy(
                        errorMessage = if (locked) "Too many failed attempts" else "Incorrect PIN code",
                        showRemainingAttempts = true,
                        remainingAttempts = result.remainingInWindow,
                        isVerifying = false,
                        isLocked = locked,
                        lockCountdown = if (locked) result.lockRemaining.inWholeSeconds.toInt() else it.lockCountdown,
                        failedAttemptId = it.failedAttemptId + 1,
                    )
                }
                if (locked) {
                    startLockCountdown()
                }
                scheduleErrorClear()
            }
        }
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
            var active = true
            while (active) {
                while (viewState.value.lockCountdown > 0) {
                    delay(1.seconds)
                    viewState.update { it.copy(lockCountdown = it.lockCountdown - 1) }
                }
                when (val status = pinAttemptsGuard.status()) {
                    is PinLockState.Tampered -> {
                        handleTamperDetected()
                        active = false
                    }
                    is PinLockState.Active -> {
                        if (status.lockRemaining > Duration.ZERO) {
                            viewState.update {
                                it.copy(lockCountdown = status.lockRemaining.inWholeSeconds.toInt())
                            }
                        } else {
                            viewState.update {
                                it.copy(
                                    isLocked = false,
                                    remainingAttempts = status.remainingInWindow,
                                    errorMessage = null,
                                )
                            }
                            active = false
                        }
                    }
                }
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
    val isInitializing: Boolean = true,
    val isBiometricAvailable: Boolean = false,
    val showRemainingAttempts: Boolean = false,
)

/**
 * One-time UI events emitted by the ViewModel
 */
sealed interface VerifyPinCodeNews {
    data object Unlocked : VerifyPinCodeNews

    data object LoggedOut : VerifyPinCodeNews

    data object Error : VerifyPinCodeNews
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
     * User clicked the biometric unlock button
     */
    data object OnBiometricClick : VerifyPinCodeEvent

    /**
     * Confirm local data cleanup and logout.
     */
    data object Reset : VerifyPinCodeEvent
}
