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
                        "Transfer successful! Hash: ${news.transactionHash}",
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
                title = { Text("Send") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                    Text("Clear Form")
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
            text = "Token",
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
                label = { Text("ETH") },
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
                        val name = when (token) {
                            KnownTokens.USDT -> "USDT"
                            KnownTokens.USDC -> "USDC"
                            KnownTokens.DAI -> "DAI"
                        }
                        Text(name)
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
            text = "Receiver Address",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = addressState.value,
            onValueChange = onAddressChanged,
            label = { Text("Enter receiver address") },
            placeholder = { Text("0x...") },
            isError = addressState.error != null,
            supportingText = addressState.error?.let { { Text(it) } },
            trailingIcon = {
                if (addressState.value.isNotEmpty()) {
                    IconButton(onClick = { onAddressChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear address",
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
            text = "Amount",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amountState.value,
            onValueChange = onAmountChanged,
            label = { Text("Enter amount") },
            placeholder = { Text("0.0") },
            isError = amountState.error != null,
            supportingText = amountState.error?.let { { Text(it) } },
            trailingIcon = {
                if (amountState.value.isNotEmpty()) {
                    IconButton(onClick = { onAmountChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear amount",
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
            Text("Sending...")
        } else {
            Text("Send Transaction")
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
