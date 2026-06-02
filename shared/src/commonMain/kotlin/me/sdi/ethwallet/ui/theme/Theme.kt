package me.sdi.ethwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalEthWalletColorScheme = staticCompositionLocalOf<ColorScheme> {
    error("No ColorScheme provided")
}

val LocalEthWalletTypography = staticCompositionLocalOf<Typography> {
    error("No Typography provided")
}

@Composable
expect fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

@Composable
fun EthWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = platformColorScheme(darkTheme, dynamicColor)
        ?: if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalEthWalletColorScheme provides colorScheme,
        LocalEthWalletTypography provides Typography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

object EthWalletTheme {
    val styles: ComponentStyles = ComponentStyles
}
