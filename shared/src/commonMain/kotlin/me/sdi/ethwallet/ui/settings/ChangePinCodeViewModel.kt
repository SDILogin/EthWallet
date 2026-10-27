package me.sdi.ethwallet.ui.settings

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
import me.sdi.ethwallet.core.security.PinLockPolicy
import me.sdi.ethwallet.core.security.PinLockState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ChangePinCode"
private const val PIN_LENGTH = 6
private val INDICATOR_ANIMATION_DELAY = 300.milliseconds
private const val INCORRECT_PIN_MESSAGE = "Incorrect PIN code"
private const val LOCKED_MESSAGE = "Too many failed attempts"
private const val MISMATCH_MESSAGE = "PINs don't match. Try again."
private const val SAME_PIN_MESSAGE = "New PIN must be different from the current PIN"
private const val GENERIC_ERROR_MESSAGE = "Something went wrong"

/**
 * Collects the current PIN, a new PIN and its confirmation, then re-wraps the master KEK with
 * [PinCodeStorage.changePinCode]. The current PIN is checked only on that final call, and a wrong
 * one counts against the same [PinAttemptsGuard] as the lock screen.
 */
class ChangePinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val dataCleaner: DataCleaner,
) : ViewModel() {
    val viewState: StateFlow<ChangePinCodeViewState>
        field = MutableStateFlow(ChangePinCodeViewState())

    private val newsChannel = Channel<ChangePinCodeNews>(Channel.BUFFERED)

    val news: Flow<ChangePinCodeNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    private var countdownJob: Job? = null

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
                    val locked = status.lockRemaining > Duration.ZERO
                    viewState.update {
                        it.copy(
                            isInitializing = false,
                            isLocked = locked,
                            lockCountdown = if (locked) status.lockRemaining.inWholeSeconds.toInt() else 0,
                            remainingAttempts = status.remainingInWindow,
                        )
                    }
                    if (locked) {
                        startLockCountdown()
                    }
                }
            }
        }
    }

    fun onEvent(event: ChangePinCodeEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        if (!viewState.value.isKeypadEnabled) {
            return
        }

        when (event) {
            is ChangePinCodeEvent.OnDigitClick -> handleDigitClick(event.digit)
            is ChangePinCodeEvent.OnDeleteClick -> handleDeleteClick()
        }
    }

    private fun handleDigitClick(digit: Int) {
        val state = viewState.value
        if (state.enteredPin.length == PIN_LENGTH) {
            return
        }

        val updatedPin = state.enteredPin + digit.toString()
        viewState.update { it.withEnteredPin(updatedPin).copy(errorMessage = null) }

        if (updatedPin.length < PIN_LENGTH) {
            return
        }

        when (state.step) {
            ChangePinCodeStep.EnterCurrent -> moveToStepAfterAnimation(ChangePinCodeStep.EnterNew)
            ChangePinCodeStep.EnterNew -> onNewPinEntered(updatedPin)
            ChangePinCodeStep.ConfirmNew -> onConfirmationEntered(updatedPin)
        }
    }

    private fun handleDeleteClick() {
        val enteredPin = viewState.value.enteredPin
        if (enteredPin.length == PIN_LENGTH) {
            return
        }

        viewState.update { it.withEnteredPin(enteredPin.dropLast(1)).copy(errorMessage = null) }
    }

    private fun onNewPinEntered(newPin: String) {
        if (newPin == viewState.value.currentPin) {
            viewState.update {
                it.copy(
                    newPin = "",
                    errorMessage = SAME_PIN_MESSAGE,
                    showRemainingAttempts = false,
                    errorId = it.errorId + 1,
                )
            }
            return
        }

        moveToStepAfterAnimation(ChangePinCodeStep.ConfirmNew)
    }

    private fun onConfirmationEntered(confirmPin: String) {
        val state = viewState.value
        if (confirmPin != state.newPin) {
            viewState.update {
                it.copy(
                    step = ChangePinCodeStep.EnterNew,
                    newPin = "",
                    confirmPin = "",
                    errorMessage = MISMATCH_MESSAGE,
                    showRemainingAttempts = false,
                    errorId = it.errorId + 1,
                )
            }
            return
        }

        changePin(currentPin = state.currentPin, newPin = state.newPin)
    }

    private fun moveToStepAfterAnimation(step: ChangePinCodeStep) {
        viewModelScope.launch {
            delay(INDICATOR_ANIMATION_DELAY)
            viewState.update { it.copy(step = step) }
        }
    }

    private fun changePin(currentPin: String, newPin: String) {
        viewState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val isChanged = coroutineScope {
                    launch(CoroutineName("IndicatorAnimation")) { delay(INDICATOR_ANIMATION_DELAY) }
                    pinCodeStorage.changePinCode(currentPin = currentPin, newPin = newPin)
                }
                if (isChanged) {
                    pinAttemptsGuard.onSuccess()
                    newsChannel.send(ChangePinCodeNews.PinChanged)
                } else {
                    handleWrongCurrentPin()
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error changing PIN" }
                viewState.update {
                    it.restartedFromCurrentPin().copy(
                        errorMessage = GENERIC_ERROR_MESSAGE,
                        showRemainingAttempts = false,
                        isSaving = false,
                        errorId = it.errorId + 1,
                    )
                }
            }
        }
    }

    private suspend fun handleWrongCurrentPin() {
        when (val result = pinAttemptsGuard.onFailedAttempt()) {
            is PinLockState.Tampered -> {
                handleTamperDetected()
            }
            is PinLockState.Active -> {
                val locked = result.lockRemaining > Duration.ZERO
                viewState.update {
                    it.restartedFromCurrentPin().copy(
                        errorMessage = if (locked) LOCKED_MESSAGE else INCORRECT_PIN_MESSAGE,
                        showRemainingAttempts = true,
                        remainingAttempts = result.remainingInWindow,
                        isSaving = false,
                        isLocked = locked,
                        lockCountdown = if (locked) result.lockRemaining.inWholeSeconds.toInt() else it.lockCountdown,
                        errorId = it.errorId + 1,
                    )
                }
                if (locked) {
                    startLockCountdown()
                }
            }
        }
    }

    private fun handleTamperDetected() {
        Logger.warn(TAG) { "PIN guard reported tampering; wiping local data" }
        if (viewState.value.isLoggingOut) {
            return
        }

        viewModelScope.coroutineContext.cancelChildren()

        viewState.update {
            it.restartedFromCurrentPin().copy(
                errorMessage = null,
                isSaving = false,
                isLoggingOut = true,
            )
        }

        viewModelScope.launch {
            try {
                dataCleaner.clean()
                newsChannel.send(ChangePinCodeNews.LoggedOut)
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error clearing local wallet data" }
                viewState.update {
                    it.copy(
                        errorMessage = GENERIC_ERROR_MESSAGE,
                        showRemainingAttempts = false,
                        isLoggingOut = false,
                    )
                }
            }
        }
    }

    private fun startLockCountdown() {
        countdownJob?.cancel()
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
        }
    }
}

enum class ChangePinCodeStep { EnterCurrent, EnterNew, ConfirmNew }

data class ChangePinCodeViewState(
    val step: ChangePinCodeStep = ChangePinCodeStep.EnterCurrent,
    val currentPin: String = "",
    val newPin: String = "",
    val confirmPin: String = "",
    val errorMessage: String? = null,
    val showRemainingAttempts: Boolean = false,
    val remainingAttempts: Int = PinLockPolicy.ATTEMPTS_PER_WINDOW,
    val isLocked: Boolean = false,
    val lockCountdown: Int = 0,
    val errorId: Int = 0,
    val isInitializing: Boolean = true,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
) {
    val enteredPin: String
        get() = when (step) {
            ChangePinCodeStep.EnterCurrent -> currentPin
            ChangePinCodeStep.EnterNew -> newPin
            ChangePinCodeStep.ConfirmNew -> confirmPin
        }

    val isKeypadEnabled: Boolean
        get() = !isInitializing && !isLocked && !isSaving && !isLoggingOut

    fun withEnteredPin(pin: String): ChangePinCodeViewState = when (step) {
        ChangePinCodeStep.EnterCurrent -> copy(currentPin = pin)
        ChangePinCodeStep.EnterNew -> copy(newPin = pin)
        ChangePinCodeStep.ConfirmNew -> copy(confirmPin = pin)
    }

    fun restartedFromCurrentPin(): ChangePinCodeViewState =
        copy(step = ChangePinCodeStep.EnterCurrent, currentPin = "", newPin = "", confirmPin = "")

    override fun toString(): String =
        "ChangePinCodeViewState(step=$step, enteredDigits=${enteredPin.length}, errorMessage=$errorMessage, " +
            "showRemainingAttempts=$showRemainingAttempts, remainingAttempts=$remainingAttempts, isLocked=$isLocked, " +
            "lockCountdown=$lockCountdown, errorId=$errorId, isInitializing=$isInitializing, isSaving=$isSaving, " +
            "isLoggingOut=$isLoggingOut)"
}

sealed interface ChangePinCodeNews {
    data object PinChanged : ChangePinCodeNews

    data object LoggedOut : ChangePinCodeNews
}

sealed interface ChangePinCodeEvent {
    data class OnDigitClick(val digit: Int) : ChangePinCodeEvent

    data object OnDeleteClick : ChangePinCodeEvent
}
