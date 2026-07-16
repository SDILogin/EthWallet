package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.allKnownTokens
import java.math.BigDecimal

private const val ETH_PRICE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=ETHUSDT"
private const val TAG = "CryptocompareFiatRateProvider"

class FiatRateProviderBinance(
    private val httpClient: HttpClient,
    private val fiatRateDao: FiatRateDao,
    private val now: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
) : FiatRateProvider {
    override suspend fun forceUpdate() {
        Logger.debug(TAG) { "Force update ETH price" }

        try {
            val ethPriceInUsdt = httpClient
                .get(ETH_PRICE_URL)
                .body<EthPriceResponse>()
                .price

            saveEthPriceToDb(ethPriceInUsdt)
            Logger.debug(TAG) { "Eth price (usdt): $ethPriceInUsdt" }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update fiat rates: ${e.message}"
            }
            throw e
        }
    }

    override fun observe(): Flow<Map<EthAddress, BigDecimal>> {
        return fiatRateDao
            .observeAll(listOf(EthContractAddress.toString()))
            .map { rows ->
                val eth = rows.firstOrNull() ?: return@map emptyMap()
                buildMap {
                    put(EthAddress(eth.tokenAddress), BigDecimal(eth.rate))

                    // stablecoins are pegged 1:1 to USDT
                    allKnownTokens.forEach { token ->
                        put(token.ethAddress, BigDecimal("1.0"))
                    }
                }
            }
    }

    private suspend fun saveEthPriceToDb(ethPriceInUsdt: Double) {
        fiatRateDao.insertAll(
            listOf(
                FiatRateEntity(
                    tokenAddress = EthContractAddress.toString(),
                    lastUpdatedTimestamp = now(),
                    rate = "%.2f".format(ethPriceInUsdt),
                ),
            ),
        )
    }
}

/**
 * {"symbol":"ETHUSDT","price":"1894.28000000"}
 */
@Serializable
private data class EthPriceResponse(
    @SerialName("price") val price: Double,
)
