package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.data.repository.TokenPreferenceRepository
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens

private const val TAG = "TokensListViewModel"

class TokensListViewModel(
    private val walletAddress: EthAddress,
    private val chainConfig: ChainConfig,
    private val tokenPreferenceRepository: TokenPreferenceRepository,
) : ViewModel() {
    private val state = MutableStateFlow(TokensListViewState(tokenListItems = emptyList()))

    val viewState: StateFlow<TokensListViewState> = state
        .onStart {
            loadTokenListItems()
            viewModelScope.launch { savePendingPreferences() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, state.value)

    private val newsChannel = Channel<TokensListNews>(Channel.BUFFERED)
    val news: Flow<TokensListNews> = newsChannel.receiveAsFlow()

    private val pendingPreferences = Channel<List<TokenPreference>>(Channel.CONFLATED)

    fun onEvent(event: TokensListEvent) {
        Logger.debug(TAG) { "onEvent: $event" }
        when (event) {
            is TokensListEvent.OnTokenVisibilityChanged -> onTokenVisibilityOnMainChanged(event.newListItemState)
            is TokensListEvent.OnTokenMoved -> onTokenMoved(event.tokenAddress, event.toIndex)
            TokensListEvent.OnTokenMoveFinished -> persistTokenListItems()
        }
    }

    private suspend fun loadTokenListItems() {
        try {
            val preferences = tokenPreferenceRepository.observe(walletAddress).first()
            val items = tokenListItems(supportedKnownTokens(chainConfig.chainId), preferences)
            state.update { it.copy(tokenListItems = items) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Failed to load token preferences" }
            newsChannel.send(TokensListNews.ShowError)
        }
    }

    private fun onTokenMoved(tokenAddress: EthAddress, toIndex: Int) {
        state.update { state ->
            val items = state.tokenListItems
            val fromIndex = items.indexOfFirst { it.knownToken.ethAddress == tokenAddress }
            if (fromIndex == -1 || toIndex !in items.indices || fromIndex == toIndex) {
                state
            } else {
                val reorderedItems = items.toMutableList()
                reorderedItems.add(toIndex, reorderedItems.removeAt(fromIndex))
                state.copy(tokenListItems = reorderedItems)
            }
        }
    }

    private fun onTokenVisibilityOnMainChanged(newState: TokenListItem) {
        state.update { state ->
            state.copy(
                tokenListItems = state.tokenListItems.map {
                    if (it.knownToken.ethAddress == newState.knownToken.ethAddress) newState else it
                },
            )
        }
        persistTokenListItems()
    }

    private fun persistTokenListItems() {
        val preferences = state.value.tokenListItems.map {
            TokenPreference(tokenAddress = it.knownToken.ethAddress, visible = it.showOnMain)
        }
        pendingPreferences.trySend(preferences)
    }

    private suspend fun savePendingPreferences() {
        for (preferences in pendingPreferences) {
            try {
                // A save that already started must finish when the user leaves the screen right after a change.
                withContext(NonCancellable) {
                    tokenPreferenceRepository.update(walletAddress, preferences)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn(TAG, e) { "Failed to save token preferences" }
                newsChannel.send(TokensListNews.ShowError)
            }
        }
    }
}

sealed interface TokensListEvent {
    data class OnTokenMoved(
        val tokenAddress: EthAddress,
        val toIndex: Int,
    ) : TokensListEvent

    data object OnTokenMoveFinished : TokensListEvent

    data class OnTokenVisibilityChanged(
        val newListItemState: TokenListItem,
    ) : TokensListEvent
}

sealed interface TokensListNews {
    data object ShowError : TokensListNews
}

data class TokenListItem(
    val knownToken: KnownToken,
    val showOnMain: Boolean,
)

/**
 * @param tokenListItems -- list of known tokens with visibility on main flag. Tokens order may be changed
 */
data class TokensListViewState(
    val tokenListItems: List<TokenListItem>,
)
