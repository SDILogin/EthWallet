package me.sdi.ethwallet.ui.receive

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.ui.KnownTokens
import java.math.BigDecimal

data class ReceiveQrViewState(
    val selectedTokenContract: EthAddress? = null,
    val qrPayload: String? = null,
    val amount: String = "",
    val walletAddress: EthAddress? = null,
) {
    val amountError: String? = getAmountError(
        selectedTokenContractAddress = selectedTokenContract,
        amount = amount.toBigDecimalOrNull(),
    )
}

private fun getAmountError(selectedTokenContractAddress: EthAddress?, amount: BigDecimal?): String? {
    return when {
        amount == null -> null
        amount <= BigDecimal.ZERO -> "Amount must be greater than zero"
        amount.scale() > getMaxDecimalPlaces(selectedTokenContractAddress) -> "Too many decimal places for selected token"
        else -> null
    }
}

private fun getMaxDecimalPlaces(selectedTokenAddress: EthAddress?): Int {
    return when (selectedTokenAddress) {
        EthContractAddress -> 18
        else ->
            KnownTokens
                .entries
                .find { it.ethAddress == selectedTokenAddress }
                ?.decimals ?: 18
    }
}
