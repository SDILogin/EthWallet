package me.sdi.ethwallet.ui.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.ERC20_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.ETH_TRANSFER_GAS_UNITS
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.arrow_back_24px
import me.sdi.ethwallet.resources.back
import me.sdi.ethwallet.resources.cancel_24px
import me.sdi.ethwallet.resources.ic_eth_logo
import me.sdi.ethwallet.resources.send_address_placeholder
import me.sdi.ethwallet.resources.send_amount
import me.sdi.ethwallet.resources.send_amount_placeholder
import me.sdi.ethwallet.resources.send_base_fee
import me.sdi.ethwallet.resources.send_base_fee_placeholder
import me.sdi.ethwallet.resources.send_clear_address
import me.sdi.ethwallet.resources.send_clear_amount
import me.sdi.ethwallet.resources.send_clear_base_fee
import me.sdi.ethwallet.resources.send_clear_form
import me.sdi.ethwallet.resources.send_clear_gas_units
import me.sdi.ethwallet.resources.send_clear_priority_fee
import me.sdi.ethwallet.resources.send_custom_gas_network_fee
import me.sdi.ethwallet.resources.send_enter_amount
import me.sdi.ethwallet.resources.send_enter_receiver_address
import me.sdi.ethwallet.resources.send_fee_estimate
import me.sdi.ethwallet.resources.send_gas_units
import me.sdi.ethwallet.resources.send_gas_units_placeholder
import me.sdi.ethwallet.resources.send_priority_fee
import me.sdi.ethwallet.resources.send_priority_fee_placeholder
import me.sdi.ethwallet.resources.send_receiver_address
import me.sdi.ethwallet.resources.send_sending
import me.sdi.ethwallet.resources.send_title
import me.sdi.ethwallet.resources.send_token
import me.sdi.ethwallet.resources.send_token_dai
import me.sdi.ethwallet.resources.send_token_eth
import me.sdi.ethwallet.resources.send_token_usdc
import me.sdi.ethwallet.resources.send_token_usdt
import me.sdi.ethwallet.resources.send_transaction
import me.sdi.ethwallet.resources.send_transfer_speed
import me.sdi.ethwallet.resources.send_transfer_successful
import me.sdi.ethwallet.ui.KnownToken
import me.sdi.ethwallet.ui.components.FormFactorPreviews
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.ui.usdtOnMainnet
import me.sdi.ethwallet.util.formatUsdCurrency
import me.sdi.ethwallet.util.rememberToastManager
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.math.BigDecimal
import java.math.BigInteger

private val SECTION_ENTER = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300))
private val SECTION_EXIT = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))

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
                SendNews.NavigateBack -> {
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

            Column {
                Text(
                    text = stringResource(Res.string.send_amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmountInput(
                    amountState = viewState.transferAmountInputState,
                    label = stringResource(Res.string.send_enter_amount),
                    placeholder = stringResource(Res.string.send_amount_placeholder),
                    clearContentDescription = stringResource(Res.string.send_clear_amount),
                    supportingText = viewState.transferAmountInputState.error,
                    onAmountChanged = { onEvent(SendEvent.OnTransferAmountChanged(it)) },
                )
            }

            GasSelector(
                gas = viewState.gas,
                onGasOptionSelected = { onEvent(SendEvent.OnGasOptionSelected(it)) },
                onBaseFeeChanged = { onEvent(SendEvent.OnBaseFeeChanged(it)) },
                onPriorityFeeChanged = { onEvent(SendEvent.OnPriorityFeeChanged(it)) },
                onGasUnitsChanged = { onEvent(SendEvent.OnGasUnitsChanged(it)) },
            )

            SendButton(
                enabled = viewState.isTransferEnabled,
                loading = viewState.transferring,
                onSendClick = { onEvent(SendEvent.OnSendClick) },
            )

            AnimatedVisibility(
                visible = !viewState.transferring,
                enter = SECTION_ENTER,
                exit = SECTION_EXIT,
            ) {
                Button(
                    onClick = { onEvent(SendEvent.OnClearFormClick) },
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
            FilterChip(
                selected = selectedTokenContract == EthContractAddress,
                onClick = { onTokenSelected(EthContractAddress) },
                label = { Text(stringResource(Res.string.send_token_eth)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_eth_logo),
                        tint = Color.Unspecified,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

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
private fun GasSelector(
    gas: GasViewState,
    onGasOptionSelected: (GasOption) -> Unit,
    onBaseFeeChanged: (String) -> Unit,
    onPriorityFeeChanged: (String) -> Unit,
    onGasUnitsChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.send_transfer_speed),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gas.chips.forEach { chip ->
                FilterChip(
                    selected = chip.selected,
                    enabled = chip.enabled,
                    onClick = { onGasOptionSelected(chip.option) },
                    label = { GasChipLabel(chip.labelRes, chip.feeUsd) },
                )
            }
        }

        val customSelected = gas.chips.firstOrNull { it.selected }?.option == GasOption.CustomFees
        AnimatedVisibility(
            visible = customSelected,
            enter = SECTION_ENTER,
            exit = SECTION_EXIT,
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                AmountInput(
                    amountState = gas.baseFee,
                    label = stringResource(Res.string.send_base_fee),
                    placeholder = stringResource(Res.string.send_base_fee_placeholder),
                    clearContentDescription = stringResource(Res.string.send_clear_base_fee),
                    supportingText = gas.baseFee.error,
                    onAmountChanged = onBaseFeeChanged,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmountInput(
                    amountState = gas.priorityFee,
                    label = stringResource(Res.string.send_priority_fee),
                    placeholder = stringResource(Res.string.send_priority_fee_placeholder),
                    clearContentDescription = stringResource(Res.string.send_clear_priority_fee),
                    supportingText = gas.priorityFee.error,
                    onAmountChanged = onPriorityFeeChanged,
                )
                AnimatedVisibility(
                    visible = gas.gasUnitsEditable,
                    enter = SECTION_ENTER,
                    exit = SECTION_EXIT,
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        AmountInput(
                            amountState = gas.gasUnits,
                            label = stringResource(Res.string.send_gas_units),
                            placeholder = stringResource(Res.string.send_gas_units_placeholder),
                            clearContentDescription = stringResource(Res.string.send_clear_gas_units),
                            supportingText = gas.gasUnits.error,
                            keyboardType = KeyboardType.Number,
                            onAmountChanged = onGasUnitsChanged,
                        )
                    }
                }
                CustomFeeEstimate(customFeeUsd = gas.customFeeUsd)
            }
        }
    }
}

@Composable
private fun CustomFeeEstimate(customFeeUsd: String?) {
    var lastFeeUsd by remember { mutableStateOf("") }
    if (customFeeUsd != null) {
        lastFeeUsd = customFeeUsd
    }
    AnimatedVisibility(
        visible = customFeeUsd != null,
        enter = SECTION_ENTER,
        exit = SECTION_EXIT,
    ) {
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.send_custom_gas_network_fee, lastFeeUsd),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GasChipLabel(labelRes: StringResource, feeUsd: String?) {
    val zeroFee = remember { formatUsdCurrency(BigDecimal.ZERO) }
    Column {
        Text(stringResource(labelRes))
        Text(
            text = stringResource(Res.string.send_fee_estimate, feeUsd ?: zeroFee),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ReceiverAddressInput(
    addressState: TextInputState,
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
    amountState: TextInputState,
    label: String,
    placeholder: String,
    clearContentDescription: String,
    supportingText: String?,
    onAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = amountState.value,
        onValueChange = onAmountChanged,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = amountState.error != null,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            if (amountState.value.isNotEmpty()) {
                IconButton(onClick = { onAmountChanged("") }) {
                    Icon(
                        painter = painterResource(Res.drawable.cancel_24px),
                        contentDescription = clearContentDescription,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
        ),
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
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

private class SendViewStateProvider : PreviewParameterProvider<SendViewState> {
    override val values: Sequence<SendViewState> = sequenceOf(
        SendViewState(
            receiverAddress = TextInputState(value = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB11"),
            transferAmountInputState = TextInputState(value = "0.5"),
            gas = GasViewState(
                chips = listOf(
                    GasChip(
                        GasOption.LowFees,
                        GasOption.LowFees.labelRes(),
                        feeUsd = "$0.02",
                        selected = false,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.Default,
                        GasOption.Default.labelRes(),
                        feeUsd = "$0.05",
                        selected = true,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.FastTransfer,
                        GasOption.FastTransfer.labelRes(),
                        feeUsd = "$0.08",
                        selected = false,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.CustomFees,
                        GasOption.CustomFees.labelRes(),
                        feeUsd = null,
                        selected = false,
                        enabled = true,
                    ),
                ),
                maxFee = Wei(BigInteger("32000000000")),
                priorityFeeWei = Wei(BigInteger("2000000000")),
                gasUnitsValue = ETH_TRANSFER_GAS_UNITS,
            ),
        ),
        SendViewState(
            selectedTokenContractAddress = usdtOnMainnet.ethAddress,
            receiverAddress = TextInputState(value = "0x742d35cc6634C0532925a3b8d73a726FbCF3eB11"),
            transferAmountInputState = TextInputState(value = "125.00"),
            gas = GasViewState(
                chips = listOf(
                    GasChip(
                        GasOption.LowFees,
                        GasOption.LowFees.labelRes(),
                        feeUsd = "$0.06",
                        selected = false,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.Default,
                        GasOption.Default.labelRes(),
                        feeUsd = "$0.15",
                        selected = false,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.FastTransfer,
                        GasOption.FastTransfer.labelRes(),
                        feeUsd = "$0.25",
                        selected = false,
                        enabled = true,
                    ),
                    GasChip(
                        GasOption.CustomFees,
                        GasOption.CustomFees.labelRes(),
                        feeUsd = "$0.11",
                        selected = true,
                        enabled = true,
                    ),
                ),
                baseFee = TextInputState(value = "20"),
                priorityFee = TextInputState(value = "2"),
                gasUnits = TextInputState(value = "65000"),
                gasUnitsEditable = true,
                customFeeUsd = "$0.11",
                maxFee = Wei(BigInteger("22000000000")),
                priorityFeeWei = Wei(BigInteger("2000000000")),
                gasUnitsValue = ERC20_TRANSFER_GAS_UNITS,
            ),
        ),
    )
}

@FormFactorPreviews
@Composable
private fun SendScreenContentPreview(
    @PreviewParameter(SendViewStateProvider::class) viewState: SendViewState,
) {
    MaterialTheme {
        SendScreenContent(
            modifier = Modifier.fillMaxSize(),
            viewState = viewState,
            chainTokens = supportedKnownTokens(1L),
            onEvent = {},
            onBackPress = {},
        )
    }
}
