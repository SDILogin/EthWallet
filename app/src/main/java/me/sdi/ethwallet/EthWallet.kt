package me.sdi.ethwallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.sdi.ethwallet.ui.theme.EthWalletTheme

@Composable
fun EthWallet(viewModel: InitialNavigationViewModel) {
    EthWalletTheme {
        val numberOfWallets by viewModel.numberOfWallets.collectAsStateWithLifecycle()
        val navController = rememberNavController()
        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = numberOfWallets >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    navController = navController,
                    startDestination = "onboarding",
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize(),
                            onImportWallet = { },
                            onCreateWallet = { },
                        )
                    }
                }
            }
        }
    }
}
