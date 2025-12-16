package me.sdi.ethwallet.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.sdi.ethwallet.Logger

actual class ToastManager {
    actual fun showToast(message: String) {
        // Desktop implementation - log the message for now
        // In the future, this could show a snackbar or notification
        Logger.debug("ToastManager") { "Toast: $message" }
    }
}

@Composable
actual fun rememberToastManager(): ToastManager {
    return remember { ToastManager() }
}
