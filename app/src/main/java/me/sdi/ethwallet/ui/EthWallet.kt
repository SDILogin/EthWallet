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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.CreateWalletScreen
import me.sdi.ethwallet.ui.wallet.ImportWalletScreen
import me.sdi.ethwallet.ui.wallet.SetPinCodeScreen
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeScreen
import me.sdi.ethwallet.ui.wallet.WalletScreen
import me.sdi.ethwallet.ui.wallet.WalletsListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EthWallet(
    viewModel: InitialNavigationViewModel,
    userActivityManager: UserActivityManager,
) {
    EthWalletTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val navController = rememberNavController()
        LaunchedEffect(Unit) {
            navController.currentBackStack.collect { backstack ->
                Logger.debug("Navigation") {
                    "Current back stack: ${backstack.joinToString { it.destination.route ?: "unknown" }}"
                }
            }
        }

        val showLockScreen by userActivityManager.showLockScreen.collectAsStateWithLifecycle()
        if (showLockScreen) {
            LaunchedEffect(Unit) {
                navigateToVerifyPinCodeScreen(navController)
            }
        }
        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = state.numberOfWallets >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                val startDestination = state.initialRoute ?: Destination.OnBoardingScreen

                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    navController = navController,
                    startDestination = startDestination,
                ) {
                    composable<Destination.OnBoardingScreen> {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize(),
                            onImportWallet = { navController.navigate(Destination.ImportWalletScreen) },
                            onCreateWallet = { navController.navigate(Destination.CreateWalletScreen) },
                        )
                    }

                    composable<Destination.CreateWalletScreen> {
                        CreateWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navController.navigateUp() },
                            onMnemonicSaved = { navController.navigate(Destination.SetPinCodeScreen) },
                        )
                    }

                    composable<Destination.ImportWalletScreen> {
                        ImportWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navController.navigateUp() },
                            onWalletImported = { navController.navigate(Destination.SetPinCodeScreen) },
                        )
                    }

                    composable<Destination.WalletScreen> {
                        WalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onManageWallets = { navController.navigate(Destination.WalletsListScreen) },
                            onEditButtonClick = { },
                            onTokenClick = { },
                        )
                    }

                    composable<Destination.WalletsListScreen> {
                        WalletsListScreen(
                            modifier = Modifier.fillMaxSize(),
                            navigateToOnboarding = { navigateToOnboardingScreen(navController) },
                            navigateToWalletDetails = { navigateToWalletScreen(navController) },
                        )
                    }

                    composable<Destination.SetPinCodeScreen> {
                        SetPinCodeScreen(
                            modifier = Modifier.fillMaxSize(),
                            onPinCodeSet = { navigateToWalletScreen(navController) },
                        )
                    }

                    composable<Destination.VerifyPinCodeScreen> {
                        VerifyPinCodeScreen(
                            onLogout = { navigateToOnboardingScreen(navController) },
                            onPinVerified = { navigateToWalletScreen(navController) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToVerifyPinCodeScreen(navController: NavHostController) {
    navController.navigate(Destination.VerifyPinCodeScreen) {
        // pop up to root
        popUpTo(0) {
            inclusive = false
        }
    }
}

private fun navigateToOnboardingScreen(navController: NavHostController) {
    navController.navigate(Destination.OnBoardingScreen) {
        popUpTo(navController.graph.findStartDestination().id) {
            inclusive = true
        }
    }
}

private fun navigateToWalletScreen(navController: NavController) {
    navController.navigate(Destination.WalletScreen) {
        popUpTo(navController.graph.findStartDestination().id) {
            inclusive = true
        }
    }
}
