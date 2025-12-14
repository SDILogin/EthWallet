package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * Price provider for fiat currencies, specifically for Ethereum (ETH).
 */
interface FiatRateProvider {
    /**
     * Forces an update of fiat rates from the network and saves to database.
     */
    suspend fun forceUpdate()

    /**
     * Observes fiat rates from the database for the given token addresses.
     *
     * @return Flow emitting a map of token addresses to their USD rates.
     */
    fun observe(): Flow<Map<EthAddress, BigDecimal>>
}
