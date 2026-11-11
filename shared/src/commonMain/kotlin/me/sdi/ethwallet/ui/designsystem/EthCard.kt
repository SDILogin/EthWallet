package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Default: regular content. Error: a failure message.
 */
enum class EthCardTone { Default, Error }

/**
 * The only card of the app. Passing onClick makes the card clickable.
 */
@Composable
fun EthCard(
    modifier: Modifier = Modifier,
    tone: EthCardTone = EthCardTone.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(
        containerColor = when (tone) {
            EthCardTone.Default -> MaterialTheme.colorScheme.surfaceContainer
            EthCardTone.Error -> MaterialTheme.colorScheme.errorContainer
        },
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    val border = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content,
        )
    }
}
