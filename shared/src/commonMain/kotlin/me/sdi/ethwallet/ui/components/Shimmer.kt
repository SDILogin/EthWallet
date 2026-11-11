package me.sdi.ethwallet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Stable
data class Shimmer(
    val colors: List<Color>,
    val duration: Int = 1000,
)

@Composable
fun rememberShimmer(durationMillis: Int = 1000): Shimmer {
    val baseColor = MaterialTheme.colorScheme.onSurface
    return remember(durationMillis, baseColor) {
        Shimmer(
            colors = listOf(
                baseColor.copy(alpha = 0.12f),
                baseColor.copy(alpha = 0.04f),
                baseColor.copy(alpha = 0.12f),
            ),
            duration = durationMillis,
        )
    }
}

fun Modifier.shimmer(
    customShimmer: Shimmer? = null,
): Modifier = composed {
    val shimmer = customShimmer ?: rememberShimmer()
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = shimmer.duration,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ShimmerTranslate",
    )

    Modifier.drawWithContent {
        drawContent()

        val width = size.width
        val height = size.height

        if (width == 0f || height == 0f) return@drawWithContent

        val offset = translateAnimation * (width + height)

        val colors = shimmer.colors
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(
                x = offset - width,
                y = offset - height,
            ),
            end = Offset(offset, offset),
        )

        drawRect(
            brush = brush,
            blendMode = BlendMode.SrcAtop,
        )
    }
}
