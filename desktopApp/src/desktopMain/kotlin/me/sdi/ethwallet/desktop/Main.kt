package me.sdi.ethwallet.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.sdi.ethwallet.ui.OnboardingScreen
import me.sdi.ethwallet.ui.theme.EthWalletTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EthWallet",
        state = rememberWindowState(width = 400.dp, height = 600.dp),
    ) {
        EthWalletTheme {
            OnboardingScreen(
                modifier = Modifier.fillMaxSize(),
                onCreateWallet = {
                    println("Create wallet clicked")
                },
                onImportWallet = {
                    println("Import wallet clicked")
                },
            )
        }
    }
}
