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

class CryptocompareEthPriceProviderTest {
    @Test
    fun `getPrice returns correct price`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"USD": 1500.0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        val price = ethPriceProvider.getPrice()

        assertEquals("1500.0", price.toPlainString()) // Changed
    }

    @Test(expected = JsonConvertException::class)
    fun `invalid json throws exception`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"USD": "invalid"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.getPrice()
    }

    @Test(expected = SocketTimeoutException::class)
    fun `device is offline`() = runTest {
        val mockEngine = MockEngine { throw SocketTimeoutException() }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.getPrice()
    }

    private fun createCryptocompareEthPriceProvider(mockEngine: MockEngine): CryptocompareEthPriceProvider {
        val networkManagerConfigurator = NetworkManagerConfigurator(mockEngine)
        return CryptocompareEthPriceProvider(networkManagerConfigurator)
    }
}
