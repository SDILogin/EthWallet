package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import me.sdi.ethwallet.core.security.PasswordInteractor
import me.sdi.ethwallet.core.security.PasswordScorer
import me.sdi.ethwallet.core.security.PasswordStrength
import me.sdi.ethwallet.core.security.passwordStrength

private const val TAG = "SetPassword"

data class SetPasswordViewState(
    val password: String = "",
    val confirmation: String = "",
    val strength: PasswordStrength? = null,
    val isSaving: Boolean = false,
    val error: SetPasswordError? = null,
) {
    val isMismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != password

    val canContinue: Boolean get() = password.isNotEmpty() && password == confirmation && !isSaving

    override fun toString(): String =
        "SetPasswordViewState(strength=$strength, isSaving=$isSaving, error=$error, isMismatch=$isMismatch)"
}

enum class SetPasswordError { SetupFailed }

sealed interface SetPasswordEvent {
    data class OnPasswordChange(val password: String) : SetPasswordEvent {
        override fun toString(): String = "OnPasswordChange"
    }

    data class OnConfirmationChange(val confirmation: String) : SetPasswordEvent {
        override fun toString(): String = "OnConfirmationChange"
    }

    data object OnContinueClick : SetPasswordEvent
}

sealed interface SetPasswordNews {
    data object PasswordSet : SetPasswordNews
}

class SetPasswordViewModel(
    private val passwordInteractor: PasswordInteractor,
    private val passwordScorer: PasswordScorer,
) : ViewModel() {
    val viewState: StateFlow<SetPasswordViewState>
        field = MutableStateFlow(SetPasswordViewState())

    private val newsChannel = Channel<SetPasswordNews>(Channel.BUFFERED)

    val news: Flow<SetPasswordNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    private var scoringJob: Job? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SetPasswordEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        when (event) {
            is SetPasswordEvent.OnPasswordChange -> handlePasswordChange(event.password)
            is SetPasswordEvent.OnConfirmationChange -> viewState.update {
                it.copy(confirmation = event.confirmation, error = null)
            }
            SetPasswordEvent.OnContinueClick -> handleContinueClick()
        }
    }

    private fun handlePasswordChange(password: String) {
        viewState.update { it.copy(password = password, strength = null, error = null) }
        scoringJob?.cancel()
        if (password.isNotEmpty()) {
            scoringJob = viewModelScope.launch {
                val score = passwordScorer.score(password)
                val strength = passwordStrength(length = password.length, score = score)
                viewState.update { state -> if (state.password == password) state.copy(strength = strength) else state }
            }
        }
    }

    private fun handleContinueClick() {
        val password = viewState.value.password
        viewState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                passwordInteractor.setPassword(password)
                newsChannel.send(SetPasswordNews.PasswordSet)
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error setting the wallet password" }
                viewState.update { it.copy(isSaving = false, error = SetPasswordError.SetupFailed) }
            }
        }
    }
}
