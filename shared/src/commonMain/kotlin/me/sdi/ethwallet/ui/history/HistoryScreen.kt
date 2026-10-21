package me.sdi.ethwallet.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.kotlin.TransactionHash
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.arrow_downward_24px
import me.sdi.ethwallet.resources.arrow_outward_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.cancel_24px
import me.sdi.ethwallet.resources.history_24px
import me.sdi.ethwallet.resources.history_amount_unavailable
import me.sdi.ethwallet.resources.history_empty_message
import me.sdi.ethwallet.resources.history_empty_title
import me.sdi.ethwallet.resources.history_end
import me.sdi.ethwallet.resources.history_estimated_transaction_fee
import me.sdi.ethwallet.resources.history_failed
import me.sdi.ethwallet.resources.history_from
import me.sdi.ethwallet.resources.history_load_more_error
import me.sdi.ethwallet.resources.history_loading
import me.sdi.ethwallet.resources.history_mainnet
import me.sdi.ethwallet.resources.history_network
import me.sdi.ethwallet.resources.history_new_contract
import me.sdi.ethwallet.resources.history_pending
import me.sdi.ethwallet.resources.history_receive_failed
import me.sdi.ethwallet.resources.history_received
import me.sdi.ethwallet.resources.history_receiving
import me.sdi.ethwallet.resources.history_refresh_error
import me.sdi.ethwallet.resources.history_self
import me.sdi.ethwallet.resources.history_send_failed
import me.sdi.ethwallet.resources.history_sending
import me.sdi.ethwallet.resources.history_sent
import me.sdi.ethwallet.resources.history_sepolia
import me.sdi.ethwallet.resources.history_this_wallet
import me.sdi.ethwallet.resources.history_title
import me.sdi.ethwallet.resources.history_to
import me.sdi.ethwallet.resources.history_today
import me.sdi.ethwallet.resources.history_transaction_fee
import me.sdi.ethwallet.resources.history_yesterday
import me.sdi.ethwallet.resources.switch_account_24px
import me.sdi.ethwallet.resources.wallet_retry
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.components.shimmer
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Instant

@Composable
fun HistoryScreen(
    modifier: Modifier,
    onHistoryItemClick: (transactionHash: TransactionHash, logIndex: Int) -> Unit,
    walletLabel: String,
    chainId: Long,
    viewState: HistoryViewState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HistoryHeader(chainId = chainId, walletLabel = walletLabel, onBackPressed = onBackPressed) },
    ) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = Modifier.widthIn(max = 720.dp).fillMaxSize()
            when (viewState) {
                is HistoryViewState.Error -> {
                    Column(
                        modifier = contentModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(Res.string.history_refresh_error))
                        Button(onClick = onRetry) { Text(stringResource(Res.string.wallet_retry)) }
                    }
                }
                is HistoryViewState.Loading -> ShimmersSkeleton(modifier = contentModifier)
                is HistoryViewState.Content -> {
                    RefreshableContent(
                        modifier = contentModifier,
                        rows = viewState.items,
                        isRefreshing = viewState.refreshing,
                        onRefresh = onRefresh,
                        pagination = viewState.pagination,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                        onHistoryItemClick = onHistoryItemClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryHeader(
    walletLabel: String,
    chainId: Long,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val networkName = when (chainId) {
        1L -> stringResource(Res.string.history_mainnet)
        11155111L -> stringResource(Res.string.history_sepolia)
        else -> stringResource(Res.string.history_network, chainId)
    }
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(painterResource(Res.drawable.arrow_back_24px), stringResource(Res.string.back))
            }
        },
        title = {
            Column {
                Text(stringResource(Res.string.history_title))
                Text(
                    text = "$walletLabel · $networkName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun ShimmersSkeleton(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(Res.string.history_loading)
    LazyColumn(
        modifier = modifier.semantics { contentDescription = loadingDescription },
        contentPadding = PaddingValues(16.dp),
        userScrollEnabled = false,
    ) {
        items(8) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .shimmer(),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(Modifier.fillMaxWidth(0.55f))
                    SkeletonLine(Modifier.fillMaxWidth(0.8f))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(Modifier.width(72.dp))
                    SkeletonLine(Modifier.width(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(modifier: Modifier) {
    Spacer(
        modifier.height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .shimmer(),
    )
}

@Composable
private fun RefreshableContent(
    rows: List<HistoryRow>,
    isRefreshing: Boolean,
    pagination: HistoryPagination,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onHistoryItemClick: (transactionHash: TransactionHash, logIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(Res.string.history_loading)
    val listState = rememberLazyListState()
    LaunchedEffect(listState, pagination, isRefreshing, rows.size) {
        if (pagination !is HistoryPagination.Idle) return@LaunchedEffect
        if (!pagination.hasMore) return@LaunchedEffect
        if (isRefreshing) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            rows.isEmpty() || (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 1
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    val timeZone = TimeZone.getDefault()
    val groups = remember(rows, timeZone.id) {
        rows.groupBy { row ->
            if (row.status == TransactionStatus.Pending) {
                "pending"
            } else {
                historyDateKey(row.timestampMillis, timeZone)
            }
        }
    }
    val today = Calendar.getInstance(timeZone)
    val todayKey = historyDateKey(today.timeInMillis, timeZone)
    today.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayKey = historyDateKey(today.timeInMillis, timeZone)

    PullToRefreshBox(modifier = modifier, isRefreshing = isRefreshing, onRefresh = onRefresh) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            if (rows.isEmpty() && !pagination.hasMore) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(Res.drawable.history_24px),
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(Res.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.history_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                groups.forEach { (key, group) ->
                    item(key = "header:$key", contentType = "header") {
                        val title = when (key) {
                            "pending" -> stringResource(Res.string.history_pending)
                            todayKey -> stringResource(Res.string.history_today)
                            yesterdayKey -> stringResource(Res.string.history_yesterday)
                            else -> formatHistoryDate(group.first().timestampMillis, timeZone)
                        }
                        Text(
                            title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(group, key = { it.key }, contentType = { "history" }) { row ->
                        HistoryListItem(row, timeZone, onHistoryItemClick)
                    }
                }
            }
            item(key = if (rows.isEmpty()) "empty-footer" else "footer") {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (pagination) {
                        HistoryPagination.Uninitialized -> Unit
                        is HistoryPagination.Loading -> {
                            CircularProgressIndicator(
                                Modifier.size(24.dp).semantics {
                                    contentDescription = loadingDescription
                                },
                            )
                        }
                        is HistoryPagination.Failed -> {
                            Text(stringResource(Res.string.history_load_more_error), textAlign = TextAlign.Center)
                            Button(onClick = onRetryLoadMore) { Text(stringResource(Res.string.wallet_retry)) }
                        }
                        is HistoryPagination.Idle -> {
                            if (!pagination.hasMore && rows.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.history_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    row: HistoryRow,
    timeZone: TimeZone,
    onHistoryItemClick: (transactionHash: TransactionHash, logIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        when (row.action) {
            TransferDirection.Sent -> when (row.status) {
                TransactionStatus.Pending -> Res.string.history_sending
                TransactionStatus.Failed -> Res.string.history_send_failed
                TransactionStatus.Confirmed -> Res.string.history_sent
            }
            TransferDirection.Received -> when (row.status) {
                TransactionStatus.Pending -> Res.string.history_receiving
                TransactionStatus.Failed -> Res.string.history_receive_failed
                TransactionStatus.Confirmed -> Res.string.history_received
            }
            TransferDirection.Self -> Res.string.history_self
        },
    )
    val counterparty = when {
        row.item.from.toString()
            .equals(row.item.to.toString(), ignoreCase = true) -> stringResource(Res.string.history_this_wallet)
        row.counterparty == null -> stringResource(Res.string.history_new_contract)
        else -> stringResource(
            if (row.counterparty == row.item.from) Res.string.history_from else Res.string.history_to,
            row.counterparty.shortHistoryAddress(),
        )
    }
    val statusColor = when (row.status) {
        TransactionStatus.Failed -> MaterialTheme.colorScheme.error
        TransactionStatus.Pending -> MaterialTheme.colorScheme.primary
        TransactionStatus.Confirmed -> MaterialTheme.colorScheme.onSurface
    }
    val time = formatHistoryTime(row.timestampMillis, timeZone)
    val subtitle = when (row.status) {
        TransactionStatus.Pending -> stringResource(Res.string.history_pending)
        TransactionStatus.Failed -> listOfNotNull(stringResource(Res.string.history_failed), time).joinToString(" · ")
        TransactionStatus.Confirmed -> time
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button) {
                    onHistoryItemClick(
                        TransactionHash(row.item.hash),
                        row.logIndex,
                    )
                }
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HistoryItemIcon(row)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = statusColor)
                Text(
                    counterparty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.amount?.let { "$it ${row.symbol}" }
                        ?: stringResource(Res.string.history_amount_unavailable, row.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.action == TransferDirection.Received && row.status == TransactionStatus.Confirmed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                row.transactionFee?.let { fee ->
                    Text(
                        stringResource(
                            if (row.feeEstimated) Res.string.history_estimated_transaction_fee else Res.string.history_transaction_fee,
                            fee,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.status == TransactionStatus.Confirmed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        statusColor
                    },
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 68.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun HistoryItemIcon(row: HistoryRow) {
    val icon = when {
        row.status == TransactionStatus.Failed -> Res.drawable.cancel_24px
        row.status == TransactionStatus.Pending -> Res.drawable.history_24px
        row.action == TransferDirection.Received -> Res.drawable.arrow_downward_24px
        row.action == TransferDirection.Self -> Res.drawable.switch_account_24px
        else -> Res.drawable.arrow_outward_24px
    }
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(24.dp),
            tint = if (row.status == TransactionStatus.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val previewWallet = EthAddress("0x1111111111111111111111111111111111111111")

internal class HistoryPreviewProvider : PreviewParameterProvider<List<Transfer>?> {
    override val values: Sequence<List<Transfer>?> = sequenceOf(
        null,
        emptyList(),
        listOf(
            Transfer.Eth(
                "0x${"a".repeat(64)}",
                Instant.parse("2026-09-07T08:00:00Z").toEpochMilliseconds(),
                previewWallet,
                EthAddress("0x2222222222222222222222222222222222222222"),
                BigInteger("15000000000000000").toWei(),
                TransactionStatus.Pending,
            ),
            Transfer.Erc20(
                "0x${"b".repeat(64)}",
                Instant.parse("2026-09-07T07:32:00Z").toEpochMilliseconds(),
                EthAddress("0x2222222222222222222222222222222222222222"),
                previewWallet,
                0,
                EthAddress("0x3333333333333333333333333333333333333333"),
                "USDC",
                6,
                "250000000",
            ),
            Transfer.Eth(
                "0x${"c".repeat(64)}",
                Instant.parse("2026-09-07T06:05:00Z").toEpochMilliseconds(),
                previewWallet,
                EthAddress("0x2222222222222222222222222222222222222222"),
                BigInteger("42000000000000000").toWei(),
                TransactionStatus.Confirmed,
            ),
            Transfer.Eth(
                "0x${"d".repeat(64)}",
                Instant.parse("2026-09-07T05:15:00Z").toEpochMilliseconds(),
                previewWallet,
                previewWallet,
                BigInteger("10000000000000000").toWei(),
                TransactionStatus.Confirmed,
            ),
            Transfer.Eth(
                "0x${"e".repeat(64)}",
                Instant.parse("2026-09-06T11:42:00Z").toEpochMilliseconds(),
                previewWallet,
                EthAddress("0x2222222222222222222222222222222222222222"),
                BigInteger("100000000000000000").toWei(),
                TransactionStatus.Failed,
            ),
        ),
    )
}

@Composable
@FormFactorPreviews
private fun HistoryScreenPreview(
    @PreviewParameter(HistoryPreviewProvider::class) items: List<Transfer>?,
) {
    EthWalletTheme {
        HistoryScreen(
            modifier = Modifier.fillMaxSize(),
            onHistoryItemClick = { _, _ -> },
            walletLabel = "My Wallet",
            chainId = 11155111L,
            viewState = items?.let {
                HistoryViewState.Content(
                    it.mapNotNull { item ->
                        item.toHistoryRow(previewWallet)
                    },
                    false,
                )
            } ?: HistoryViewState.Loading,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onRetryLoadMore = {},
            onBackPressed = {},
        )
    }
}
