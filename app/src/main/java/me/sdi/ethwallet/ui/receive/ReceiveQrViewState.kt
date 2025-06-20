package me.sdi.ethwallet.ui.receive

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.ui.KnownTokens

data class ReceiveQrViewState(
    val receiverAddress: EthAddress? = null,
    val selectedToken: SelectedToken = SelectedToken.ETH,
    val amount: String = "",
    val amountError: String? = null,
    val qrPayload: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface SelectedToken {
    object ETH : SelectedToken

    data class ERC20(val token: KnownTokens) : SelectedToken
}
