package me.sdi.ethwallet.ui.wallet

sealed interface WalletEvent {
    data object Refresh : WalletEvent

    data object Retry : WalletEvent
}
