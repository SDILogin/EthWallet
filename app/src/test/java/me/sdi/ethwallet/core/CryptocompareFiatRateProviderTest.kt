package me.sdi.ethwallet.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.data.NetworkManagerConfigurator
import me.sdi.ethwallet.database.dao.InMemoryFiatRateDao
import me.sdi.ethwallet.database.entity.FiatRateEntity
import me.sdi.ethwallet.ui.KnownTokens
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.SocketTimeoutException

class CryptocompareFiatRateProviderTest {
    private val inMemoryFiatRateDao = InMemoryFiatRateDao()
    private val currentTime = 1000000L

    @Before
    fun setUp() {
        inMemoryFiatRateDao.clear()
    }

    @Test
    fun `observe returns empty map initially`() = runTest {
        val ethPriceProvider = createCryptocompareEthPriceProvider(MockEngine { throw RuntimeException("Should not call network") })

        val result = ethPriceProvider.observe().first()

        assertEquals("Should return empty map when no cached rates exist", emptyMap<EthAddress, BigDecimal>(), result)
    }

    @Test
    fun `forceUpdate fetches and stores rates from network`() = runTest {
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
            "Should return ETH rate",
            BigDecimal("2748.7630566245"),
            result[EthContractAddress]?.setScale(10, RoundingMode.HALF_UP),
        )
        assertEquals("Should return USDT rate", BigDecimal("1.0"), result[KnownTokens.USDT.ethAddress])
        assertEquals("Should return USDC rate", BigDecimal("1.0"), result[KnownTokens.USDC.ethAddress])
        assertEquals("Should return DAI rate", BigDecimal("1.0"), result[KnownTokens.DAI.ethAddress])
    }

    @Test
    fun `observe returns cached rates from database`() = runTest {
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

        assertEquals("Should return cached ETH rate", ethRate, result[EthContractAddress])
        assertEquals("Should return cached USDT rate", usdtRate, result[KnownTokens.USDT.ethAddress])
        assertEquals("Should return cached USDC rate", usdcRate, result[KnownTokens.USDC.ethAddress])
        assertEquals("Should return cached DAI rate", daiRate, result[KnownTokens.DAI.ethAddress])
    }

    @Test(expected = JsonConvertException::class)
    fun `forceUpdate with invalid json throws exception`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"ETH": "invalid"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.forceUpdate()
    }

    @Test(expected = SocketTimeoutException::class)
    fun `forceUpdate when device is offline throws exception`() = runTest {
        val mockEngine = MockEngine { throw SocketTimeoutException() }
        val ethPriceProvider = createCryptocompareEthPriceProvider(mockEngine)

        ethPriceProvider.forceUpdate()
    }

    private fun createCryptocompareEthPriceProvider(mockEngine: MockEngine): CryptocompareFiatRateProvider {
        val networkManagerConfigurator = NetworkManagerConfigurator(mockEngine)
        return CryptocompareFiatRateProvider(networkManagerConfigurator, inMemoryFiatRateDao, now = { currentTime })
    }
}
