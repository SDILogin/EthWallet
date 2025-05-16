package me.sdi.ethwallet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sdi.ethwallet.ui.theme.EthWalletTheme

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
        setContent {
            EthWalletTheme {
                val numberOfWallets by viewModel.numberOfWallets.collectAsStateWithLifecycle()
                if (numberOfWallets >= 0) {
                    if (numberOfWallets == 0) {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
                        )
                    } else {
                        WalletsScreen(
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Welcome to EthWallet! Let's get started.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
fun WalletsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Wallets",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
