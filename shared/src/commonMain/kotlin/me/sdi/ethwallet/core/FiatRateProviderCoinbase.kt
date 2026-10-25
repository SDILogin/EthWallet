package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.allKnownTokens
import java.math.BigDecimal

private const val TAG = "FiatRateProviderCoinbase"

class FiatRateProviderCoinbase(
    private val httpClient: HttpClient,
    private val fiatRateDao: FiatRateDao,
    private val now: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
) : FiatRateProvider {
    private val addressesBySymbol = allKnownTokens.groupBy({ it.symbol }, { it.ethAddress }) +
        ("ETH" to listOf(EthContractAddress))

    override suspend fun forceUpdate() {
        try {
            val rows = coroutineScope {
                addressesBySymbol.map { (symbol, addresses) ->
                    async {
                        val quote = httpClient.get("https://api.coinbase.com/v2/prices/$symbol-USD/spot")
                            .body<SpotPriceResponse>().data
                        require(quote.currency == "USD") { "Expected a USD quote for $symbol" }
                        val rate = BigDecimal(quote.amount)
                        require(rate.signum() > 0) { "Expected a positive USD rate for $symbol" }
                        addresses.map { address ->
                            FiatRateEntity(
                                tokenAddress = address.toString(),
                                lastUpdatedTimestamp = now(),
                                rate = rate.toPlainString(),
                            )
                        }
                    }
                }.awaitAll().flatten()
            }
            // Keep the previous quotes if any request fails.
            fiatRateDao.insertAll(rows)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG) { "Failed to force update fiat rates: ${e.message}" }
            throw e
        }
    }

    override fun observe(): Flow<Map<EthAddress, BigDecimal>> = fiatRateDao
        .observeAll(addressesBySymbol.values.flatten().map { it.toString() })
        .map { rows -> rows.associate { EthAddress(it.tokenAddress) to BigDecimal(it.rate) } }
}

@Serializable
private data class SpotPriceResponse(val data: SpotPrice)

@Serializable
private data class SpotPrice(val amount: String, val currency: String)
