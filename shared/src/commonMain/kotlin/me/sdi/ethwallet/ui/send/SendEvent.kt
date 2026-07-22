package me.sdi.ethwallet.ui.send

import me.sdi.ethwallet.core.EthAddress

sealed interface SendEvent {
    data class OnTokenSelected(val contractAddress: EthAddress) : SendEvent

    data class OnReceiverAddressChanged(val address: String) : SendEvent

    data class OnTransferAmountChanged(val amount: String) : SendEvent

    data class OnGasOptionSelected(val option: GasOption) : SendEvent

    data class OnBaseFeeChanged(val value: String) : SendEvent

    data class OnPriorityFeeChanged(val value: String) : SendEvent

    data class OnGasUnitsChanged(val value: String) : SendEvent

    data object OnSendClick : SendEvent

    data object OnClearFormClick : SendEvent
}
