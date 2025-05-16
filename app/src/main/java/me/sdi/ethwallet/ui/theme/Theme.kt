package me.sdi.ethwallet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF1C64F2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF002366),
        onPrimaryContainer = Color(0xFFE3ECFF),
        inversePrimary = Color(0xFFADCFFF),
        secondary = Color.White,
        onSecondary = Color(0xFF1A1A1A),
        secondaryContainer = Color(0xFF2B2B2B),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFFE0E0E0),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF333333),
        onTertiaryContainer = Color.White,
        background = Color(0xFF000000),
        onBackground = Color.White,
        surface = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceTint = Color(0xFF1C64F2),
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF888888),
        outlineVariant = Color(0xFF444444),
        scrim = Color.Black,
        surfaceBright = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF171717),
        surfaceContainerHigh = Color(0xFF222222),
        surfaceContainerHighest = Color(0xFF2C2C2C),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceDim = Color(0xFF101010),
    )

val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1C64F2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3ECFF),
        onPrimaryContainer = Color(0xFF002366),
        inversePrimary = Color(0xFFB3CFFF),
        secondary = Color(0xFF222222),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFEFEF),
        onSecondaryContainer = Color(0xFF111111),
        tertiary = Color(0xFF3C3C3C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDADADA),
        onTertiaryContainer = Color(0xFF1A1A1A),
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFF0F0F0),
        onSurfaceVariant = Color(0xFF1E1E1E),
        surfaceTint = Color(0xFF1C64F2),
        inverseSurface = Color(0xFF1A1A1A),
        inverseOnSurface = Color(0xFFF2F2F2),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF444444),
        outlineVariant = Color(0xFFAAAAAA),
        scrim = Color.Black,
        surfaceBright = Color(0xFFF8F8F8),
        surfaceContainer = Color(0xFFF4F4F4),
        surfaceContainerHigh = Color(0xFFEFEFEF),
        surfaceContainerHighest = Color(0xFFEAEAEA),
        surfaceContainerLow = Color(0xFFFDFDFD),
        surfaceContainerLowest = Color.White,
        surfaceDim = Color(0xFFE0E0E0),
    )

@Composable
fun EthWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
