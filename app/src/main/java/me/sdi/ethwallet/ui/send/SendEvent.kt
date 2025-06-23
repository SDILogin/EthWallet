package me.sdi.ethwallet.ui.send

import me.sdi.ethwallet.core.EthAddress

sealed interface SendEvent {
    data class OnTokenSelected(val contractAddress: EthAddress) : SendEvent

    data class OnReceiverAddressChanged(val address: String) : SendEvent

    data class OnAmountChanged(val amount: String) : SendEvent

    object OnTransferRequested : SendEvent

    object OnClearForm : SendEvent
}
