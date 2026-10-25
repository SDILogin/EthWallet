package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.database.dao.InMemoryFiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.allKnownTokens
import me.sdi.ethwallet.ui.usdcOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigDecimal
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FiatRateProviderCoinbaseTest {
    private val dao = InMemoryFiatRateDao()
    private val currentTime = 1000000L
    private val quotes = mapOf("ETH" to "1893.58123456", "USDT" to "0.98", "USDC" to "0.87", "DAI" to "1.02")

    @Test
    fun observeReturnsEmptyMapWithoutCachedQuotes() = runTest {
        assertEquals(emptyMap(), provider(MockEngine { error("Unexpected network request") }).observe().first())
    }

    @Test
    fun forceUpdateStoresIndependentUsdQuotesForEverySupportedAddress() = runTest {
        val requested = mutableListOf<String>()
        val provider = provider(
            MockEngine { request ->
                assertEquals("api.coinbase.com", request.url.host)
                val symbol = request.url.encodedPath.removePrefix("/v2/prices/").removeSuffix("-USD/spot")
                requested.add(symbol)
                respond(
                    """{"data":{"amount":"${quotes.getValue(symbol)}","currency":"USD"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        provider.forceUpdate()

        assertEquals(quotes.keys, requested.toSet())
        assertEquals(4, requested.size)
        val expected = allKnownTokens.associate { it.ethAddress to BigDecimal(quotes.getValue(it.symbol)) } +
            (EthContractAddress to BigDecimal(quotes.getValue("ETH")))
        assertEquals(expected, provider.observe().first())
        val rows = dao.readAll(expected.keys.map { it.toString() })
        assertEquals(expected.size, rows.size)
        rows.forEach { assertEquals(currentTime, it.lastUpdatedTimestamp) }
    }

    @Test
    fun observeUsesOnlyCachedQuotesWithoutInventingPeggedRates() = runTest {
        val cached = listOf(
            FiatRateEntity(EthContractAddress.toString(), "2500.12345678", currentTime),
            FiatRateEntity(usdcOnMainnet.ethAddress.toString(), "0.87", currentTime),
        )
        dao.insertAll(cached)
        val result = provider(MockEngine { error("Unexpected network request") }).observe().first()

        assertEquals(
            mapOf(EthContractAddress to BigDecimal("2500.12345678"), usdcOnMainnet.ethAddress to BigDecimal("0.87")),
            result,
        )
    }

    @Test
    fun invalidOrFailedQuotesPreserveCachedRates() = runTest {
        val cached = listOf(FiatRateEntity(usdtOnMainnet.ethAddress.toString(), "0.98", currentTime))
        dao.insertAll(cached)
        for (invalidQuote in listOf(
            """{"data":{"amount":"1.0","currency":"EUR"}}""",
            """{"data":{"amount":"-1","currency":"USD"}}""",
            """{"data":{"amount":"0","currency":"USD"}}""",
            """{"data":{"amount":"invalid","currency":"USD"}}""",
        )) {
            val provider = provider(
                MockEngine { request ->
                    val content = if (request.url.encodedPath.contains("USDC-USD")) {
                        invalidQuote
                    } else {
                        """{"data":{"amount":"1.01","currency":"USD"}}"""
                    }
                    respond(content, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
            assertFailsWith<IllegalArgumentException> { provider.forceUpdate() }
            assertEquals(cached, dao.readAll(listOf(usdtOnMainnet.ethAddress.toString())))
            assertEquals(mapOf(usdtOnMainnet.ethAddress to BigDecimal("0.98")), provider.observe().first())
        }
    }

    @Test
    fun malformedResponseFails() = runTest {
        val provider = provider(
            MockEngine {
                respond("""{"data":{}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        assertFailsWith<JsonConvertException> { provider.forceUpdate() }
    }

    @Test
    fun networkFailurePropagates() = runTest {
        assertFailsWith<SocketTimeoutException> {
            provider(MockEngine { throw SocketTimeoutException() }).forceUpdate()
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        assertFailsWith<CancellationException> {
            provider(MockEngine { throw CancellationException() }).forceUpdate()
        }
        assertEquals(emptyList(), dao.readAll(listOf(EthContractAddress.toString())))
    }

    private fun provider(engine: MockEngine) = FiatRateProviderCoinbase(
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        dao,
        now = { currentTime },
    )
}
