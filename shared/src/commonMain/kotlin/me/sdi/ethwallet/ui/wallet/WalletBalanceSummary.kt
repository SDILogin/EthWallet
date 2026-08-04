package me.sdi.ethwallet.ui.wallet

import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.core.transfer.toEth
import me.sdi.ethwallet.ui.supportedKnownTokens
import me.sdi.ethwallet.util.formatUsdCurrency
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal data class WalletBalanceSummary(
    val totalBalanceEth: String,
    val totalBalanceUsd: String?,
    val favoriteTokens: List<FavoriteToken>,
)

internal fun mapWalletBalance(
    balances: Map<EthAddress, BigInteger>,
    fiatRates: Map<EthAddress, BigDecimal>,
    chainId: Long,
): WalletBalanceSummary {
    val ethBalance = Wei(balances[EthContractAddress] ?: BigInteger.ZERO).toEth().value
    val knownTokens = supportedKnownTokens(chainId)
    val favoriteTokens = knownTokens.map { tokenInfo ->
        val tokenAmount = BigDecimal(balances[tokenInfo.ethAddress] ?: BigInteger.ZERO).divide(
            BigDecimal.TEN.pow(tokenInfo.decimals),
            tokenInfo.decimals,
            RoundingMode.DOWN,
        )
        val tokenUsdValue = fiatRates[tokenInfo.ethAddress]?.let(tokenAmount::multiply)

        FavoriteToken(
            address = tokenInfo.ethAddress,
            name = tokenInfo.tokenName,
            ticker = tokenInfo.symbol,
            amount = formatTokenAmount(tokenAmount),
            usdValue = tokenUsdValue,
            usdValueFormatted = tokenUsdValue?.let(::formatUsdCurrency) ?: "N/A",
        )
    }
    val hasRequiredFiatRates = fiatRates.containsKey(EthContractAddress) &&
        knownTokens.all { tokenInfo ->
            balances[tokenInfo.ethAddress] == null ||
                balances.getValue(tokenInfo.ethAddress) == BigInteger.ZERO ||
                fiatRates.containsKey(tokenInfo.ethAddress)
        }
    val totalBalanceUsd = if (hasRequiredFiatRates) {
        val ethUsdValue = ethBalance.multiply(fiatRates.getValue(EthContractAddress))
        val tokenUsdValue = favoriteTokens.sumOf { it.usdValue ?: BigDecimal.ZERO }
        formatUsdCurrency(ethUsdValue.add(tokenUsdValue))
    } else {
        null
    }

    return WalletBalanceSummary(
        totalBalanceEth = "$ethBalance ETH",
        totalBalanceUsd = totalBalanceUsd,
        favoriteTokens = favoriteTokens,
    )
}

private fun formatTokenAmount(amount: BigDecimal): String {
    return when {
        amount == BigDecimal.ZERO -> "0"
        amount < BigDecimal.ONE -> amount.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        else -> amount.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    }
}
