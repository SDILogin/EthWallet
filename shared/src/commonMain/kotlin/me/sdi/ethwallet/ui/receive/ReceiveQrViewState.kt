package me.sdi.ethwallet.ui.receive

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.findKnownToken
import java.math.BigDecimal

data class ReceiveQrViewState(
    val selectedTokenContract: EthAddress = EthContractAddress,
    val qrPayload: String? = null,
    val amount: String = "",
    val walletAddress: EthAddress? = null,
) {
    val amountError: String? = getAmountError(
        selectedTokenContractAddress = selectedTokenContract,
        rawAmount = amount,
    )
}

private fun getAmountError(selectedTokenContractAddress: EthAddress, rawAmount: String): String? {
    val amount = rawAmount.toBigDecimalOrNull()
    return when {
        rawAmount.isBlank() -> null
        amount == null -> "Invalid amount"
        amount <= BigDecimal.ZERO -> "Amount must be greater than zero"
        amount.scale() > getMaxDecimalPlaces(selectedTokenContractAddress) -> "Too many decimal places for selected token"
        else -> null
    }
}

private fun getMaxDecimalPlaces(selectedTokenAddress: EthAddress): Int {
    return findKnownToken(selectedTokenAddress)?.decimals ?: 18
}
