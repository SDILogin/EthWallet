package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.WalletRepository

/**
 * UI state for the Import Wallet screen.
 *
 * @param label The user-visible wallet name
 * @param mnemonic The recovery phrase entered by the user
 * @param isImportEnabled Whether the name and recovery phrase are valid for import
 * @param isLoading Whether an import is in progress
 * @param isError Whether the last import attempt failed
 * @param errorMessage Message describing the failure, if any
 */
data class ImportWalletViewState(
    val label: String = "",
    val mnemonic: String = "",
    val isImportEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
) {
    // Redacts the mnemonic so state logging never leaks seed material.
    override fun toString(): String =
        "ImportWalletViewState(label=$label, mnemonic=<redacted>, isImportEnabled=$isImportEnabled, " +
            "isLoading=$isLoading, isError=$isError, errorMessage=$errorMessage)"
}

/**
 * Events that can be handled by the ImportWalletViewModel.
 */
sealed interface ImportWalletEvent {
    /** The wallet label changed. */
    data class LabelChanged(val label: String) : ImportWalletEvent

    /** The recovery phrase text changed. */
    data class MnemonicChanged(val mnemonic: String) : ImportWalletEvent {
        // Redacts the mnemonic so event logging never leaks seed material.
        override fun toString(): String = "MnemonicChanged(mnemonic=<redacted>)"
    }

    /** The user requested importing the wallet. */
    data object ImportClicked : ImportWalletEvent
}

/**
 * One-time effects emitted by the ImportWalletViewModel.
 */
sealed interface ImportWalletNews {
    /** The wallet was imported and stored successfully. */
    data object WalletImported : ImportWalletNews
}

/**
 * ViewModel for the Import Wallet screen.
 * Validates the entered recovery phrase and restores the wallet through the repository.
 */
class ImportWalletViewModel(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val viewState: StateFlow<ImportWalletViewState>
        field = MutableStateFlow(ImportWalletViewState())

    val news: SharedFlow<ImportWalletNews>
        field = MutableSharedFlow<ImportWalletNews>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        news
            .onEach { Logger.debug(TAG) { "News: $it" } }
            .launchIn(viewModelScope)
    }

    /**
     * Single entry point for handling all events in the ViewModel.
     */
    fun onEvent(event: ImportWalletEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is ImportWalletEvent.LabelChanged -> onLabelChanged(event.label)
            is ImportWalletEvent.MnemonicChanged -> onMnemonicChanged(event.mnemonic)
            is ImportWalletEvent.ImportClicked -> importWallet()
        }
    }

    private fun onMnemonicChanged(mnemonic: String) {
        viewState.update {
            it.copy(
                mnemonic = mnemonic,
                isImportEnabled = isValidMnemonic(mnemonic) && isValidWalletLabel(it.label),
                isError = false,
                errorMessage = null,
            )
        }
    }

    private fun onLabelChanged(label: String) {
        viewState.update {
            it.copy(
                label = label,
                isImportEnabled = isValidMnemonic(it.mnemonic) && isValidWalletLabel(label),
            )
        }
    }

    private fun importWallet() {
        if (!viewState.value.isImportEnabled || viewState.value.isLoading) {
            return
        }

        viewState.update { it.copy(isLoading = true, isError = false, errorMessage = null) }

        viewModelScope.launch {
            try {
                walletRepository.importWallet(
                    mnemonic = viewState.value.mnemonic,
                    label = normalizeWalletLabel(viewState.value.label),
                )
                viewState.update { it.copy(isLoading = false) }
                news.emit(ImportWalletNews.WalletImported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug(TAG) { "Error importing wallet: ${e.message}" }
                viewState.update {
                    it.copy(isLoading = false, isError = true, errorMessage = e.message)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ImportWalletViewModel"
    }
}

private fun isValidMnemonic(mnemonic: String): Boolean {
    val words = mnemonic.trim().split(Regex("\\s+"))
    return words.size == 12 && words.all { it.isNotEmpty() }
}

private fun isValidWalletLabel(label: String): Boolean =
    isWalletLabelValid(normalizeWalletLabel(label))
