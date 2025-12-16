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
        fun saver(): Saver<NavigationState, Any> = listSaver(
            save = { state ->
                state.backStack.map { destination ->
                    when (destination) {
                        Destination.OnBoardingScreen -> "OnBoardingScreen"
                        Destination.CreateWalletScreen -> "CreateWalletScreen"
                        Destination.ImportWalletScreen -> "ImportWalletScreen"
                        Destination.WalletScreen -> "WalletScreen"
                        Destination.WalletsListScreen -> "WalletsListScreen"
                        Destination.SetPinCodeScreen -> "SetPinCodeScreen"
                        Destination.VerifyPinCodeScreen -> "VerifyPinCodeScreen"
                        Destination.ReceiveQrScreen -> "ReceiveQrScreen"
                        Destination.SendScreen -> "SendScreen"
                    }
                }
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val names = saved as List<String>
                NavigationState(
                    names.map { name ->
                        when (name) {
                            "OnBoardingScreen" -> Destination.OnBoardingScreen
                            "CreateWalletScreen" -> Destination.CreateWalletScreen
                            "ImportWalletScreen" -> Destination.ImportWalletScreen
                            "WalletScreen" -> Destination.WalletScreen
                            "WalletsListScreen" -> Destination.WalletsListScreen
                            "SetPinCodeScreen" -> Destination.SetPinCodeScreen
                            "VerifyPinCodeScreen" -> Destination.VerifyPinCodeScreen
                            "ReceiveQrScreen" -> Destination.ReceiveQrScreen
                            "SendScreen" -> Destination.SendScreen
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
