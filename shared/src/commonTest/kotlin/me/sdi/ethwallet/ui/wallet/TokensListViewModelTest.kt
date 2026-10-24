package me.sdi.ethwallet.ui.wallet

import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
import kotlin.test.Test
import kotlin.test.assertEquals

class TokensListViewModelTest {
    private val viewModel = TokensListViewModel(
        ChainConfig(
            rpcUrl = "https://rpc.example",
            chainId = ETH_SEPOLIA_CHAIN_ID,
            explorerBaseUrl = null,
            transactionHistoryApiBaseUrl = "https://history.example",
        ),
    )

    @Test
    fun moving_first_token_to_last_updates_list_order_without_mutating_previous_state() {
        val initialItems = viewModel.viewState.value.tokenListItems

        viewModel.onEvent(TokensListEvent.OnTokenMoved(initialItems[0].knownToken.ethAddress, 2))

        assertEquals(listOf(initialItems[1], initialItems[2], initialItems[0]), viewModel.viewState.value.tokenListItems)
        assertEquals(listOf("USDT", "USDC", "DAI"), initialItems.map { it.knownToken.symbol })
    }

    @Test
    fun moving_last_token_to_first_and_repeating_the_move_keeps_requested_order() {
        val initialItems = viewModel.viewState.value.tokenListItems
        val event = TokensListEvent.OnTokenMoved(initialItems[2].knownToken.ethAddress, 0)
        val expected = listOf(initialItems[2], initialItems[0], initialItems[1])

        viewModel.onEvent(event)
        assertEquals(expected, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(event)
        assertEquals(expected, viewModel.viewState.value.tokenListItems)
    }

    @Test
    fun moving_tokens_preserves_visibility_and_visibility_changes_preserve_order() {
        val initialItems = viewModel.viewState.value.tokenListItems
        val hiddenToken = initialItems[0].copy(showOnMain = false)
        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(hiddenToken))

        viewModel.onEvent(TokensListEvent.OnTokenMoved(hiddenToken.knownToken.ethAddress, 2))

        assertEquals(listOf(initialItems[1], initialItems[2], hiddenToken), viewModel.viewState.value.tokenListItems)

        val secondHiddenToken = initialItems[1].copy(showOnMain = false)
        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(secondHiddenToken))

        assertEquals(listOf(secondHiddenToken, initialItems[2], hiddenToken), viewModel.viewState.value.tokenListItems)
    }

    @Test
    fun unknown_token_and_out_of_range_positions_leave_list_unchanged() {
        val initialItems = viewModel.viewState.value.tokenListItems
        val tokenAddress = initialItems[0].knownToken.ethAddress

        viewModel.onEvent(TokensListEvent.OnTokenMoved(tokenAddress, -1))
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(TokensListEvent.OnTokenMoved(tokenAddress, initialItems.size))
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(
            TokensListEvent.OnTokenMoved(EthAddress("0x0000000000000000000000000000000000000001"), 1),
        )
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)
    }
}
