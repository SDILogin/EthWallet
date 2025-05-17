package me.sdi.ethwallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.CreateWalletScreen

@OptIn(ExperimentalMaterial3Api::class)
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    navController = navController,
                    startDestination = Destination.OnBoardingScreen,
                ) {
                    composable<Destination.OnBoardingScreen> {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize(),
                            onImportWallet = {
                            },
                            onCreateWallet = {
                                navController.navigate(Destination.CreateWalletScreen)
                            },
                        )
                    }

                    composable<Destination.CreateWalletScreen> {
                        CreateWalletScreen(
                            modifier = Modifier.fillMaxSize().background(Color.Red),
                            onUpButtonClick = { navController.navigateUp() },
                            onMnemonicSaved = {
                            },
                        )
                    }
                }
            }
        }
    }
}
