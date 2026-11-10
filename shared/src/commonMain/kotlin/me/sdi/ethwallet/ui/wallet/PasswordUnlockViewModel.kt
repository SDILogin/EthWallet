package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.PasswordInteractor

private const val TAG = "PasswordUnlock"

data class PasswordUnlockViewState(
    val password: String = "",
    val isBusy: Boolean = false,
    val error: PasswordUnlockError? = null,
) {
    override fun toString(): String = "PasswordUnlockViewState(isBusy=$isBusy, error=$error)"
}

enum class PasswordUnlockError { WrongPassword, UnlockFailed, ResetFailed }

sealed interface PasswordUnlockEvent {
    data class OnPasswordChange(val password: String) : PasswordUnlockEvent {
        override fun toString(): String = "OnPasswordChange"
    }

    data object OnUnlockClick : PasswordUnlockEvent

    data object OnResetConfirm : PasswordUnlockEvent
}

sealed interface PasswordUnlockNews {
    data object Unlocked : PasswordUnlockNews

    data object LoggedOut : PasswordUnlockNews
}

class PasswordUnlockViewModel(
    private val passwordInteractor: PasswordInteractor,
    private val dataCleaner: DataCleaner,
) : ViewModel() {
    val viewState: StateFlow<PasswordUnlockViewState>
        field = MutableStateFlow(PasswordUnlockViewState())

    private val newsChannel = Channel<PasswordUnlockNews>(Channel.BUFFERED)

    val news: Flow<PasswordUnlockNews> =
        newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PasswordUnlockEvent) {
        Logger.debug(TAG) { "onEvent: $event" }

        when (event) {
            is PasswordUnlockEvent.OnPasswordChange -> viewState.update {
                it.copy(password = event.password, error = null)
            }
            PasswordUnlockEvent.OnUnlockClick -> handleUnlockClick()
            PasswordUnlockEvent.OnResetConfirm -> handleResetConfirm()
        }
    }

    private fun handleUnlockClick() {
        val password = viewState.value.password
        viewState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                if (passwordInteractor.unlock(password)) {
                    newsChannel.send(PasswordUnlockNews.Unlocked)
                } else {
                    viewState.update { it.copy(isBusy = false, error = PasswordUnlockError.WrongPassword) }
                }
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error unlocking with the wallet password" }
                viewState.update { it.copy(isBusy = false, error = PasswordUnlockError.UnlockFailed) }
            }
        }
    }

    private fun handleResetConfirm() {
        viewState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                dataCleaner.clean()
                newsChannel.send(PasswordUnlockNews.LoggedOut)
            } catch (e: Exception) {
                ensureActive()

                Logger.warn(TAG, e) { "Error clearing local wallet data" }
                viewState.update { it.copy(isBusy = false, error = PasswordUnlockError.ResetFailed) }
            }
        }
    }
}
