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
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.FileBasedEthWalletCreator
import me.sdi.ethwallet.core.security.KeyStoreEncryptor
import me.sdi.ethwallet.data.AndroidTempFileProvider
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.DatabaseBuilder

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
                    val seedPhrase = walletRepository.createWallet(label = "My Wallet")
                    CreateWalletViewState.Content(
                        seedPhrase = seedPhrase,
                    )
                } catch (e: Exception) {
                    ensureActive()

                    Logger.debug("CreateWalletViewModel") {
                        "Error creating wallet: ${e.message}"
                    }
                    CreateWalletViewState.Error(e)
                }

            _viewState.update { vs }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val context = checkNotNull(this[APPLICATION_KEY])
                    val tempFileProvider = AndroidTempFileProvider(context)
                    val walletCreator = FileBasedEthWalletCreator(tempFileProvider)
                    val privateKeyEncryptor = KeyStoreEncryptor()
                    val database = DatabaseBuilder.getInstance(context)
                    val walletRepository =
                        WalletRepository(
                            walletDao = database.walletDao(),
                            walletCreator = walletCreator,
                            privateKeyEncryptor = privateKeyEncryptor,
                        )

                    CreateWalletViewModel(
                        walletRepository = walletRepository,
                    )
                }
            }
    }
}
