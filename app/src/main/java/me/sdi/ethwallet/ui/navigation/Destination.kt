package me.sdi.ethwallet.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object OnBoardingScreen : Destination

    @Serializable
    data object CreateWalletScreen : Destination

    @Serializable
    data object ImportWalletScreen : Destination

    @Serializable
    data object WalletScreen : Destination

    @Serializable
    data object WalletsListScreen : Destination

    @Serializable
    data object SetPinCodeScreen : Destination

    @Serializable
    data object VerifyPinCodeScreen : Destination

    @Serializable
    data object ReceiveQrScreen : Destination

    @Serializable
    data object SendScreen : Destination
}
