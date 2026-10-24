package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.drag_handle_24px
import me.sdi.ethwallet.resources.tokens_list_reorder
import me.sdi.ethwallet.resources.tokens_list_title
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TokensListScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
) {
    val viewModel = koinViewModel<TokensListViewModel>()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    TokensListScreenContent(
        modifier = modifier,
        onBackPressed = onBackPressed,
        viewState = viewState,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokensListScreenContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewState: TokensListViewState,
    onEvent: (TokensListEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggedTokenKey by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tokens_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            state = listState,
        ) {
            items(
                viewState.tokenListItems,
                key = { it.knownToken.ethAddress.lowercase().toString() },
                contentType = { "regular_list_item" },
            ) { tokenListItem ->
                val key = tokenListItem.knownToken.ethAddress.lowercase().toString()
                val isDragging = draggedTokenKey == key
                val dragState = rememberDraggableState { delta ->
                    if (draggedTokenKey == key) {
                        draggedTop += delta
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        val draggedItem = visibleItems.firstOrNull { it.key == key }
                        if (draggedItem != null) {
                            val center = draggedTop + draggedItem.size / 2f
                            val target = if (delta > 0) {
                                visibleItems.lastOrNull {
                                    it.index > draggedItem.index && center > it.offset + it.size / 2f
                                }
                            } else {
                                visibleItems.firstOrNull {
                                    it.index < draggedItem.index && center < it.offset + it.size / 2f
                                }
                            }
                            if (target != null) {
                                // Keep the viewport fixed when the first visible token changes position.
                                listState.requestScrollToItem(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                )
                                onEvent(
                                    TokensListEvent.OnTokenMoved(
                                        tokenAddress = tokenListItem.knownToken.ethAddress,
                                        toIndex = target.index,
                                    ),
                                )
                            }
                        }
                    }
                }
                TokenListItem(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                draggedTop - (item?.offset ?: 0)
                            } else {
                                0f
                            }
                        }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                if (item != null) {
                                    draggedTop = item.offset.toFloat()
                                    draggedTokenKey = key
                                }
                            },
                            onDragStopped = { draggedTokenKey = null },
                        ),
                    name = tokenListItem.knownToken.tokenName,
                    symbol = tokenListItem.knownToken.symbol,
                    iconRes = tokenListItem.knownToken.iconRes,
                    checked = tokenListItem.showOnMain,
                    onCheckedChange = { newCheckedState ->
                        val newListItemState = tokenListItem.copy(
                            showOnMain = newCheckedState,
                        )
                        onEvent(TokensListEvent.OnTokenVisibilityChanged(newListItemState))
                    },
                )
            }
        }
    }
}

@Composable
private fun TokenListItem(
    name: String,
    symbol: String,
    iconRes: DrawableResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.drag_handle_24px),
                    contentDescription = stringResource(Res.string.tokens_list_reorder, symbol),
                )
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified,
                )
            }
        },
        headlineContent = { Text(text = name) },
        supportingContent = { Text(text = symbol) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
    )
}

@Preview(name = "Light", uiMode = 0x10)
@Preview(name = "Dark", uiMode = 0x20)
@Composable
private fun TokensListScreenContentPreview() {
    EthWalletTheme(dynamicColor = false) {
        TokensListScreenContent(
            onBackPressed = {},
            viewState = TokensListViewState(
                tokenListItems = supportedKnownTokens(ETH_SEPOLIA_CHAIN_ID).mapIndexed { index, token ->
                    TokenListItem(
                        knownToken = token,
                        showOnMain = index != 1,
                    )
                },
            ),
            onEvent = {},
        )
    }
}
