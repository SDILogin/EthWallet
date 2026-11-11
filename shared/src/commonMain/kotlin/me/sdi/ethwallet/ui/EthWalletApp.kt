package me.sdi.ethwallet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.ui.designsystem.EthText
import me.sdi.ethwallet.ui.history.HistoryRoute
import me.sdi.ethwallet.ui.history.TransactionDetailsRoute
import me.sdi.ethwallet.ui.navigation.Destination
import me.sdi.ethwallet.ui.receive.ReceiveScreen
import me.sdi.ethwallet.ui.send.SendScreen
import me.sdi.ethwallet.ui.settings.ChangePinCodeScreen
import me.sdi.ethwallet.ui.settings.SettingsScreen
import me.sdi.ethwallet.ui.theme.EthWalletTheme
import me.sdi.ethwallet.ui.wallet.CreateWalletScreen
import me.sdi.ethwallet.ui.wallet.ImportWalletScreen
import me.sdi.ethwallet.ui.wallet.PasswordUnlockScreen
import me.sdi.ethwallet.ui.wallet.SetPasswordScreen
import me.sdi.ethwallet.ui.wallet.SetPinCodeScreen
import me.sdi.ethwallet.ui.wallet.TokenDetailsScreen
import me.sdi.ethwallet.ui.wallet.TokensListScreen
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeScreen
import me.sdi.ethwallet.ui.wallet.WalletScreen
import me.sdi.ethwallet.ui.wallet.WalletsListScreen
import org.koin.compose.getKoin

@Composable
fun EthWalletApp() {
    EthWalletTheme {
        val backStack = rememberNavBackStack(
            configuration = SavedStateConfiguration {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        @OptIn(ExperimentalSerializationApi::class)
                        subclassesOfSealed<Destination>()
                    }
                }
            },
            Destination.SplashScreen,
        )
        var activeWalletAddress by rememberSaveable { mutableStateOf<String?>(null) }

        fun popToWalletsList() {
            val walletsListIndex = backStack.indexOf(Destination.WalletsListScreen)
            check(walletsListIndex >= 0)
            backStack.subList(walletsListIndex + 1, backStack.size).clear()
        }

        val koin = getKoin()
        val appLockMethod = koin.get<AppLockMethod>()
        val isAuthenticatedFlow = snapshotFlow {
            backStack.any { it is Destination.WalletsListScreen }
        }
        LaunchedEffect(Unit) {
            combine(
                koin.get<PinSession>().isUnlocked,
                isAuthenticatedFlow,
            ) { unlocked, isAuthenticated ->
                !unlocked && isAuthenticated
            }
                .distinctUntilChanged()
                .collect { showLockScreen ->
                    if (showLockScreen) {
                        activeWalletAddress = null
                        backStack.clear()
                        backStack.add(Destination.LockScreen)
                    }
                }
        }

        val entryProvider = entryProvider<NavKey> {
            entry<Destination.SplashScreen> {
                SplashScreen(
                    modifier = Modifier.fillMaxSize(),
                    startDestination = { destination ->
                        backStack.clear()
                        backStack.add(destination)
                    },
                )
            }

            entry<Destination.LockScreen> {
                val onUnlocked: () -> Unit = {
                    backStack.clear()
                    backStack.add(Destination.WalletsListScreen)
                }
                val onLogout: () -> Unit = {
                    backStack.clear()
                    backStack.add(Destination.OnBoardingScreen)
                }
                when (appLockMethod) {
                    AppLockMethod.Pin -> VerifyPinCodeScreen(
                        modifier = Modifier.fillMaxSize(),
                        onUnlocked = onUnlocked,
                        onLogout = onLogout,
                    )
                    AppLockMethod.Password -> PasswordUnlockScreen(
                        modifier = Modifier.fillMaxSize(),
                        onUnlocked = onUnlocked,
                        onLogout = onLogout,
                    )
                }
            }

            entry<Destination.OnBoardingScreen> {
                OnboardingScreen(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    onContinueClick = {
                        when (appLockMethod) {
                            AppLockMethod.Pin -> backStack.add(Destination.SetPinCodeScreen)
                            AppLockMethod.Password -> backStack.add(Destination.SetPasswordScreen)
                        }
                    },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.WalletsListScreen>(
                metadata = listPane(
                    detailPlaceholder = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EthText(
                                text = "No wallet has been selected yet",
                            )
                        }
                    },
                ),
            ) {
                WalletsListScreen(
                    modifier = Modifier.fillMaxSize(),
                    selectedWalletAddress = activeWalletAddress?.let(::EthAddress),
                    onWalletSelected = { ethAddress ->
                        val destination = Destination.WalletScreen(ethAddress = ethAddress.toString())
                        if (backStack.lastOrNull() != destination) {
                            popToWalletsList()
                            backStack.add(destination)
                        }
                        activeWalletAddress = ethAddress.toString()
                    },
                    onWalletDeleted = { address ->
                        if (address == activeWalletAddress?.let(::EthAddress)) {
                            popToWalletsList()
                            activeWalletAddress = null
                        }
                    },
                    onImportWallet = { backStack.add(Destination.ImportWalletScreen) },
                    onCreateWallet = { backStack.add(Destination.CreateWalletScreen) },
                    onSettingsClick = { backStack.add(Destination.SettingsScreen) },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.CreateWalletScreen>(
                metadata = detailPane(),
            ) {
                CreateWalletScreen(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    onUpButtonClick = { backStack.removeLastOrNull() },
                    onMnemonicSaved = {
                        activeWalletAddress = null
                        backStack.clear()
                        backStack.add(Destination.WalletsListScreen)
                    },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.ImportWalletScreen>(
                metadata = detailPane(),
            ) {
                ImportWalletScreen(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    onUpButtonClick = { backStack.removeLastOrNull() },
                    onWalletImported = {
                        activeWalletAddress = null
                        backStack.clear()
                        backStack.add(Destination.WalletsListScreen)
                    },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.WalletScreen>(
                metadata = detailPane(),
            ) { destination ->
                WalletScreen(
                    modifier = Modifier.fillMaxSize(),
                    walletAddress = EthAddress(destination.ethAddress),
                    onUpButtonClick = { backStack.removeLastOrNull() },
                    onReceiveButtonClick = { backStack.add(Destination.ReceiveQrScreen(destination.ethAddress)) },
                    onSendButtonClick = { backStack.add(Destination.SendScreen(destination.ethAddress)) },
                    onHistoryButtonClick = { label ->
                        backStack.add(Destination.HistoryScreen(destination.ethAddress, label))
                    },
                    onEditButtonClick = { backStack.add(Destination.TokensListScreen) },
                    onTokenClick = { tokenAddress ->
                        backStack.add(Destination.TokenDetailsScreen(destination.ethAddress, tokenAddress.toString()))
                    },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.TokenDetailsScreen>(
                metadata = detailPane(),
            ) { destination ->
                TokenDetailsScreen(
                    modifier = Modifier.fillMaxSize(),
                    walletAddress = EthAddress(destination.walletAddress),
                    tokenAddress = EthAddress(destination.tokenAddress),
                    onSendButtonClick = { backStack.add(Destination.SendScreen(destination.walletAddress)) },
                    onReceiveButtonClick = { backStack.add(Destination.ReceiveQrScreen(destination.walletAddress)) },
                    onHistoryButtonClick = {
                        backStack.add(Destination.HistoryScreen(destination.walletAddress, destination.walletAddress))
                    },
                    onUpButtonClick = { backStack.removeLastOrNull() },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.TokensListScreen>(
                metadata = detailPane(),
            ) {
                TokensListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.ReceiveQrScreen>(
                metadata = detailPane(),
            ) { destination ->
                ReceiveScreen(
                    walletAddress = EthAddress(destination.ethAddress),
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.HistoryScreen>(
                metadata = detailPane(),
            ) { destination ->
                HistoryRoute(
                    walletAddress = EthAddress(destination.ethAddress),
                    walletLabel = destination.walletLabel,
                    onTransactionClick = { hash, logIndex ->
                        backStack.add(Destination.TransactionDetails(destination.ethAddress, hash, logIndex))
                    },
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.TransactionDetails>(
                metadata = detailPane(),
            ) { destination ->
                TransactionDetailsRoute(
                    walletAddress = EthAddress(destination.ethAddress),
                    transactionHash = destination.transactionHash,
                    logIndex = destination.logIndex,
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.SendScreen>(
                metadata = detailPane(),
            ) { destination ->
                SendScreen(
                    modifier = Modifier.fillMaxSize(),
                    walletAddress = EthAddress(destination.ethAddress),
                    onBackPress = { backStack.removeLastOrNull() },
                )
            }

            entry<Destination.SetPinCodeScreen> {
                SetPinCodeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPinCodeSet = {
                        backStack.clear()
                        backStack.add(Destination.WalletsListScreen)
                    },
                )
            }

            entry<Destination.SetPasswordScreen> {
                SetPasswordScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPasswordSet = {
                        backStack.clear()
                        backStack.add(Destination.WalletsListScreen)
                    },
                )
            }

            entry<Destination.SettingsScreen> {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    onChangePinClick = { backStack.add(Destination.ChangePinCodeScreen) },
                )
            }

            entry<Destination.ChangePinCodeScreen> {
                ChangePinCodeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    onPinChanged = { backStack.removeLastOrNull() },
                )
            }
        }

        @OptIn(ExperimentalMaterial3AdaptiveApi::class)
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            entryProvider = entryProvider,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(
                rememberListDetailSceneStrategy(
                    backNavigationBehavior = BackNavigationBehavior.PopLatest,
                    directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
                        .copy(horizontalPartitionSpacerSize = 0.dp),
                ),
            ),
        )
    }
}
