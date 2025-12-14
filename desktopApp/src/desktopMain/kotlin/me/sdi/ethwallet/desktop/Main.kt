package me.sdi.ethwallet.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.sdi.ethwallet.shared.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "EthWallet") {
        App()
    }
}
