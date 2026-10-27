package me.sdi.ethwallet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.extraPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import me.sdi.ethwallet.core.security.PinSession
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
import me.sdi.ethwallet.ui.wallet.SetPinCodeScreen
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

        val koin = getKoin()
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
                VerifyPinCodeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onUnlocked = {
                        backStack.clear()
                        backStack.add(Destination.WalletsListScreen)
                    },
                    onLogout = {
                        backStack.clear()
                        backStack.add(Destination.OnBoardingScreen)
                    },
                )
            }

            entry<Destination.OnBoardingScreen> {
                OnboardingScreen(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    onCreatePinClick = { backStack.add(Destination.SetPinCodeScreen) },
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
                            Text(
                                text = "No wallet has been selected yet",
                            )
                        }
                    },
                ),
            ) {
                val selectedWalletAddress = (backStack.lastOrNull() as? Destination.WalletScreen)
                    ?.ethAddress
                    ?.let(::EthAddress)
                WalletsListScreen(
                    modifier = Modifier.fillMaxSize(),
                    selectedWalletAddress = selectedWalletAddress,
                    onWalletSelected = { ethAddress ->
                        val destination = Destination.WalletScreen(ethAddress = ethAddress.toString())
                        val currentDestination = backStack.lastOrNull()
                        if (currentDestination == destination) {
                            return@WalletsListScreen
                        }
                        if (currentDestination is Destination.WalletScreen) {
                            backStack.removeLastOrNull()
                        }
                        backStack.add(destination)
                    },
                    onWalletDeleted = { address ->
                        if ((backStack.lastOrNull() as? Destination.WalletScreen)?.ethAddress == address.toString()) {
                            backStack.removeLastOrNull()
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
                    onTokenClick = { },
                )
            }

            entry<Destination.TokensListScreen> {
                TokensListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            entry<Destination.ReceiveQrScreen> { destination ->
                ReceiveScreen(
                    walletAddress = EthAddress(destination.ethAddress),
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            entry<Destination.HistoryScreen> { destination ->
                HistoryRoute(
                    walletAddress = EthAddress(destination.ethAddress),
                    walletLabel = destination.walletLabel,
                    onTransactionClick = { hash, logIndex ->
                        backStack.add(Destination.TransactionDetails(destination.ethAddress, hash, logIndex))
                    },
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            entry<Destination.TransactionDetails> { destination ->
                TransactionDetailsRoute(
                    walletAddress = EthAddress(destination.ethAddress),
                    transactionHash = destination.transactionHash,
                    logIndex = destination.logIndex,
                    onBackPressed = { backStack.removeLastOrNull() },
                )
            }

            entry<Destination.SendScreen> { destination ->
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

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.SettingsScreen>(
                metadata = extraPane(),
            ) {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    onChangePinClick = { backStack.add(Destination.ChangePinCodeScreen) },
                )
            }

            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            entry<Destination.ChangePinCodeScreen>(
                metadata = extraPane(),
            ) {
                ChangePinCodeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    onPinChanged = { backStack.removeLastOrNull() },
                    onLogout = {
                        backStack.clear()
                        backStack.add(Destination.OnBoardingScreen)
                    },
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
                rememberListDetailSceneStrategy(),
            ),
        )
    }
}
