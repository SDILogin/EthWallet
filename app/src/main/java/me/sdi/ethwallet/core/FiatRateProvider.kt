package me.sdi.ethwallet.core

import java.math.BigDecimal

/**
 * Price provider for fiat currencies, specifically for Ethereum (ETH).
 */
interface FiatRateProvider {
    /**
     * Fetches the current USD rates for a list of ERC-20 tokens.
     *
     * @param targetErc20TokenNames List of ERC-20 token names to fetch rates for.
     * @return A map where the keys are token symbols (e.g., "USDT", "DAI") or "ETH" for Ethereum itself,
     * and the values are their corresponding USD rates as [BigDecimal].
     */
    suspend fun getUsdRates(targetErc20TokenNames: List<String>): Map<String, BigDecimal>
}
