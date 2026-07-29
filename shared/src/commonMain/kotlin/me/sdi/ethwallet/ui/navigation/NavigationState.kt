package me.sdi.ethwallet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

class NavigationState(
    initialBackStack: List<Destination>,
) {
    val backStack: SnapshotStateList<Destination> =
        mutableStateListOf(*initialBackStack.toTypedArray())

    companion object {
        /**
         * A restored back stack is never trusted to decide whether the app is unlocked: the PIN
         * screen is an overlay driven by the session key, not a destination, so a stack saved while
         * it was on screen restores to the wallet behind the lock.
         */
        fun saver(): Saver<NavigationState, Any> = listSaver(
            save = { state ->
                state.backStack.map { destination ->
                    when (destination) {
                        Destination.SplashScreen -> "SplashScreen"
                        Destination.OnBoardingScreen -> "OnBoardingScreen"
                        Destination.CreateWalletScreen -> "CreateWalletScreen"
                        Destination.ImportWalletScreen -> "ImportWalletScreen"
                        Destination.WalletScreen -> "WalletScreen"
                        Destination.WalletsListScreen -> "WalletsListScreen"
                        Destination.SetPinCodeScreen -> "SetPinCodeScreen"
                        Destination.ReceiveQrScreen -> "ReceiveQrScreen"
                        Destination.SendScreen -> "SendScreen"
                        Destination.SettingsScreen -> "SettingsScreen"
                    }
                }
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val names = saved as List<String>
                NavigationState(
                    names.map { name ->
                        when (name) {
                            "SplashScreen" -> Destination.SplashScreen
                            "OnBoardingScreen" -> Destination.OnBoardingScreen
                            "CreateWalletScreen" -> Destination.CreateWalletScreen
                            "ImportWalletScreen" -> Destination.ImportWalletScreen
                            "WalletScreen" -> Destination.WalletScreen
                            "WalletsListScreen" -> Destination.WalletsListScreen
                            "SetPinCodeScreen" -> Destination.SetPinCodeScreen
                            "VerifyPinCodeScreen" -> Destination.WalletScreen
                            "ReceiveQrScreen" -> Destination.ReceiveQrScreen
                            "SendScreen" -> Destination.SendScreen
                            "SettingsScreen" -> Destination.SettingsScreen
                            else -> Destination.OnBoardingScreen
                        }
                    },
                )
            },
        )
    }
}

@Composable
fun rememberNavigationState(startDestination: Destination): NavigationState {
    return rememberSaveable(saver = NavigationState.saver()) {
        NavigationState(listOf(startDestination))
    }
}
