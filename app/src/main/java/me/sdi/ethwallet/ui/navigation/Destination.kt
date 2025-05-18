package me.sdi.ethwallet.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object OnBoardingScreen

    @Serializable
    data object CreateWalletScreen

    @Serializable
    data object ImportWalletScreen

    @Serializable
    data object WalletScreen
}
