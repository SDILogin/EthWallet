package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.allKnownTokens
import java.math.BigDecimal

private const val PRICE_URL = "https://min-api.cryptocompare.com/data/price?fsym=USD&tsyms="
private const val TAG = "CryptocompareFiatRateProvider"

class CryptocompareFiatRateProvider(
    private val httpClient: HttpClient,
    private val fiatRateDao: FiatRateDao,
    private val now: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
) : FiatRateProvider {
    override suspend fun forceUpdate() {
        val tokenAddresses = listOf(EthContractAddress) + allKnownTokens.map { it.ethAddress }
        Logger.debug(TAG) {
            "Forcing update of fiat rates for ${tokenAddresses.size} tokens"
        }

        try {
            val networkResponse = loadRatesFromNetwork(tokenAddresses)
            saveToDb(networkResponse)
            Logger.debug(TAG) {
                "Successfully updated fiat rates for ${networkResponse.size} tokens"
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Logger.warn(TAG) {
                "Failed to force update fiat rates: ${e.message}"
            }
            throw e
        }
    }

    override fun observe(): Flow<Map<EthAddress, BigDecimal>> {
        val tokenAddresses = listOf(EthContractAddress) + allKnownTokens.map { it.ethAddress }
        return fiatRateDao.observeAll(tokenAddresses.map { it.toString() }).map { fiatRateEntities ->
            Logger.debug(TAG) {
                "Observing fiat rates for ${tokenAddresses.size} tokens, found ${fiatRateEntities.size} entries"
            }
            fiatRateEntities.associate { entity ->
                val tokenAddress = EthAddress(entity.tokenAddress)
                val rate = BigDecimal(entity.rate)
                Logger.debug(TAG) {
                    "Token: $tokenAddress, Rate: $rate"
                }
                tokenAddress to rate
            }.also { result ->
                Logger.debug(TAG) {
                    "Observed fiat rates: ${result.size} tokens"
                }
            }
        }
    }

    private suspend fun saveToDb(networkResponse: Map<EthAddress, BigDecimal>) {
        val currentTime = now()
        val fiatRatesDbEntities = networkResponse.map { (ethAddress, rate) ->
            FiatRateEntity(
                tokenAddress = ethAddress.toString(),
                lastUpdatedTimestamp = currentTime,
                rate = rate.toString(),
            )
        }

        fiatRateDao.insertAll(fiatRatesDbEntities)
    }

    private suspend fun loadRatesFromNetwork(
        erc20ContractAddresses: List<EthAddress>,
    ): Map<EthAddress, BigDecimal> {
        val targetErc20TokenNames = allKnownTokens
            .filter { it.ethAddress in erc20ContractAddresses }
            .map { it.symbol }
            .distinct()
        val toSymString = createToSymString(targetErc20TokenNames + "ETH")

        val response: Map<String, Double> = httpClient.get(PRICE_URL + toSymString).body()
        return response.flatMap { (tokenSymbol, value) ->
            val rate = BigDecimal.valueOf(1.0 / value)
            val matchingAddresses = allKnownTokens
                .filter { it.symbol.equals(tokenSymbol, ignoreCase = true) }
                .map { it.ethAddress }
                .ifEmpty { listOf(EthContractAddress) }

            matchingAddresses.map { it to rate }
        }.toMap()
    }

    private fun createToSymString(targetErc20TokenNames: List<String>): String {
        return targetErc20TokenNames.joinToString(separator = ",") { it.uppercase() }
    }
}
