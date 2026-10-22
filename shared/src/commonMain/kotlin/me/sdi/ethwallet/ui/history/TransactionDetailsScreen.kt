package me.sdi.ethwallet.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.history.TransactionFee
import me.sdi.ethwallet.core.history.TransactionStatus
import me.sdi.ethwallet.core.history.Transfer
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.arrow_downward_24px
import me.sdi.ethwallet.resources.arrow_outward_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.copy_all_24px
import me.sdi.ethwallet.resources.history_amount_unavailable
import me.sdi.ethwallet.resources.history_estimated_transaction_fee
import me.sdi.ethwallet.resources.history_failed
import me.sdi.ethwallet.resources.history_mainnet
import me.sdi.ethwallet.resources.history_network
import me.sdi.ethwallet.resources.history_pending
import me.sdi.ethwallet.resources.history_received
import me.sdi.ethwallet.resources.history_self
import me.sdi.ethwallet.resources.history_sent
import me.sdi.ethwallet.resources.history_sepolia
import me.sdi.ethwallet.resources.history_transaction_fee
import me.sdi.ethwallet.resources.transaction_details_addresses
import me.sdi.ethwallet.resources.transaction_details_confirmed
import me.sdi.ethwallet.resources.transaction_details_contract
import me.sdi.ethwallet.resources.transaction_details_copy
import me.sdi.ethwallet.resources.transaction_details_date
import me.sdi.ethwallet.resources.transaction_details_error
import me.sdi.ethwallet.resources.transaction_details_explorer
import me.sdi.ethwallet.resources.transaction_details_from
import me.sdi.ethwallet.resources.transaction_details_hash
import me.sdi.ethwallet.resources.transaction_details_metadata
import me.sdi.ethwallet.resources.transaction_details_network
import me.sdi.ethwallet.resources.transaction_details_title
import me.sdi.ethwallet.resources.transaction_details_to
import me.sdi.ethwallet.resources.transaction_details_token
import me.sdi.ethwallet.resources.transaction_details_unavailable
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.TimeZone
import kotlin.time.Instant

@Composable
fun TransactionDetailsRoute(
    walletAddress: EthAddress,
    transactionHash: String,
    logIndex: Int,
    onBackPressed: () -> Unit,
) {
    val viewModel: TransactionDetailsViewModel = koinViewModel(parameters = { parametersOf(walletAddress, transactionHash, logIndex) })
    val state by viewModel.viewState.collectAsState()
    val chainConfig: ChainConfig = koinInject()
    TransactionDetailsScreen(state, walletAddress, chainConfig, onBackPressed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsScreen(
    state: TransactionDetailsState,
    walletAddress: EthAddress,
    chainConfig: ChainConfig,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transaction_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(painterResource(Res.drawable.arrow_back_24px), stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            when (state) {
                TransactionDetailsState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                TransactionDetailsState.Error -> Text(
                    stringResource(Res.string.transaction_details_error),
                    Modifier.align(Alignment.Center),
                )
                TransactionDetailsState.Unavailable -> Text(
                    stringResource(Res.string.transaction_details_unavailable),
                    Modifier.align(Alignment.Center),
                )
                is TransactionDetailsState.Content -> DetailsContent(state, walletAddress, chainConfig)
            }
        }
    }
}

@Composable
private fun DetailsContent(state: TransactionDetailsState.Content, walletAddress: EthAddress, chainConfig: ChainConfig) {
    val transfer = state.transfer
    val outgoing = transfer.from.toString().equals(walletAddress.toString(), ignoreCase = true)
    val self = transfer.from.toString().equals(transfer.to.toString(), ignoreCase = true)
    val direction = when {
        self -> Res.string.history_self
        outgoing -> Res.string.history_sent
        else -> Res.string.history_received
    }
    val symbol = (transfer as? Transfer.Erc20)?.let {
        it.tokenSymbol?.takeIf(String::isNotBlank) ?: it.tokenAddress.shortHistoryAddress()
    } ?: "ETH"
    val amount = when (transfer) {
        is Transfer.Eth -> formatHistoryAmount(transfer.valueWei.value.toString(), 18)
        is Transfer.Erc20 -> formatHistoryAmount(transfer.value, transfer.tokenDecimals)
    }
    val sign = if (transfer.status != TransactionStatus.Confirmed || self) {
        ""
    } else if (outgoing) {
        "-"
    } else {
        "+"
    }
    val status = when (transfer.status) {
        TransactionStatus.Confirmed -> Res.string.transaction_details_confirmed
        TransactionStatus.Pending -> Res.string.history_pending
        TransactionStatus.Failed -> Res.string.history_failed
    }
    val statusColor = if (transfer.status == TransactionStatus.Failed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (outgoing) Res.drawable.arrow_outward_24px else Res.drawable.arrow_downward_24px),
                    null,
                    Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                stringResource(direction),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                amount?.let {
                    "$sign$it $symbol"
                } ?: stringResource(Res.string.history_amount_unavailable, symbol),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(stringResource(status), style = MaterialTheme.typography.titleMedium, color = statusColor)
        }
        DetailsCard {
            DetailValue(
                stringResource(Res.string.transaction_details_network),
                when (chainConfig.chainId) {
                    1L -> stringResource(Res.string.history_mainnet)
                    11155111L -> stringResource(Res.string.history_sepolia)
                    else -> stringResource(Res.string.history_network, chainConfig.chainId)
                },
            )
            HorizontalDivider()
            val timeZone = TimeZone.getDefault()
            DetailValue(
                stringResource(Res.string.transaction_details_date),
                "${formatHistoryDate(transfer.timestamp, timeZone)}, ${formatHistoryTime(transfer.timestamp, timeZone)}",
            )
            transfer.fee?.let { fee ->
                HorizontalDivider()
                Text(
                    stringResource(
                        if (fee.estimated) Res.string.history_estimated_transaction_fee else Res.string.history_transaction_fee,
                        formatHistoryAmount(fee.value.value.toString(), 18).orEmpty(),
                    ),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(stringResource(Res.string.transaction_details_addresses), style = MaterialTheme.typography.titleMedium)
        DetailsCard {
            CopyValue(
                stringResource(Res.string.transaction_details_from),
                transfer.from.toString(),
                state.fromWalletLabel,
            )
            HorizontalDivider()
            CopyValue(
                stringResource(Res.string.transaction_details_to),
                transfer.to.toString(),
                state.toWalletLabel,
            )
        }
        DetailsCard { CopyValue(stringResource(Res.string.transaction_details_hash), transfer.hash) }
        if (transfer is Transfer.Erc20) {
            Text(stringResource(Res.string.transaction_details_token), style = MaterialTheme.typography.titleMedium)
            DetailsCard {
                CopyValue(stringResource(Res.string.transaction_details_contract), transfer.tokenAddress.toString())
                Text(
                    stringResource(Res.string.transaction_details_metadata, transfer.tokenDecimals, transfer.logIndex),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        chainConfig.explorerBaseUrl?.takeIf(String::isNotBlank)?.let { baseUrl ->
            Button(
                onClick = { uriHandler.openUri("${baseUrl.trimEnd('/')}/tx/${transfer.hash}") },
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(Res.string.transaction_details_explorer))
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(Res.drawable.arrow_outward_24px), null)
            }
        }
    }
}

@Composable
private fun DetailsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(content = content)
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CopyValue(label: String, value: String, walletLabel: String? = null) {
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                walletLabel?.let { "$label · $it" } ?: label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
            Icon(painterResource(Res.drawable.copy_all_24px), stringResource(Res.string.transaction_details_copy, label))
        }
    }
}

private val detailsPreviewWallet = EthAddress("0x${"1".repeat(40)}")

internal class TransactionDetailsPreviewProvider : PreviewParameterProvider<TransactionDetailsState> {
    private val tokenTransfer = Transfer.Erc20(
        hash = "0x${"a".repeat(64)}",
        timestamp = Instant.parse("2026-10-22T07:32:00Z").toEpochMilliseconds(),
        from = detailsPreviewWallet,
        to = EthAddress("0x${"2".repeat(40)}"),
        logIndex = 12,
        tokenAddress = EthAddress("0x${"3".repeat(40)}"),
        tokenSymbol = "USDC",
        tokenDecimals = 6,
        value = "250000000",
        fee = TransactionFee("420000000000000".toBigInteger().toWei(), estimated = false),
    )

    override val values: Sequence<TransactionDetailsState> = sequenceOf(
        TransactionDetailsState.Content(tokenTransfer, fromWalletLabel = "Main wallet", toWalletLabel = "Savings"),
        TransactionDetailsState.Content(
            Transfer.Eth(
                hash = tokenTransfer.hash,
                timestamp = tokenTransfer.timestamp,
                from = tokenTransfer.to,
                to = detailsPreviewWallet,
                valueWei = "15000000000000000".toBigInteger().toWei(),
                status = TransactionStatus.Confirmed,
            ),
        ),
        TransactionDetailsState.Content(
            tokenTransfer.copy(
                status = TransactionStatus.Pending,
                fee = tokenTransfer.fee?.copy(estimated = true),
            ),
        ),
        TransactionDetailsState.Content(tokenTransfer.copy(status = TransactionStatus.Failed)),
        TransactionDetailsState.Loading,
        TransactionDetailsState.Unavailable,
        TransactionDetailsState.Error,
    )
}

@Composable
@FormFactorPreviews
private fun TransactionDetailsScreenPreview(
    @PreviewParameter(TransactionDetailsPreviewProvider::class) state: TransactionDetailsState,
) {
    EthWalletTheme {
        TransactionDetailsScreen(
            state = state,
            walletAddress = detailsPreviewWallet,
            chainConfig = ChainConfig(
                rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
                chainId = 11155111L,
                explorerBaseUrl = "https://sepolia.etherscan.io",
                transactionHistoryApiBaseUrl = "https://eth-sepolia.blockscout.com",
            ),
            onBackPressed = {},
        )
    }
}
