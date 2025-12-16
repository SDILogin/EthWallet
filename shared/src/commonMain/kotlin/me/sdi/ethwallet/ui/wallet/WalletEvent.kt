package me.sdi.ethwallet.ui.wallet

sealed interface WalletEvent {
    data object Initialize : WalletEvent

    data object Refresh : WalletEvent
}
