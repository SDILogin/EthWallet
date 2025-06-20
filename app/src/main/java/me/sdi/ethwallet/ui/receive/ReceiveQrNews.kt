package me.sdi.ethwallet.ui.receive

sealed interface ReceiveQrNews {
    data class ShowToast(val message: String) : ReceiveQrNews

    data class CopyToClipboard(val text: String) : ReceiveQrNews
}