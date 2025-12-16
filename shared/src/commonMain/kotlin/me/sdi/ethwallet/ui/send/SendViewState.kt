package me.sdi.ethwallet.ui.send

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.GasParams

data class SendViewState(
    val selectedTokenContractAddress: EthAddress = EthContractAddress,
    val receiverAddress: AddressInputState = AddressInputState(),
    val amount: AmountInputState = AmountInputState(),
    val isLoading: Boolean = false,
    val gasParams: GasParams = GasParams.Default,
) {
    val isTransferEnabled: Boolean =
        !isLoading &&
            receiverAddress.isValid &&
            amount.isValid &&
            receiverAddress.value.isNotBlank() &&
            amount.value.isNotBlank()
}

data class AddressInputState(
    val value: String = "",
    val error: String? = null,
) {
    val isValid: Boolean = error == null && value.isNotBlank()
}

data class AmountInputState(
    val value: String = "",
    val error: String? = null,
) {
    val isValid: Boolean = error == null && value.isNotBlank()
}
