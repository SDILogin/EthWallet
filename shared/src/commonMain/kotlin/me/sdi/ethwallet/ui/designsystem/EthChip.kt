package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

private const val CHIP_HEIGHT_DP = 40
private const val CHIP_ICON_SIZE_DP = 20

/**
 * One option of a required single choice, with an icon and a short label.
 */
@Composable
fun EthChip(
    text: String,
    icon: Painter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(CHIP_HEIGHT_DP.dp),
        shape = CircleShape,
        color = if (selected) colorScheme.surface else colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurface,
        border = selectionBorder(selected),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(CHIP_ICON_SIZE_DP.dp),
            )
            EthText(text = text, maxLines = 1)
        }
    }
}
