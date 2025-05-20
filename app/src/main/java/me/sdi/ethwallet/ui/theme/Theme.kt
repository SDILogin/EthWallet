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

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6), // blue accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFF93C5FD),
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F2937), // gray button
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF9CA3AF), // light gray text
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF374151),
    onTertiaryContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFE5E7EB),
    surfaceTint = Color(0xFF3B82F6),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    error = Color(0xFFEF4444),
    onError = Color.Black,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color.White,
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF374151),
    scrim = Color.Black,
    surfaceBright = Color(0xFF111111),
    surfaceContainer = Color(0xFF171717),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF101010),
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    inversePrimary = Color(0xFF60A5FA),
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondaryContainer = Color(0xFF111827),
    tertiary = Color(0xFF4B5563),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1D5DB),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF1F2937),
    surfaceTint = Color(0xFF3B82F6),
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFD1D5DB),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceContainerLow = Color(0xFFFDFDFD),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFE5E7EB),
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
