package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens

class TokensListViewModel(chainConfig: ChainConfig) : ViewModel() {
    private val _viewState = MutableStateFlow(
        TokensListViewState(
            tokenListItems = supportedKnownTokens(chainConfig.chainId)
                .mapIndexed { index, token ->
                    TokenListItem(
                        knownToken = token,
                        showOnMain = true,
                    )
                },
        ),
    )
    val viewState: StateFlow<TokensListViewState> = _viewState.asStateFlow()

    fun onEvent(event: TokensListEvent) {
        when (event) {
            is TokensListEvent.OnTokenVisibilityChanged -> onTokenVisibilityOnMainChanged(event.newListItemState)
            is TokensListEvent.OnTokenMoved -> onTokenMoved(event.tokenAddress, event.toIndex)
        }
    }

    private fun onTokenMoved(tokenAddress: EthAddress, toIndex: Int) {
        _viewState.update { state ->
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
        val knownTokensWithUpdatedItem = _viewState
            .value
            .tokenListItems
            .map {
                if (it.knownToken.ethAddress == newState.knownToken.ethAddress) {
                    newState
                } else {
                    it
                }
            }

        _viewState.update { it.copy(tokenListItems = knownTokensWithUpdatedItem) }
    }
}

sealed interface TokensListEvent {
    data class OnTokenMoved(
        val tokenAddress: EthAddress,
        val toIndex: Int,
    ) : TokensListEvent

    data class OnTokenVisibilityChanged(
        val newListItemState: TokenListItem,
    ) : TokensListEvent
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
