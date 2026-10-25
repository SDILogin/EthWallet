package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * USD price provider for ETH and supported tokens.
 */
interface FiatRateProvider {
    /**
     * Forces an update of fiat rates from the network and saves to database.
     */
    suspend fun forceUpdate()

    /**
     * Observes cached fiat rates. Tokens without quotes are omitted.
     *
     * @return Flow emitting a map of token addresses to their USD rates.
     */
    fun observe(): Flow<Map<EthAddress, BigDecimal>>
}
