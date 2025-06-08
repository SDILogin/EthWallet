package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.sdi.ethwallet.data.NetworkManagerConfigurator
import java.math.BigDecimal

private const val PRICE_URL = "https://min-api.cryptocompare.com/data/price?fsym=ETH&tsyms=USD"

@Serializable
private data class PriceResponse(
    @SerialName("USD")
    val usd: Double,
)

class CryptocompareEthPriceProvider(
    private val networkManagerConfigurator: NetworkManagerConfigurator = NetworkManagerConfigurator(),
) : EthPriceProvider {
    override suspend fun getPrice(): BigDecimal {
        val client: HttpClient = networkManagerConfigurator.createHttpClient()

        return try {
            val response: PriceResponse = client.get(PRICE_URL).body()
            BigDecimal(response.usd.toString())
        } finally {
            client.close()
        }
    }
}
