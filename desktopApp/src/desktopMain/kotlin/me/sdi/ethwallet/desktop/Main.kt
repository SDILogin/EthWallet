package me.sdi.ethwallet.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import me.sdi.ethwallet.ui.InitialNavigationViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

fun main() = application {
    val chainConfig = ChainConfig(
        rpcUrl = System.getProperty("eth.rpc.url") ?: "https://eth.llamarpc.com",
        chainId = System.getProperty("eth.chain.id")?.toLongOrNull() ?: 1L,
        explorerBaseUrl = System.getProperty("eth.explorer.base.url"),
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "EthWallet",
        state = rememberWindowState(width = 400.dp, height = 600.dp),
    ) {
        KoinApplication(
            configuration = koinConfiguration(declaration = { modules(appModules(chainConfig)) }),
            content = {
                val viewModel: InitialNavigationViewModel = koinInject()
                EthWalletApp(viewModel)
            },
        )
    }
}
