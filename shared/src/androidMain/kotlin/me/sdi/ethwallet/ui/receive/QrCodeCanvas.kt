package me.sdi.ethwallet.ui.receive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import me.sdi.ethwallet.Logger

@Composable
actual fun QrCodeCanvas(
    qrData: String,
    modifier: Modifier,
) {
    var bitMatrix: BitMatrix? by remember { mutableStateOf(null) }
    var loading: Boolean by remember { mutableStateOf(bitMatrix == null) }

    val density = LocalDensity.current
    val size = 300.dp
    val sizePx = with(density) { size.toPx().toInt() }
    LaunchedEffect(qrData) {
        Logger.debug("QrCodeCanvas") { "Generating QR code for $qrData" }
        bitMatrix = try {
            // qrcode data based size
            val qrSize = if (qrData.length > 100) 300 else 150
            QRCodeWriter().encode(
                qrData,
                BarcodeFormat.QR_CODE,
                qrSize,
                qrSize,
            )
        } catch (t: Throwable) {
            Logger.warn("QrCode", t) { "Error generating QR code" }
            null
        }
        loading = false
    }

    if (loading) {
        CircularProgressIndicator()
    } else {
        bitMatrix?.let { matrix ->
            val pixelSize = sizePx.toFloat() / matrix.width
            val invertColors = isSystemInDarkTheme()
            val activeColor = if (invertColors) Color.White else Color.Black
            val inactiveColor = MaterialTheme.colorScheme.background
            Canvas(modifier = modifier) {
                drawRect(
                    color = inactiveColor,
                    size = Size(sizePx.toFloat(), sizePx.toFloat()),
                )
                for (x in 0 until matrix.width) {
                    for (y in 0 until matrix.height) {
                        if (matrix[x, y]) {
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
