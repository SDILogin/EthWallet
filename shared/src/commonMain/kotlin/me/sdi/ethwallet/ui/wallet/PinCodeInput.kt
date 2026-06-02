package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Composable
fun PinCodeInput(
    pinCode: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 6,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxLength) { index ->
            val isActive = index < pinCode.length
            val color by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                animationSpec = tween(300),
                label = "colorAnimation",
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.2f else 1f,
                animationSpec = tween(300),
                label = "scaleAnimation",
            )

            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .scale(scale)
                        .background(color, CircleShape),
            )
        }
    }
}

@Preview
@Composable
private fun PinCodeInputPreview(
    @PreviewParameter(PinCodeInputPreviewParameterProvider::class) pinCode: String,
) {
    EthWalletTheme {
        Box(modifier = Modifier.padding(8.dp)) {
            PinCodeInput(
                pinCode = pinCode,
                maxLength = 6,
            )
        }
    }
}

private class PinCodeInputPreviewParameterProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "",
        "123",
        "123456",
    )
}
