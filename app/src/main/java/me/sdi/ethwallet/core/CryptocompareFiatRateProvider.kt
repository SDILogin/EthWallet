package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import me.sdi.ethwallet.data.NetworkManagerConfigurator
import java.math.BigDecimal

private const val PRICE_URL = "https://min-api.cryptocompare.com/data/price?fsym=USD&tsyms="

class CryptocompareFiatRateProvider(
    private val networkManagerConfigurator: NetworkManagerConfigurator = NetworkManagerConfigurator(),
) : FiatRateProvider {
    override suspend fun getUsdRates(targetErc20TokenNames: List<String>): Map<String, BigDecimal> {
        val client: HttpClient = networkManagerConfigurator.createHttpClient()
        val toSymString = createToSymString(targetErc20TokenNames + "ETH")

        return try {
            val response: Map<String, Double> = client.get(PRICE_URL + toSymString).body()
            response.mapValues { BigDecimal.valueOf(1.0 / it.value) }
        } finally {
            client.close()
        }
    }

    private fun createToSymString(targetErc20TokenNames: List<String>): String {
        return targetErc20TokenNames.joinToString(separator = ",") { it.uppercase() }
    }
}
