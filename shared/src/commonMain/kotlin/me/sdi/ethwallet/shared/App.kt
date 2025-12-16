package me.sdi.ethwallet.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.sdi.ethwallet.ui.OnboardingScreen
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Composable
fun App() {
    EthWalletTheme {
        OnboardingScreen(
            modifier = Modifier.fillMaxSize(),
            onCreateWallet = {},
            onImportWallet = {},
        )
    }
}
