package me.sdi.ethwallet.util

import androidx.compose.runtime.Composable

/**
 * Platform-specific toast manager for showing toast messages.
 */
expect class ToastManager {
    fun showToast(message: String)
}

/**
 * Composable function to remember a ToastManager instance.
 */
@Composable
expect fun rememberToastManager(): ToastManager
