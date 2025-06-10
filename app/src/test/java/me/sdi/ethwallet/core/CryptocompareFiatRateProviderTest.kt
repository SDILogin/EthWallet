package me.sdi.ethwallet.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.NetworkManagerConfigurator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class CryptocompareFiatRateProviderTest {
    @Test
    fun `getPrice returns correct price`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"ETH": 0.0003638, "DAI": 1.0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        val rates = ethPriceProvider.getUsdRates(listOf("DAI"))

        assertEquals("2748.7", rates["ETH"]?.toPlainString()?.substring(0, 6))
        assertEquals("1.0", rates["DAI"]?.toPlainString())
    }

    @Test(expected = JsonConvertException::class)
    fun `invalid json throws exception`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"ETH": "invalid"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.getUsdRates(listOf("DAI"))
    }

    @Test(expected = SocketTimeoutException::class)
    fun `device is offline`() = runTest {
        val mockEngine = MockEngine { throw SocketTimeoutException() }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.getUsdRates(listOf("DAI"))
    }

    private fun createCryptocompareEthPriceProvider(mockEngine: MockEngine): CryptocompareFiatRateProvider {
        val networkManagerConfigurator = NetworkManagerConfigurator(mockEngine)
        return CryptocompareFiatRateProvider(networkManagerConfigurator)
    }
}
