package me.sdi.ethwallet.ui.wallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.pin_digits_entered
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun PinCodeInput(
    pinCode: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 6,
    isError: Boolean = false,
    shakeTrigger: Int = 0,
) {
    val fillColor by animateColorAsState(
        targetValue =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        animationSpec = tween(150),
        label = "fillColor",
    )
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    val digitsEntered = stringResource(Res.string.pin_digits_entered, pinCode.length, maxLength)

    val density = LocalDensity.current
    val shakeSmall = with(density) { 8.dp.toPx() }
    val shakeLarge = with(density) { 12.dp.toPx() }
    val shakeOffset = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger == 0) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        shakeOffset.animateTo(
            targetValue = 0f,
            animationSpec =
                keyframes {
                    durationMillis = 350
                    (-shakeLarge) at 50
                    shakeLarge at 110
                    (-shakeSmall) at 170
                    shakeSmall at 230
                    0f at 350
                },
        )
    }

    Row(
        modifier =
            modifier
                .graphicsLayer { translationX = shakeOffset.value }
                .semantics { contentDescription = digitsEntered },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxLength) { index ->
            val isActive = index < pinCode.length
            val fillFraction = remember { Animatable(if (index < pinCode.length) 1f else 0f) }

            LaunchedEffect(isActive) {
                if (isActive) {
                    fillFraction.animateTo(
                        targetValue = 1f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                    )
                } else {
                    fillFraction.animateTo(targetValue = 0f, animationSpec = tween(100))
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .border(2.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = fillFraction.value
                                scaleY = fillFraction.value
                            }
                            .background(fillColor, CircleShape),
                )
            }
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
