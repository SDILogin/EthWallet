package me.sdi.ethwallet.ui.receive

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Address", news.text)
                    clipboard.setPrimaryClip(clip)
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
            // Currency Picker
            CurrencyPicker(
                selectedToken = viewState.selectedToken,
                onTokenSelected = { onEvent(ReceiveQrEvent.OnTokenSelected(it)) },
            )

            // Amount Input
            AmountInput(
                amount = viewState.amount,
                amountError = viewState.amountError,
                onAmountChanged = { onEvent(ReceiveQrEvent.OnAmountChanged(it)) },
                onClearAmount = { onEvent(ReceiveQrEvent.OnClearAmount) },
            )

            // QR Code Display
            QrCodeDisplay(
                qrPayload = viewState.qrPayload,
                isLoading = viewState.isLoading,
            )

            // Action Buttons
            ActionButtons(
                onGenerateQr = { onEvent(ReceiveQrEvent.OnGenerateQrCode) },
                onCopyAddress = { onEvent(ReceiveQrEvent.OnCopyAddress) },
                hasQrCode = viewState.qrPayload != null,
            )

            // Error Display
            viewState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CurrencyPicker(
    selectedToken: SelectedToken,
    onTokenSelected: (SelectedToken) -> Unit,
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
                selected = selectedToken == SelectedToken.ETH,
                onClick = { onTokenSelected(SelectedToken.ETH) },
                label = { Text("ETH") },
            )

            // ERC20 Token Chips
            KnownTokens.entries.forEach { token ->
                FilterChip(
                    selected = selectedToken is SelectedToken.ERC20 && selectedToken.token == token,
                    onClick = { onTokenSelected(SelectedToken.ERC20(token)) },
                    label = { Text(token.name.split(" ")[0]) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = token.icon),
                            contentDescription = token.tokenName,
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (amount.isNotEmpty()) {
            Button(
                onClick = onClearAmount,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Clear Amount")
            }
        }
    }
}

@Composable
private fun QrCodeDisplay(
    qrPayload: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "QR Code",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                qrPayload != null -> {
                    QrCodeCanvas(
                        qrData = qrPayload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Text(
                        text = "Generate QR code to display",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCodeCanvas(
    qrData: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier.size(size)) {
        try {
            val sizePx = with(density) { size.toPx().toInt() }
            val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(
                qrData,
                com.google.zxing.BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
            )

            val pixelSize = sizePx.toFloat() / bitMatrix.width
            val canvasSize = Size(sizePx.toFloat(), sizePx.toFloat())

            // Draw white background
            drawRect(
                color = Color.White,
                topLeft = Offset.Zero,
                size = canvasSize,
            )

            // Draw QR code pixels
            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    if (bitMatrix[x, y]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x * pixelSize, y * pixelSize),
                            size = Size(pixelSize, pixelSize),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Handle QR generation error - draw placeholder
            val sizePx = with(density) { size.toPx() }
            drawRect(
                color = Color.LightGray,
                topLeft = Offset.Zero,
                size = Size(sizePx, sizePx),
            )
        }
    }
}

@Composable
private fun ActionButtons(
    onGenerateQr: () -> Unit,
    onCopyAddress: () -> Unit,
    hasQrCode: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onGenerateQr,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Generate QR Code")
        }

        Button(
            onClick = onCopyAddress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copy Address")
        }
    }
}

@Composable
@PreviewScreenSizes
@PreviewLightDark
private fun ReceiveScreenPreview() {
    EthWalletTheme {
        ReceiveScreen(
            viewState = ReceiveQrViewState(),
            onEvent = {},
        )
    }
}
