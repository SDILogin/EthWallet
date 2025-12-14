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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.navigation.Navigator
import me.sdi.ethwallet.ui.navigation.rememberNavigationState
import me.sdi.ethwallet.ui.receive.ReceiveScreen
import me.sdi.ethwallet.ui.send.SendScreen
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

        val showLockScreen by userActivityManager.showLockScreen.collectAsStateWithLifecycle()

        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = state.numberOfWallets >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val startDestination = state.initialRoute ?: Destination.OnBoardingScreen
            val navigationState = rememberNavigationState(startDestination)
            val navigator = remember(navigationState) { Navigator(navigationState) }

            if (showLockScreen) {
                LaunchedEffect(Unit) {
                    navigator.navigateToVerifyPinCode()
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                val entryProvider = entryProvider<Destination>(
                    fallback = { key ->
                        NavEntry(key) { }
                    },
                ) {
                    entry<Destination.OnBoardingScreen> {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize(),
                            onImportWallet = {
                                navigator.navigate(Destination.ImportWalletScreen)
                            },
                            onCreateWallet = {
                                navigator.navigate(Destination.CreateWalletScreen)
                            },
                        )
                    }

                    entry<Destination.CreateWalletScreen> {
                        CreateWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navigator.goBack() },
                            onMnemonicSaved = {
                                navigator.navigate(Destination.SetPinCodeScreen)
                            },
                        )
                    }

                    entry<Destination.ImportWalletScreen> {
                        ImportWalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onUpButtonClick = { navigator.goBack() },
                            onWalletImported = {
                                navigator.navigate(Destination.SetPinCodeScreen)
                            },
                        )
                    }

                    entry<Destination.WalletScreen> {
                        WalletScreen(
                            modifier = Modifier.fillMaxSize(),
                            onManageWallets = {
                                navigator.navigate(Destination.WalletsListScreen)
                            },
                            onReceiveButtonClick = {
                                navigator.navigate(Destination.ReceiveQrScreen)
                            },
                            onSendButtonClick = {
                                navigator.navigate(Destination.SendScreen)
                            },
                            onEditButtonClick = { },
                            onTokenClick = { },
                        )
                    }

                    entry<Destination.ReceiveQrScreen> {
                        ReceiveScreen(
                            onBackPressed = { navigator.goBack() },
                        )
                    }

                    entry<Destination.SendScreen> {
                        SendScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBackPress = { navigator.goBack() },
                        )
                    }

                    entry<Destination.WalletsListScreen> {
                        WalletsListScreen(
                            modifier = Modifier.fillMaxSize(),
                            navigateToOnboarding = {
                                navigator.navigateAndClearStack(Destination.OnBoardingScreen)
                            },
                            navigateToWalletDetails = {
                                navigator.navigateAndClearStack(Destination.WalletScreen)
                            },
                        )
                    }

                    entry<Destination.SetPinCodeScreen> {
                        SetPinCodeScreen(
                            modifier = Modifier.fillMaxSize(),
                            onPinCodeSet = {
                                navigator.navigateAndClearStack(Destination.WalletScreen)
                            },
                        )
                    }

                    entry<Destination.VerifyPinCodeScreen> {
                        VerifyPinCodeScreen(
                            onLogout = {
                                navigator.navigateAndClearStack(Destination.OnBoardingScreen)
                            },
                            onPinVerified = {
                                navigator.navigateAndClearStack(Destination.WalletScreen)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                NavDisplay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    backStack = navigationState.backStack,
                    entryProvider = entryProvider,
                    onBack = { navigator.goBack() },
                )
            }
        }
    }
}
