package me.sdi.ethwallet.ui.send

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.ui.icon
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    modifier: Modifier = Modifier,
    viewModel: SendViewModel = koinViewModel(),
    onBackPress: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is SendNews.TransferSuccess -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.send_transfer_successful, news.transactionHash),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is SendNews.TransferError -> {
                    Toast.makeText(context, news.message, Toast.LENGTH_LONG).show()
                }
                is SendNews.ValidationError -> {
                    Toast.makeText(context, news.message, Toast.LENGTH_SHORT).show()
                }
                SendNews.NavigateBack -> {
                    // Handle navigation back if needed
                }
            }
        }
    }

    SendScreen(
        modifier = modifier,
        viewState = viewState,
        onEvent = viewModel::onEvent,
        onBackPress = onBackPress,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendScreen(
    modifier: Modifier = Modifier,
    viewState: SendViewState,
    onEvent: (SendEvent) -> Unit,
    onBackPress: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.send_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
                    Text(stringResource(R.string.send_clear_form))
                }
            }
        }
    }
}

@Composable
private fun TokenSelector(
    selectedTokenContract: EthAddress,
    onTokenSelected: (EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.send_token),
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
                label = { Text(stringResource(R.string.send_token_eth)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.eth_logo_120w),
                        tint = Color.Unspecified,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // ERC20 Token Chips
            KnownTokens.entries.forEach { token ->
                FilterChip(
                    selected = selectedTokenContract == token.ethAddress,
                    onClick = { onTokenSelected(token.ethAddress) },
                    label = {
                        val nameRes = when (token) {
                            KnownTokens.USDT -> R.string.send_token_usdt
                            KnownTokens.USDC -> R.string.send_token_usdc
                            KnownTokens.DAI -> R.string.send_token_dai
                        }
                        Text(stringResource(nameRes))
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = token.icon),
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
            text = stringResource(R.string.send_receiver_address),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = addressState.value,
            onValueChange = onAddressChanged,
            label = { Text(stringResource(R.string.send_enter_receiver_address)) },
            placeholder = { Text(stringResource(R.string.send_address_placeholder)) },
            isError = addressState.error != null,
            supportingText = addressState.error?.let { { Text(it) } },
            trailingIcon = {
                if (addressState.value.isNotEmpty()) {
                    IconButton(onClick = { onAddressChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.send_clear_address),
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
            text = stringResource(R.string.send_amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amountState.value,
            onValueChange = onAmountChanged,
            label = { Text(stringResource(R.string.send_enter_amount)) },
            placeholder = { Text(stringResource(R.string.send_amount_placeholder)) },
            isError = amountState.error != null,
            supportingText = amountState.error?.let { { Text(it) } },
            trailingIcon = {
                if (amountState.value.isNotEmpty()) {
                    IconButton(onClick = { onAmountChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.send_clear_amount),
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
            Text(stringResource(R.string.send_sending))
        } else {
            Text(stringResource(R.string.send_transaction))
        }
    }
}

@Composable
@PreviewScreenSizes
@PreviewLightDark
private fun SendScreenPreview() {
    EthWalletTheme {
        SendScreen(
            viewState = SendViewState(),
            onEvent = {},
            onBackPress = { },
        )
    }
}
