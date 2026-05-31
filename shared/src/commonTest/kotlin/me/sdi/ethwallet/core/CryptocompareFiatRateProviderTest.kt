package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.sdi.ethwallet.database.dao.InMemoryFiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.KnownTokens
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.SocketTimeoutException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CryptocompareFiatRateProviderTest {
    private val inMemoryFiatRateDao = InMemoryFiatRateDao()
    private val currentTime = 1000000L

    @BeforeTest
    fun setUp() {
        inMemoryFiatRateDao.clear()
    }

    @Test
    fun observe_returns_empty_map_initially() = runTest {
        val ethPriceProvider = createCryptocompareEthPriceProvider(MockEngine { throw RuntimeException("Should not call network") })

        val result = ethPriceProvider.observe().first()

        assertEquals(emptyMap<EthAddress, BigDecimal>(), result, "Should return empty map when no cached rates exist")
    }

    @Test
    fun forceUpdate_fetches_and_stores_rates_from_network() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"ETH": 0.0003638, "USDT": 1.0, "USDC": 1.0, "DAI": 1.0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.forceUpdate()

        val result = ethPriceProvider.observe().first()

        assertEquals(
            BigDecimal("2748.7630566245"),
            result[EthContractAddress]?.setScale(10, RoundingMode.HALF_UP),
            "Should return ETH rate",
        )
        assertEquals(BigDecimal("1.0"), result[KnownTokens.USDT.ethAddress], "Should return USDT rate")
        assertEquals(BigDecimal("1.0"), result[KnownTokens.USDC.ethAddress], "Should return USDC rate")
        assertEquals(BigDecimal("1.0"), result[KnownTokens.DAI.ethAddress], "Should return DAI rate")
    }

    @Test
    fun observe_returns_cached_rates_from_database() = runTest {
        // Pre-populate database with rates for all known tokens
        val ethRate = BigDecimal("2500.0")
        val usdtRate = BigDecimal("1.0")
        val usdcRate = BigDecimal("1.0")
        val daiRate = BigDecimal("1.0")
        inMemoryFiatRateDao.insertAll(
            listOf(
                FiatRateEntity(
                    tokenAddress = EthContractAddress.toString(),
                    rate = ethRate.toString(),
                    lastUpdatedTimestamp = currentTime,
                ),
                FiatRateEntity(
                    tokenAddress = KnownTokens.USDT.ethAddress.toString(),
                    rate = usdtRate.toString(),
                    lastUpdatedTimestamp = currentTime,
                ),
                FiatRateEntity(
                    tokenAddress = KnownTokens.USDC.ethAddress.toString(),
                    rate = usdcRate.toString(),
                    lastUpdatedTimestamp = currentTime,
                ),
                FiatRateEntity(
                    tokenAddress = KnownTokens.DAI.ethAddress.toString(),
                    rate = daiRate.toString(),
                    lastUpdatedTimestamp = currentTime,
                ),
            ),
        )

        val ethPriceProvider = createCryptocompareEthPriceProvider(MockEngine { throw RuntimeException("Should not call network") })

        val result = ethPriceProvider.observe().first()

        assertEquals(ethRate, result[EthContractAddress], "Should return cached ETH rate")
        assertEquals(usdtRate, result[KnownTokens.USDT.ethAddress], "Should return cached USDT rate")
        assertEquals(usdcRate, result[KnownTokens.USDC.ethAddress], "Should return cached USDC rate")
        assertEquals(daiRate, result[KnownTokens.DAI.ethAddress], "Should return cached DAI rate")
    }

    @Test
    fun forceUpdate_with_invalid_json_throws_exception() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"ETH": "invalid"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        assertFailsWith<JsonConvertException> {
            ethPriceProvider.forceUpdate()
        }
    }

    @Test
    fun forceUpdate_when_device_is_offline_throws_exception() = runTest {
        val mockEngine = MockEngine { throw SocketTimeoutException() }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        assertFailsWith<SocketTimeoutException> {
            ethPriceProvider.forceUpdate()
        }
    }

    private fun createCryptocompareEthPriceProvider(mockEngine: MockEngine): CryptocompareFiatRateProvider {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }
        return CryptocompareFiatRateProvider(httpClient, inMemoryFiatRateDao, now = { currentTime })
    }
}
