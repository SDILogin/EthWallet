package me.sdi.ethwallet.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val LocalToastManager = staticCompositionLocalOf<ToastManager?> { null }

actual class ToastManager(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    actual fun showToast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

@Composable
actual fun rememberToastManager(): ToastManager {
    return checkNotNull(LocalToastManager.current)
}

@Composable
actual fun ToastHost(content: @Composable () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toastManager = remember(snackbarHostState, scope) { ToastManager(snackbarHostState, scope) }
    CompositionLocalProvider(LocalToastManager provides toastManager) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
