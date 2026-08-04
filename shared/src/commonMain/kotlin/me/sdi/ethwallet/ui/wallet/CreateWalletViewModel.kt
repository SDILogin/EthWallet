package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.data.repository.WalletRepository

/**
 * ViewModel for the Create Wallet screen.
 * Handles wallet creation and state management.
 */
class CreateWalletViewModel(
    private val ethManager: EthManager,
    private val walletRepository: WalletRepository,
) : ViewModel() {
    val viewState: StateFlow<CreateWalletViewState>
        field = MutableStateFlow<CreateWalletViewState>(CreateWalletViewState())

    private val newsChannel = Channel<CreateWalletNews>(Channel.BUFFERED)
    val news: Flow<CreateWalletNews> = newsChannel.receiveAsFlow().onEach { Logger.debug(TAG) { "News: $it" } }

    private var ethCreationResult: EthCreationResult? = null

    init {
        viewState
            .onEach { Logger.debug(TAG) { "ViewState: $it" } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ethCreationResult = ethManager.createWallet().also {
                    this@CreateWalletViewModel.ethCreationResult = it
                }

                viewState.update {
                    it.copy(seedPhrase = ethCreationResult.seedPhrase)
                }
            }
        }
    }

    fun onEvent(event: CreateWalletEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is CreateWalletEvent.LabelChanged -> viewState.update { it.copy(label = event.label) }
            is CreateWalletEvent.SeedPhraseSaved -> onSeedPhraseSaved()
        }
    }

    private fun onSeedPhraseSaved() {
        val ethCreationResult = ethCreationResult ?: return
        val label = normalizeWalletLabel(viewState.value.label)
        if (!isWalletLabelValid(label) || viewState.value.isSaving) {
            return
        }

        viewState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                walletRepository.saveWallet(ethCreationResult, label = label)
                newsChannel.send(CreateWalletNews.WalletCreated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, e) { "Error saving wallet" }
                viewState.update { it.copy(isSaving = false) }
            }
        }
    }

    companion object {
        private const val TAG = "CreateWalletViewModel"
    }
}

data class CreateWalletViewState(
    val seedPhrase: List<String> = emptyList(),
    val label: String = "",
    val isSaving: Boolean = false,
) {
    val isLabelValid: Boolean
        get() = isWalletLabelValid(normalizeWalletLabel(label))
}

sealed interface CreateWalletNews {
    data object WalletCreated : CreateWalletNews
}

sealed interface CreateWalletEvent {
    data class LabelChanged(val label: String) : CreateWalletEvent

    data object SeedPhraseSaved : CreateWalletEvent
}
