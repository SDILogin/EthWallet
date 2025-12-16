package me.sdi.ethwallet.ui.wallet

sealed interface WalletNews {
    data class ShowErrorMessage(val error: Throwable) : WalletNews
}
