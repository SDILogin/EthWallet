package me.sdi.ethwallet.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.chevron_right_24px
import me.sdi.ethwallet.resources.history_24px
import me.sdi.ethwallet.resources.history_mainnet
import me.sdi.ethwallet.resources.history_sepolia
import me.sdi.ethwallet.resources.history_title
import me.sdi.ethwallet.resources.token_details_contract
import me.sdi.ethwallet.resources.token_details_decimals
import me.sdi.ethwallet.resources.token_details_history_description
import me.sdi.ethwallet.resources.token_details_information
import me.sdi.ethwallet.resources.token_details_network
import me.sdi.ethwallet.resources.token_details_standard
import me.sdi.ethwallet.resources.token_details_unavailable
import me.sdi.ethwallet.resources.wallet_receive
import me.sdi.ethwallet.resources.wallet_send
import me.sdi.ethwallet.ui.ETH_MAINNET_CHAIN_ID
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.usdcOnMainnet
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TokenDetailsScreen(
    walletAddress: EthAddress,
    tokenAddress: EthAddress,
    onUpButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    onReceiveButtonClick: () -> Unit,
    onHistoryButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<TokenDetailsViewModel>(
        parameters = { parametersOf(walletAddress, tokenAddress) },
    )
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    TokenDetailsScreenContent(
        modifier = modifier,
        onUpButtonClick = onUpButtonClick,
        onSendButtonClick = onSendButtonClick,
        onReceiveButtonClick = onReceiveButtonClick,
        onHistoryButtonClick = onHistoryButtonClick,
        viewState = viewState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailsScreenContent(
    onUpButtonClick: () -> Unit,
    onSendButtonClick: () -> Unit,
    onReceiveButtonClick: () -> Unit,
    onHistoryButtonClick: () -> Unit,
    viewState: TokenDetailsViewState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(viewState.name) },
                navigationIcon = {
                    IconButton(onClick = onUpButtonClick) {
                        Icon(painterResource(Res.drawable.arrow_back_24px), stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                painter = painterResource(viewState.icon),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.Unspecified,
            )
            Text(viewState.symbol, style = MaterialTheme.typography.titleLarge)
            Text(
                text = viewState.balance?.let { "$it ${viewState.symbol}" }
                    ?: stringResource(Res.string.token_details_unavailable),
                style = MaterialTheme.typography.headlineLarge,
            )
            Row(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onSendButtonClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.wallet_send))
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onReceiveButtonClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.wallet_receive))
                }
            }
            Card(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(Res.string.token_details_information),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TokenInformationRow(
                        stringResource(Res.string.token_details_network),
                        when (viewState.chainId) {
                            ETH_MAINNET_CHAIN_ID -> stringResource(Res.string.history_mainnet)
                            ETH_SEPOLIA_CHAIN_ID -> stringResource(Res.string.history_sepolia)
                            else -> viewState.chainId.toString()
                        },
                    )
                    TokenInformationRow(stringResource(Res.string.token_details_standard), "ERC-20")
                    TokenInformationRow(
                        stringResource(Res.string.token_details_contract),
                        viewState.address,
                    )
                    TokenInformationRow(
                        stringResource(Res.string.token_details_decimals),
                        viewState.decimals.toString(),
                    )
                }
            }
            Card(
                onClick = onHistoryButtonClick,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.history_24px),
                        contentDescription = null,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(12.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(Res.string.history_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.token_details_history_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painter = painterResource(Res.drawable.chevron_right_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenInformationRow(label: String, value: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(value)
    }
}

@PreviewScreenSizes
@Composable
private fun TokenDetailsScreenContentPreview() {
    EthWalletTheme(dynamicColor = false) {
        TokenDetailsScreenContent(
            onUpButtonClick = {},
            onSendButtonClick = {},
            onReceiveButtonClick = {},
            onHistoryButtonClick = {},
            viewState = usdcOnMainnet.toDetailsViewState(ETH_MAINNET_CHAIN_ID).copy(balance = "500.00"),
        )
    }
}
