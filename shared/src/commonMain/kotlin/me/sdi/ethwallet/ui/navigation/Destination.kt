package me.sdi.ethwallet.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object SplashScreen : Destination

    @Serializable
    data object LockScreen : Destination

    @Serializable
    data object OnBoardingScreen : Destination

    @Serializable
    data object CreateWalletScreen : Destination

    @Serializable
    data object ImportWalletScreen : Destination

    @Serializable
    data class WalletScreen(val ethAddress: String) : Destination

    @Serializable
    data object WalletsListScreen : Destination

    @Serializable
    data object SetPinCodeScreen : Destination

    @Serializable
    data class ReceiveQrScreen(val ethAddress: String) : Destination

    @Serializable
    data class SendScreen(val ethAddress: String) : Destination

    @Serializable
    data class HistoryScreen(val ethAddress: String, val walletLabel: String) : Destination

    @Serializable
    data object SettingsScreen : Destination
}
