package me.sdi.ethwallet.ui.receive

sealed interface ReceiveQrEvent {
    data class OnTokenSelected(val token: SelectedToken) : ReceiveQrEvent

    data class OnAmountChanged(val amount: String) : ReceiveQrEvent

    object OnGenerateQrCode : ReceiveQrEvent

    object OnClearAmount : ReceiveQrEvent

    object OnCopyAddress : ReceiveQrEvent

    object OnBackPressed : ReceiveQrEvent
}
