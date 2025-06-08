package me.sdi.ethwallet.core

import java.math.BigDecimal

/**
 * Ethereum price data source. Must be used to fetch the current ETH price
 * and display it to the user.
 */
interface EthPriceProvider {
    /**
     * Returns the current ETH price.
     */
    suspend fun getPrice(): BigDecimal
}
