package me.sdi.ethwallet.ui.receive

sealed interface ReceiveQrNews {
    data class ShowToast(val message: String) : ReceiveQrNews
}
