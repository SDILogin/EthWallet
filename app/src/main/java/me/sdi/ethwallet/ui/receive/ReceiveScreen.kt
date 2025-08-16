package me.sdi.ethwallet.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CopyAll
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.R
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.KnownTokens
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiveQrViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(key1 = viewModel.news) {
        viewModel.news.collect { news ->
            when (news) {
                is ReceiveQrNews.ShowToast -> {
                    Toast.makeText(context, news.message, Toast.LENGTH_SHORT).show()
                }
                is ReceiveQrNews.CopyToClipboard -> {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Address", news.text)
                    clipboard.setPrimaryClip(clip)

                    // Show toast for older Android versions where clipboard labels aren't shown
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    ReceiveScreen(
        modifier = modifier,
        viewState = viewState,
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
private fun ReceiveScreen(
    modifier: Modifier = Modifier,
    viewState: ReceiveQrViewState,
    onEvent: (event: ReceiveQrEvent) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Receive") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ReceiveQrEvent.OnBackPressed) }) {
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
            CurrencyPicker(
                selectedTokenContract = viewState.selectedTokenContract,
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
    onTokenSelected: (EthAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Currency",
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
private fun AmountInput(
    amount: String,
    amountError: String?,
    onAmountChanged: (String) -> Unit,
    onClearAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Amount (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChanged,
            label = { Text("Enter amount") },
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
                            imageVector = Icons.Default.Clear,
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
                        text = "Select currency to generate QR code",
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
                imageVector = Icons.Filled.CopyAll,
                contentDescription = "Copy Address",
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

@Composable
private fun QrCodeCanvas(
    qrData: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    var bitMatrix: BitMatrix? by remember { mutableStateOf(null) }
    var loading: Boolean by remember { mutableStateOf(bitMatrix == null) }

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx().toInt() }
    LaunchedEffect(qrData) {
        Logger.debug("QrCodeCanvas") { "Generating QR code for $qrData" }
        bitMatrix = QRCodeWriter().encode(
            qrData,
            BarcodeFormat.QR_CODE,
            150,
            150,
        )
        loading = false
    }

    if (loading) {
        SideEffect {
            Logger.debug("QrCodeCanvas") { "Loading" }
        }
        CircularProgressIndicator()
    } else {
        SideEffect {
            Logger.debug("QrCodeCanvas") { "Canvas" }
        }
        bitMatrix?.let { bitMatrix ->
            SideEffect {
                Logger.debug("QrCodeCanvas") { "Draw matrix" }
            }
            val pixelSize = sizePx.toFloat() / bitMatrix.width
            val invertColors = isSystemInDarkTheme()
            val activeColor = if (invertColors) Color.White else Color.Black
            val inactiveColor =MaterialTheme.colorScheme.background
            Canvas(modifier = modifier.size(size)) {
                drawRect(
                    color = inactiveColor,
                    size = Size(sizePx.toFloat(), sizePx.toFloat()),
                )
                for (x in 0 until bitMatrix.width) {
                    for (y in 0 until bitMatrix.height) {
                        if (bitMatrix[x, y]) {
                            drawRect(
                                color = activeColor,
                                topLeft = Offset(x * pixelSize, y * pixelSize),
                                size = Size(pixelSize, pixelSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@PreviewScreenSizes
@PreviewLightDark
private fun ReceiveScreenPreview() {
    EthWalletTheme {
        ReceiveScreen(
            viewState = ReceiveQrViewState(
                qrPayload = "123",
            ),
            onEvent = {},
        )
    }
}
