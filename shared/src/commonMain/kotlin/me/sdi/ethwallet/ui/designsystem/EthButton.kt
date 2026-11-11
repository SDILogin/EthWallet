package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

private const val BUTTON_MIN_HEIGHT_DP = 52

/**
 * Primary: the main action of a screen. Secondary: an alternative action.
 * Text: a low-emphasis action such as Edit or Skip.
 */
enum class EthButtonStyle { Primary, Secondary, Text }

/**
 * The only labeled button of the app. Loading ignores clicks, keeps the enabled colors
 * and the width, and shows a spinner over the hidden label.
 */
@Composable
fun EthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EthButtonStyle = EthButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val label: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            EthText(text = text, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            }
        }
    }
    val isEnabled = enabled && !loading
    when (style) {
        EthButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors().let {
                if (loading) {
                    it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor)
                } else {
                    it
                }
            },
            content = { label() },
        )
        EthButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
            enabled = isEnabled,
            border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ).let {
                if (loading) {
                    it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor)
                } else {
                    it
                }
            },
            content = { label() },
        )
        EthButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = isEnabled,
            colors = ButtonDefaults.textButtonColors().let {
                if (loading) {
                    it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor)
                } else {
                    it
                }
            },
            content = { label() },
        )
    }
}

/**
 * The only icon button of the app. Filled draws the primary color behind the icon.
 */
@Composable
fun EthIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (filled) {
        IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    } else {
        IconButtonDefaults.iconButtonColors()
    }
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, content = content)
}
