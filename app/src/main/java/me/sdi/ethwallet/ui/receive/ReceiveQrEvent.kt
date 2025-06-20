package me.sdi.ethwallet.ui.receive

import me.sdi.ethwallet.core.EthAddress

sealed interface ReceiveQrEvent {
    data class OnTokenSelected(val tokenContractAddress: EthAddress) : ReceiveQrEvent

    data class OnAmountChanged(val amount: String) : ReceiveQrEvent

    object OnClearAmount : ReceiveQrEvent

    object OnCopyAddress : ReceiveQrEvent

    object OnBackPressed : ReceiveQrEvent
}
