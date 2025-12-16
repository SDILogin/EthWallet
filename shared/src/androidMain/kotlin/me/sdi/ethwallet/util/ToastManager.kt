package me.sdi.ethwallet.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class ToastManager(private val context: Context) {
    actual fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
actual fun rememberToastManager(): ToastManager {
    val context = LocalContext.current
    return remember { ToastManager(context.applicationContext) }
}
