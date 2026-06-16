package me.sdi.ethwallet.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.cancel_24px
import me.sdi.ethwallet.resources.eth_logo_120w
import me.sdi.ethwallet.resources.send_address_placeholder
import me.sdi.ethwallet.resources.send_amount
import me.sdi.ethwallet.resources.send_amount_placeholder
import me.sdi.ethwallet.resources.send_clear_address
import me.sdi.ethwallet.resources.send_clear_amount
import me.sdi.ethwallet.resources.send_clear_form
import me.sdi.ethwallet.resources.send_enter_amount
import me.sdi.ethwallet.resources.send_enter_receiver_address
import me.sdi.ethwallet.resources.send_receiver_address
import me.sdi.ethwallet.resources.send_sending
import me.sdi.ethwallet.resources.send_title
import me.sdi.ethwallet.resources.send_token
import me.sdi.ethwallet.resources.send_token_dai
import me.sdi.ethwallet.resources.send_token_eth
import me.sdi.ethwallet.resources.send_token_usdc
import me.sdi.ethwallet.resources.send_token_usdt
import me.sdi.ethwallet.resources.send_transaction
import me.sdi.ethwallet.resources.send_transfer_successful
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    modifier: Modifier = Modifier,
    viewModel: SendViewModel = koinViewModel(),
    onBackPress: () -> Unit,
) {
    val chainConfig: ChainConfig = koinInject()
    val chainTokens = remember(chainConfig.chainId) { supportedKnownTokens(chainConfig.chainId) }
    val viewState by viewModel.viewState.collectAsState()
    val toastManager = rememberToastManager()
    val transferSuccessMessage = stringResource(Res.string.send_transfer_successful)

    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is SendNews.TransferSuccess -> {
                    toastManager.showToast("$transferSuccessMessage ${news.transactionHash}")
                }
                is SendNews.TransferError -> {
                    toastManager.showToast(news.message)
                }
                is SendNews.ValidationError -> {
                    toastManager.showToast(news.message)
                }
                SendNews.NavigateBack -> {
                    // Handle navigation back if needed
                }
            }
        }
    }

    SendScreenContent(
        modifier = modifier,
        viewState = viewState,
        chainTokens = chainTokens,
        onEvent = viewModel::onEvent,
        onBackPress = onBackPress,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendScreenContent(
    modifier: Modifier = Modifier,
    viewState: SendViewState,
    chainTokens: List<KnownToken>,
    onEvent: (SendEvent) -> Unit,
    onBackPress: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.send_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
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
            TokenSelector(
                selectedTokenContract = viewState.selectedTokenContractAddress,
                chainTokens = chainTokens,
                onTokenSelected = { onEvent(SendEvent.OnTokenSelected(it)) },
            )

            ReceiverAddressInput(
                addressState = viewState.receiverAddress,
                onAddressChanged = { onEvent(SendEvent.OnReceiverAddressChanged(it)) },
            )

            AmountInput(
                amountState = viewState.amount,
                onAmountChanged = { onEvent(SendEvent.OnAmountChanged(it)) },
            )

            SendButton(
                enabled = viewState.isTransferEnabled,
                loading = viewState.isLoading,
                onSendClick = { onEvent(SendEvent.OnTransferRequested) },
            )

            if (!viewState.isLoading) {
                Button(
                    onClick = { onEvent(SendEvent.OnClearForm) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.send_clear_form))
                }
            }
        }
    }
}

@Composable
private fun TokenSelector(
    selectedTokenContract: EthAddress,
    chainTokens: List<KnownToken>,
    onTokenSelected: (EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.send_token),
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
private fun ReceiverAddressInput(
    addressState: AddressInputState,
    onAddressChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.send_receiver_address),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = addressState.value,
            onValueChange = onAddressChanged,
            label = { Text(stringResource(Res.string.send_enter_receiver_address)) },
            placeholder = { Text(stringResource(Res.string.send_address_placeholder)) },
            isError = addressState.error != null,
            supportingText = addressState.error?.let { { Text(it) } },
            trailingIcon = {
                if (addressState.value.isNotEmpty()) {
                    IconButton(onClick = { onAddressChanged("") }) {
                        Icon(
                            painter = painterResource(Res.drawable.cancel_24px),
                            contentDescription = stringResource(Res.string.send_clear_address),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
}

@Composable
private fun AmountInput(
    amountState: AmountInputState,
    onAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.send_amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amountState.value,
            onValueChange = onAmountChanged,
            label = { Text(stringResource(Res.string.send_enter_amount)) },
            placeholder = { Text(stringResource(Res.string.send_amount_placeholder)) },
            isError = amountState.error != null,
            supportingText = amountState.error?.let { { Text(it) } },
            trailingIcon = {
                if (amountState.value.isNotEmpty()) {
                    IconButton(onClick = { onAmountChanged("") }) {
                        Icon(
                            painter = painterResource(Res.drawable.cancel_24px),
                            contentDescription = stringResource(Res.string.send_clear_amount),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    loading: Boolean,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onSendClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(Res.string.send_sending))
        } else {
            Text(stringResource(Res.string.send_transaction))
        }
    }
}
