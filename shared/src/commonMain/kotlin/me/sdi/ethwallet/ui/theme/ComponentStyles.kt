package me.sdi.ethwallet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationStyleApi::class)
val StyleScope.colors: ColorScheme
    get() = LocalEthWalletColorScheme.currentValue

@OptIn(ExperimentalFoundationStyleApi::class)
val StyleScope.typography: Typography
    get() = LocalEthWalletTypography.currentValue

@OptIn(ExperimentalFoundationStyleApi::class)
object ComponentStyles {
    val mnemonicCardStyle = Style {
        background(colors.surfaceVariant)
        shape(RoundedCornerShape(16.dp))
        borderWidth(1.dp)
        borderColor(colors.outline)
    }
}
