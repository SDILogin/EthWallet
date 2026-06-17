package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.WalletRepository

/**
 * UI state representation for the Create Wallet screen.
 */
sealed interface CreateWalletViewState {
    /**
     * Loading state shown during wallet creation.
     */
    data object Loading : CreateWalletViewState

    /**
     * Content state displaying the seed phrase.
     * @param seedPhrase The list of words in the mnemonic seed phrase
     */
    data class Content(
        val seedPhrase: List<String>,
    ) : CreateWalletViewState

    /**
     * Error state when wallet creation fails.
     * @param throwable The error that occurred
     */
    data class Error(val throwable: Throwable) : CreateWalletViewState
}

/**
 * Events that can be handled by the CreateWalletViewModel.
 */
sealed interface CreateWalletEvent {
    /** Triggered to create a new wallet */
    data object CreateWallet : CreateWalletEvent
}

/**
 * ViewModel for the Create Wallet screen.
 * Handles wallet creation and state management.
 */
class CreateWalletViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val viewState: StateFlow<CreateWalletViewState>
        field = MutableStateFlow<CreateWalletViewState>(CreateWalletViewState.Loading)

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)
    }

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: CreateWalletEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is CreateWalletEvent.CreateWallet -> createWallet()
        }
    }

    private fun createWallet() {
        viewState.update { CreateWalletViewState.Loading }

        viewModelScope.launch {
            val vs: CreateWalletViewState =
                try {
                    val seedPhrase = walletRepository.createWallet(label = "My Wallet")
                    CreateWalletViewState.Content(
                        seedPhrase = seedPhrase,
                    )
                } catch (e: Exception) {
                    ensureActive()

                    Logger.debug(TAG) {
                        "Error creating wallet: ${e.message}"
                    }
                    CreateWalletViewState.Error(e)
                }

            viewState.update { vs }
        }.also { job ->
            job.invokeOnCompletion {
                if (it == null) {
                    Logger.debug(TAG) { "createWalletJob successfully completed" }
                } else {
                    Logger.warn(TAG, it) { "createWalletJob completed with exception" }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CreateWalletViewModel"
    }
}
