package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TILE_CORNER_DP = 16
private const val SELECTED_BORDER_DP = 2
private const val TILE_DISABLED_ALPHA = 0.38f
private const val TILE_TITLE_MIN_FONT_SP = 12
private const val TILE_TITLE_MAX_FONT_SP = 16

/**
 * One option of a required single choice, with a title and a hint below it.
 * Place the options in a row and give them equal weight. The title shrinks to fit one line.
 */
@Composable
fun EthSelectableTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else TILE_DISABLED_ALPHA),
        enabled = enabled,
        shape = RoundedCornerShape(TILE_CORNER_DP.dp),
        color = if (selected) colorScheme.surface else colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurface,
        border = selectionBorder(selected),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EthText(
                text = title,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = TILE_TITLE_MIN_FONT_SP.sp,
                    maxFontSize = TILE_TITLE_MAX_FONT_SP.sp,
                ),
            )
            EthText(
                text = subtitle,
                style = EthTextStyle.Caption,
                color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The border of an [EthChip] or an [EthSelectableTile]. The border does not change the size.
 */
@Composable
internal fun selectionBorder(selected: Boolean): BorderStroke? {
    val colorScheme = MaterialTheme.colorScheme
    return if (selected) {
        BorderStroke(SELECTED_BORDER_DP.dp, colorScheme.onSurface)
    } else if (colorScheme.surface.luminance() < 0.5f) {
        BorderStroke(1.dp, colorScheme.outlineVariant)
    } else {
        null
    }
}
