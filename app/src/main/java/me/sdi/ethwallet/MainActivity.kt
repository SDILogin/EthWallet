package me.sdi.ethwallet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding(),
                        )
                    } else {
                        WalletsScreen(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    Surface {
        Column(
            modifier =
                modifier
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(16.dp))
            Icon(
                painter = painterResource(R.drawable.eth_logo_120w),
                tint = Color.Unspecified,
                contentDescription = null,
                modifier = Modifier.width(100.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "ETH Wallet",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .widthIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                },
            ) {
                Text(
                    text = "Create new wallet",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .widthIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                },
            ) {
                Text(
                    text = "Import existing wallet",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
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
