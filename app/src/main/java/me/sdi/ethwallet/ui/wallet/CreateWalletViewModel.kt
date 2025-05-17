package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.core.EthWalletCreator
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.data.AndroidTempFileProvider

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
class CreateWalletViewModel(private val walletCreator: EthWalletCreator) : ViewModel() {
    private val _viewState = MutableStateFlow<CreateWalletViewState>(CreateWalletViewState.Loading)
    val viewState: StateFlow<CreateWalletViewState> = _viewState.asStateFlow()

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: CreateWalletEvent) {
        when (event) {
            is CreateWalletEvent.CreateWallet -> createWallet()
        }
    }

    private fun createWallet() {
        _viewState.update { CreateWalletViewState.Loading }

        viewModelScope.launch {
            val vs: CreateWalletViewState =
                try {
                    CreateWalletViewState.Content(
                        seedPhrase = walletCreator.createWallet().seedPhrase,
                    )
                } catch (e: Exception) {
                    ensureActive()

                    CreateWalletViewState.Error(e)
                }

            _viewState.update { vs }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val tempFileProvider = AndroidTempFileProvider(checkNotNull(this[APPLICATION_KEY]))
                    val walletCreator = FileBasedEthWalletCreator(tempFileProvider)
                    CreateWalletViewModel(walletCreator)
                }
            }
    }
}
