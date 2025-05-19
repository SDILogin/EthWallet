package me.sdi.ethwallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.CreateWalletScreen
import me.sdi.ethwallet.ui.wallet.ImportWalletScreen
import me.sdi.ethwallet.ui.wallet.SetPinCodeScreen
import me.sdi.ethwallet.ui.wallet.WalletScreen
import me.sdi.ethwallet.ui.wallet.WalletsListScreen

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
                                navController.navigate(Destination.ImportWalletScreen)
                            },
                            onCreateWallet = {
                                navController.navigate(Destination.CreateWalletScreen)
                            },
                        )
                    }

                    composable<Destination.CreateWalletScreen> {
                        CreateWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navController.navigateUp() },
                            onMnemonicSaved = {
                                navController.navigate(Destination.SetPinCodeScreen)
                            },
                        )
                    }

                    composable<Destination.ImportWalletScreen> {
                        ImportWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navController.navigateUp() },
                            onWalletImported = {
                                navController.navigate(Destination.SetPinCodeScreen)
                            },
                        )
                    }

                    composable<Destination.WalletScreen> {
                        WalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onLogout = {
                                navController.navigate(Destination.OnBoardingScreen) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                }
                            },
                            onManageWallets = {
                                navController.navigate(Destination.WalletsListScreen)
                            },
                        )
                    }

                    composable<Destination.WalletsListScreen> {
                        WalletsListScreen(
                            modifier = Modifier.fillMaxSize(),
                            navigateToOnboarding = {
                                navController.navigate(Destination.OnBoardingScreen) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                }
                            },
                            navigateToWalletDetails = { navigateToWalletScreen(navController) },
                        )
                    }

                    composable<Destination.SetPinCodeScreen> {
                        SetPinCodeScreen(
                            modifier = Modifier.fillMaxSize(),
                            onPinCodeSet = {
                                // Navigate to appropriate screen after PIN is set
                                navigateToWalletScreen(navController)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToWalletScreen(navController: NavController) {
    navController.navigate(Destination.WalletScreen) {
        // Clear the back stack so the user can't navigate back to the onboarding flow
        popUpTo(navController.graph.findStartDestination().id) {
            inclusive = true
        }
    }
}
