package me.sdi.ethwallet

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.ui.EthWallet

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<InitialNavigationViewModel>(
        factoryProducer = { InitialNavigationViewModel.Factory },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        initLogger()

        var lastPrintAt = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            // warmup database
            AppDatabase.getInstance(application)

            (viewModel.numberOfWallets.value < 0).also {
                val now = System.currentTimeMillis()
                if (now - lastPrintAt > 1000) {
                    lastPrintAt = now
                    Logger.debug("MainActivity") {
                        "Splash screen condition: $it"
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent { EthWallet(viewModel) }
    }
}

private fun Context.initLogger() {
    Logger.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    Logger.printer = { level: Level, tag: Tag, message: Message ->
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.WARN -> Log.w(tag, message)
        }
    }
    Logger.debug("EthWallet") { "Logger initialized" }
}
