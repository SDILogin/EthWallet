package me.sdi.ethwallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineName
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
import me.sdi.ethwallet.core.security.PinCodeStorage
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ChangePinCode"
private const val PIN_LENGTH = 6
private const val INDICATOR_ANIMATION_DELAY_MS = 300

class ChangePinCodeViewModel(
    private val pinCodeStorage: PinCodeStorage,
) : ViewModel() {
    val viewState: StateFlow<ChangePinCodeViewState>
        field = MutableStateFlow(ChangePinCodeViewState())

    private val newsChannel = Channel<ChangePinCodeNews>(Channel.BUFFERED)

    val news: Flow<ChangePinCodeNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ChangePinCodeEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        if (viewState.value.isSaving) {
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
        viewState.update { it.withEnteredPin(updatedPin).copy(error = null) }

        if (updatedPin.length < PIN_LENGTH) {
            return
        }

        when (state.step) {
            ChangePinCodeStep.EnterNew -> moveToConfirmationAfterAnimation()
            ChangePinCodeStep.ConfirmNew -> onConfirmationEntered(updatedPin)
        }
    }

    private fun handleDeleteClick() {
        val enteredPin = viewState.value.enteredPin
        if (enteredPin.length == PIN_LENGTH) {
            return
        }

        viewState.update { it.withEnteredPin(enteredPin.dropLast(1)).copy(error = null) }
    }

    private fun moveToConfirmationAfterAnimation() {
        viewModelScope.launch {
            delay(INDICATOR_ANIMATION_DELAY_MS.milliseconds)
            viewState.update { it.copy(step = ChangePinCodeStep.ConfirmNew) }
        }
    }

    private fun onConfirmationEntered(confirmPin: String) {
        val newPin = viewState.value.newPin
        if (confirmPin != newPin) {
            restartWithError(ChangePinCodeError.PinsDoNotMatch)
            return
        }

        changePin(newPin)
    }

    private fun changePin(newPin: String) {
        viewState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                coroutineScope {
                    launch(CoroutineName("IndicatorAnimation")) { delay(INDICATOR_ANIMATION_DELAY_MS.milliseconds) }
                    pinCodeStorage.changePinCode(newPin)
                }
                newsChannel.send(ChangePinCodeNews.PinChanged)
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error changing PIN" }
                restartWithError(ChangePinCodeError.Unknown)
            }
        }
    }

    private fun restartWithError(error: ChangePinCodeError) {
        viewState.update { ChangePinCodeViewState(error = error) }
        viewModelScope.launch { newsChannel.send(ChangePinCodeNews.ShakePinInput) }
    }
}

enum class ChangePinCodeStep { EnterNew, ConfirmNew }

enum class ChangePinCodeError { PinsDoNotMatch, Unknown }

data class ChangePinCodeViewState(
    val step: ChangePinCodeStep = ChangePinCodeStep.EnterNew,
    val newPin: String = "",
    val confirmPin: String = "",
    val error: ChangePinCodeError? = null,
    val isSaving: Boolean = false,
) {
    val enteredPin: String
        get() = when (step) {
            ChangePinCodeStep.EnterNew -> newPin
            ChangePinCodeStep.ConfirmNew -> confirmPin
        }

    fun withEnteredPin(pin: String): ChangePinCodeViewState = when (step) {
        ChangePinCodeStep.EnterNew -> copy(newPin = pin)
        ChangePinCodeStep.ConfirmNew -> copy(confirmPin = pin)
    }

    override fun toString(): String =
        "ChangePinCodeViewState(step=$step, enteredDigits=${enteredPin.length}, error=$error, isSaving=$isSaving)"
}

sealed interface ChangePinCodeNews {
    data object PinChanged : ChangePinCodeNews

    data object ShakePinInput : ChangePinCodeNews
}

sealed interface ChangePinCodeEvent {
    data class OnDigitClick(val digit: Int) : ChangePinCodeEvent

    data object OnDeleteClick : ChangePinCodeEvent
}
