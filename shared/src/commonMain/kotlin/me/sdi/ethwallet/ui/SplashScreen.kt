package me.sdi.ethwallet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.ic_eth_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Height the Android system splash screen gives the logo: the 54dp tall drawing inside the 108dp
 * `ic_launcher_foreground` canvas, which the platform renders at 288dp
 * (`starting_surface_icon_size` 160dp, times the 1.2 no-icon-background scale, times the 1.5
 * adaptive icon inflation).
 */
private val LogoHeight = 144.dp

/**
 * This is just a root screen in the navigation backstack. It is used to prevent empty backstack
 * state while we are checking for auth state.
 *
 * Logo size, centre and background repeat the Android system splash screen, so the system splash
 * can be dropped on top of this screen without any visible change. The background must stay equal
 * to `@color/splash_background`, which the platform paints behind its own icon.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(if (isSystemInDarkTheme()) Color.Black else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_eth_logo),
            contentDescription = null,
            modifier = Modifier.height(LogoHeight),
        )
    }
}
