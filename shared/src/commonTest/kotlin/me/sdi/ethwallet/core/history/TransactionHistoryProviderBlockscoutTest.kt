package me.sdi.ethwallet.core.history

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.di.createHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionHistoryProviderBlockscoutTest {
    private val walletAddress = EthAddress("0x1234567890123456789012345678901234567890")

    @Test
    fun getTransactions_maps_confirmed_and_pending_transactions_and_forwards_cursor() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = createHttpClient(
            MockEngine { request ->
                requests += request
                respondJson(if (requests.size == 1) TRANSACTIONS_PAGE_ONE else EMPTY_PAGE)
            },
        )
        val provider = TransactionHistoryProviderBlockscout(client, "https://history.example.test/")

        val firstPage = provider.getTransactions(walletAddress)
        val secondPage = provider.getTransactions(walletAddress, firstPage.nextCursor)

        assertEquals(2, firstPage.items.size)
        assertEquals(TransactionStatus.Confirmed, firstPage.items[0].status)
        assertEquals("2026-09-04T08:00:00.000000Z", firstPage.items[0].timestamp)
        assertEquals(TransactionStatus.Pending, firstPage.items[1].status)
        assertNull(firstPage.items[1].timestamp)
        assertNull(firstPage.items[1].to)
        assertEquals(emptyList(), secondPage.items)
        assertNull(secondPage.nextCursor)
        assertEquals(
            "https://history.example.test/api/v2/addresses/$walletAddress/transactions",
            requests[0].url.toString(),
        )
        assertEquals("123", requests[1].url.parameters["block_number"])
        assertEquals("4", requests[1].url.parameters["index"])
        assertEquals("50", requests[1].url.parameters["items_count"])
    }

    @Test
    fun getTokenTransfers_maps_only_erc20_transfers() = runTest {
        val client = createHttpClient(MockEngine { respondJson(TOKEN_TRANSFERS_PAGE) })
        val provider = TransactionHistoryProviderBlockscout(client, "https://history.example.test")

        val page = provider.getTokenTransfers(walletAddress)

        assertEquals(1, page.items.size)
        assertEquals("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", page.items.single().transactionHash)
        assertEquals("USDC", page.items.single().tokenSymbol)
        assertEquals(6, page.items.single().tokenDecimals)
        assertEquals("1500000", page.items.single().value)
        assertNull(page.nextCursor)
    }

    @Test
    fun rejects_cursor_created_by_another_provider() = runTest {
        val client = createHttpClient(MockEngine { error("Network request must not be made") })
        val provider = TransactionHistoryProviderBlockscout(client, "https://history.example.test")
        val foreignCursor = object : TransactionHistoryCursor {}

        assertFailsWith<IllegalArgumentException> {
            provider.getTransactions(walletAddress, foreignCursor)
        }
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val EMPTY_PAGE = """{"items":[],"next_page_params":null}"""

        val TRANSACTIONS_PAGE_ONE =
            """
            {
              "items": [
                {
                  "hash": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "timestamp": "2026-09-04T08:00:00.000000Z",
                  "block_number": 123,
                  "from": { "hash": "0x1234567890123456789012345678901234567890" },
                  "to": { "hash": "0x9876543210987654321098765432109876543210" },
                  "value": "1000000000000000",
                  "status": "ok"
                },
                {
                  "hash": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "timestamp": null,
                  "block_number": null,
                  "from": { "hash": "0x1234567890123456789012345678901234567890" },
                  "to": null,
                  "value": "0",
                  "status": null
                }
              ],
              "next_page_params": {
                "block_number": 123,
                "index": 4,
                "items_count": 50,
                "unused": null
              }
            }
            """.trimIndent()

        val TOKEN_TRANSFERS_PAGE =
            """
            {
              "items": [
                {
                  "transaction_hash": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "log_index": 7,
                  "timestamp": "2026-09-04T08:00:00.000000Z",
                  "from": { "hash": "0x1234567890123456789012345678901234567890" },
                  "to": { "hash": "0x9876543210987654321098765432109876543210" },
                  "token": {
                    "address_hash": "0x1111111111111111111111111111111111111111",
                    "symbol": "USDC",
                    "decimals": "6",
                    "type": "ERC-20"
                  },
                  "total": { "value": "1500000", "decimals": "6" }
                },
                {
                  "transaction_hash": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "log_index": 8,
                  "timestamp": "2026-09-04T08:00:01.000000Z",
                  "from": { "hash": "0x1234567890123456789012345678901234567890" },
                  "to": { "hash": "0x9876543210987654321098765432109876543210" },
                  "token": {
                    "address_hash": "0x2222222222222222222222222222222222222222",
                    "symbol": "NFT",
                    "decimals": null,
                    "type": "ERC-721"
                  },
                  "total": { "value": null, "decimals": null }
                }
              ],
              "next_page_params": null
            }
            """.trimIndent()
    }
}
