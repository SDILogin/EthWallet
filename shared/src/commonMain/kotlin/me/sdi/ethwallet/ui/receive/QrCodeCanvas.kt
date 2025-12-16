package me.sdi.ethwallet.ui.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific QR code canvas composable.
 * Renders a QR code for the given data string.
 */
@Composable
expect fun QrCodeCanvas(
    qrData: String,
    modifier: Modifier = Modifier,
)
