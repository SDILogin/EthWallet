package me.sdi.ethwallet.ui.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Primary: the main action of a screen. Secondary: an alternative action.
 * Text: a low-emphasis action such as Edit or Skip.
 */
enum class EthButtonStyle { Primary, Secondary, Text }

/**
 * The only labeled button of the app. Loading replaces the label with a spinner.
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
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            EthText(text = text)
        }
    }
    when (style) {
        EthButtonStyle.Primary -> Button(onClick = onClick, modifier = modifier, enabled = enabled, content = { label() })
        EthButtonStyle.Secondary ->
            OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = { label() })
        EthButtonStyle.Text -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = { label() })
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
        )
    } else {
        IconButtonDefaults.iconButtonColors()
    }
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, content = content)
}
