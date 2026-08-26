package me.sdi.ethwallet.ui.wallet

sealed interface WalletNews {
    data object ShowErrorMessage : WalletNews
}
