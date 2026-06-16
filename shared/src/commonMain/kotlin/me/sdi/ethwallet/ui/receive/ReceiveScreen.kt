package me.sdi.ethwallet.ui.receive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.cancel_24px
import me.sdi.ethwallet.resources.copy_all_24px
import me.sdi.ethwallet.resources.eth_logo_120w
import me.sdi.ethwallet.resources.receive_address_copied
import me.sdi.ethwallet.resources.receive_amount_optional
import me.sdi.ethwallet.resources.receive_copy_address
import me.sdi.ethwallet.resources.receive_currency
import me.sdi.ethwallet.resources.receive_select_currency
import me.sdi.ethwallet.resources.receive_title
import me.sdi.ethwallet.resources.send_enter_amount
import me.sdi.ethwallet.resources.send_token_dai
import me.sdi.ethwallet.resources.send_token_eth
import me.sdi.ethwallet.resources.send_token_usdc
import me.sdi.ethwallet.resources.send_token_usdt
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiveQrViewModel = koinViewModel(),
) {
    val chainConfig: ChainConfig = koinInject()
    val chainTokens = remember(chainConfig.chainId) { supportedKnownTokens(chainConfig.chainId) }
    val viewState by viewModel.viewState.collectAsState()
    val toastManager = rememberToastManager()
    val clipboardManager = LocalClipboardManager.current
    val addressCopiedMessage = stringResource(Res.string.receive_address_copied)

    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is ReceiveQrNews.ShowToast -> {
                    toastManager.showToast(news.message)
                }
                is ReceiveQrNews.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(news.text))
                    toastManager.showToast(addressCopiedMessage)
                }
            }
        }
    }

    ReceiveScreenContent(
        modifier = modifier,
        viewState = viewState,
        chainTokens = chainTokens,
        onEvent = { event ->
            when (event) {
                ReceiveQrEvent.OnBackPressed -> onBackPressed()
                else -> viewModel.onEvent(event)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveScreenContent(
    modifier: Modifier = Modifier,
    viewState: ReceiveQrViewState,
    chainTokens: List<KnownToken>,
    onEvent: (event: ReceiveQrEvent) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.receive_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ReceiveQrEvent.OnBackPressed) }) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CurrencyPicker(
                selectedTokenContract = viewState.selectedTokenContract,
                chainTokens = chainTokens,
                onTokenSelected = { onEvent(ReceiveQrEvent.OnTokenSelected(it)) },
            )

            AmountInput(
                amount = viewState.amount,
                amountError = viewState.amountError,
                onAmountChanged = { onEvent(ReceiveQrEvent.OnAmountChanged(it)) },
                onClearAmount = { onEvent(ReceiveQrEvent.OnClearAmount) },
            )

            QrCodeDisplay(
                qrPayload = viewState.qrPayload,
                walletAddress = viewState.walletAddress,
                onCopyAddress = { onEvent(ReceiveQrEvent.OnCopyAddress) },
            )
        }
    }
}

@Composable
private fun CurrencyPicker(
    selectedTokenContract: EthAddress,
    chainTokens: List<KnownToken>,
    onTokenSelected: (EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.receive_currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ETH Chip
            FilterChip(
                selected = selectedTokenContract == EthContractAddress,
                onClick = { onTokenSelected(EthContractAddress) },
                label = { Text(stringResource(Res.string.send_token_eth)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.eth_logo_120w),
                        tint = Color.Unspecified,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // ERC20 Token Chips
            chainTokens.forEach { token ->
                FilterChip(
                    selected = selectedTokenContract == token.ethAddress,
                    onClick = { onTokenSelected(token.ethAddress) },
                    label = {
                        val nameRes = when (token.symbol) {
                            "USDT" -> Res.string.send_token_usdt
                            "USDC" -> Res.string.send_token_usdc
                            "DAI" -> Res.string.send_token_dai
                            else -> null
                        }
                        Text(nameRes?.let { stringResource(it) } ?: token.symbol)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(token.iconRes),
                            tint = Color.Unspecified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AmountInput(
    amount: String,
    amountError: String?,
    onAmountChanged: (String) -> Unit,
    onClearAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.receive_amount_optional),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChanged,
            label = { Text(stringResource(Res.string.send_enter_amount)) },
            isError = amountError != null,
            supportingText = amountError?.let { { Text(it) } },
            trailingIcon = {
                AnimatedVisibility(
                    amount.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    IconButton(
                        onClick = onClearAmount,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.cancel_24px),
                            contentDescription = null,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QrCodeDisplay(
    qrPayload: String?,
    walletAddress: EthAddress?,
    onCopyAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                qrPayload != null -> {
                    QrCodeCanvas(
                        qrData = qrPayload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.receive_select_currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Address display with copy functionality
        walletAddress?.let { address ->
            Spacer(modifier = Modifier.height(16.dp))
            AddressDisplay(
                address = address,
                onCopyAddress = onCopyAddress,
            )
        }
    }
}

@Composable
private fun AddressDisplay(
    address: EthAddress,
    onCopyAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addressString = address.toString()
    val truncatedAddress = "${addressString.take(6)}...${addressString.takeLast(4)}"

    val annotatedText = buildAnnotatedString {
        append(truncatedAddress)
        append(" ")
        appendInlineContent("copyIcon", "[copy]")
    }

    val inlineContent = mapOf(
        "copyIcon" to InlineTextContent(
            placeholder = Placeholder(
                width = MaterialTheme.typography.bodyMedium.fontSize,
                height = MaterialTheme.typography.bodyMedium.fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Icon(
                painter = painterResource(Res.drawable.copy_all_24px),
                contentDescription = stringResource(Res.string.receive_copy_address),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        },
    )

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCopyAddress() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
