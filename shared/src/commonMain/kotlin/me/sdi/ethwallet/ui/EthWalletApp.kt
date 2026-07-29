package me.sdi.ethwallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.navigation.Navigator
import me.sdi.ethwallet.ui.navigation.rememberNavigationState
import me.sdi.ethwallet.ui.receive.ReceiveScreen
import me.sdi.ethwallet.ui.send.SendScreen
import me.sdi.ethwallet.ui.settings.SettingsScreen
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.CreateWalletScreen
import me.sdi.ethwallet.ui.wallet.ImportWalletScreen
import me.sdi.ethwallet.ui.wallet.SetPinCodeScreen
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeScreen
import me.sdi.ethwallet.ui.wallet.WalletScreen
import me.sdi.ethwallet.ui.wallet.WalletsListScreen

@Composable
fun EthWalletApp(viewModel: InitialNavigationViewModel) {
    EthWalletTheme {
        val state by viewModel.state.collectAsState()

        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = state.numberOfWallets >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val startDestination: Destination = when (state.initialRoute) {
                InitialRoute.OnBoarding -> Destination.OnBoardingScreen
                InitialRoute.Wallet -> Destination.WalletScreen
                null -> Destination.OnBoardingScreen
            }
            val navigationState = rememberNavigationState(startDestination)
            val navigator = remember(navigationState) { Navigator(navigationState) }

            var wasLocked by remember { mutableStateOf(state.isLocked) }
            LaunchedEffect(state.isLocked) {
                if (wasLocked && !state.isLocked && state.isPinCodeSet) {
                    navigator.navigateAndClearStack(Destination.WalletScreen)
                }
                wasLocked = state.isLocked
            }

            if (state.isLocked) {
                FreshViewModelStoreOwner {
                    VerifyPinCodeScreen(
                        modifier = Modifier.fillMaxSize(),
                        onLogout = {
                            viewModel.onEvent(InitialNavigationEvent.WalletsCleared)
                            navigator.navigateAndClearStack(Destination.OnBoardingScreen)
                        },
                    )
                }
                return@AnimatedVisibility
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val entryProvider = entryProvider {
                    entry<Destination.OnBoardingScreen> {
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
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
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
                            onUpButtonClick = { navigator.goBack() },
                            onMnemonicSaved = {
                                navigator.navigate(Destination.SetPinCodeScreen)
                            },
                        )
                    }

                    entry<Destination.ImportWalletScreen> {
                        ImportWalletScreen(
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
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
                            onSettingsClick = {
                                navigator.navigate(Destination.SettingsScreen)
                            },
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
                                viewModel.onEvent(InitialNavigationEvent.WalletsCleared)
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

                    entry<Destination.SettingsScreen> {
                        SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { navigator.goBack() },
                        )
                    }
                }

                @OptIn(ExperimentalMaterial3AdaptiveApi::class)
                NavDisplay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    backStack = navigationState.backStack,
                    entryProvider = entryProvider,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    sceneStrategies = listOf(
                        rememberListDetailSceneStrategy(),
                    ),
                    onBack = { navigator.goBack() },
                )
            }
        }
    }
}

/**
 * Runs [content] against a [ViewModelStore] that lives exactly as long as this composable stays in
 * the composition. The lock screen is shown once per lock, so its ViewModel must not survive into
 * the next one carrying the entered digits, the logout flag, or a stale attempt and biometric
 * status read at init.
 */
@Composable
private fun FreshViewModelStoreOwner(content: @Composable () -> Unit) {
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    DisposableEffect(viewModelStoreOwner) {
        onDispose { viewModelStoreOwner.viewModelStore.clear() }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner, content = content)
}
