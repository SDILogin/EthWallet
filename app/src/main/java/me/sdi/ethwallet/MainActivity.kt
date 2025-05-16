package me.sdi.ethwallet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<InitialNavigationViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var lastPrintAt = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            (viewModel.numberOfWallets.value < 0).also {
                val now = System.currentTimeMillis()
                if (now - lastPrintAt > 1000) {
                    lastPrintAt = now
                    Log.d("MainActivity", "Splash screen condition: $it")
                }
            }
        }

        enableEdgeToEdge()
        setContent { EthWallet(viewModel) }
    }
}
