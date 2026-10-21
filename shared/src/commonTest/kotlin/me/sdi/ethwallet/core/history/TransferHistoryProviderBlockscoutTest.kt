package me.sdi.ethwallet.core.history

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.di.createHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransferHistoryProviderBlockscoutTest {
    private val wallet = EthAddress("0x${"1".repeat(40)}")
    private val other = EthAddress("0x${"2".repeat(40)}")
    private val hash = "0x${"a".repeat(64)}"
    private val time = "2026-09-04T08:00:00Z"
    private val next = """{"block_number":123,"index":4,"items_count":50,"key":"a b","unused":null}"""

    @Test
    fun maps_both_sources_and_forwards_each_complete_cursor() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val provider = provider { request ->
            requests += request
            when {
                request.url.parameters["index"] != null -> page()
                request.url.encodedPath.endsWith("/transactions") -> page(
                    transaction(),
                    transaction(hash = "0x${"b".repeat(64)}", block = "null", status = "null", feeType = "maximum"),
                    next = next,
                )
                else -> page(token(from = other, to = wallet), next = """{"index":8,"items_count":50}""")
            }
        }
        val first = provider.getTransfers(wallet)
        val native = assertIs<Transfer.Eth>(first.eth!!.items[0])
        val pending = first.eth.items[1]
        assertEquals(Instant.parse(time).toEpochMilliseconds(), native.timestamp)
        assertEquals(TransactionStatus.Confirmed, native.status)
        assertEquals(TransactionStatus.Pending, pending.status)
        assertTrue(pending.fee!!.estimated)
        assertEquals(3, first.items.size)
        assertEquals(Instant.parse(time).toEpochMilliseconds(), first.eth.oldestTimestamp)
        val second = provider.getTransfers(wallet, first.eth.nextRequest, first.erc20!!.nextRequest)
        assertEquals(emptyList(), second.items)
        assertEquals(TransfersPageRequest.Skip, second.eth!!.nextRequest)
        assertEquals(TransfersPageRequest.Skip, second.erc20!!.nextRequest)
        val ethRequest = requests.last { it.url.encodedPath.endsWith("/transactions") }
        assertEquals("123", ethRequest.url.parameters["block_number"])
        assertEquals("4", ethRequest.url.parameters["index"])
        assertEquals("50", ethRequest.url.parameters["items_count"])
        assertEquals("a b", ethRequest.url.parameters["key"])
        assertNull(ethRequest.url.parameters["unused"])
        assertEquals("8", requests.last { it.url.encodedPath.endsWith("/token-transfers") }.url.parameters["index"])
    }

    @Test
    fun skip_does_not_request_the_source() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val provider = provider {
            requests += it
            page()
        }
        val result = provider.getTransfers(wallet, TransfersPageRequest.Skip, TransfersPageRequest.First)
        assertNull(result.eth)
        assertEquals(1, requests.size)
        assertTrue(requests.single().url.encodedPath.endsWith("/token-transfers"))
    }

    @Test
    fun multiple_logs_reuse_full_fee_from_zero_value_parent_in_native_response() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val provider = provider { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/token-transfers")) {
                page(token(logIndex = 7), token(logIndex = 8))
            } else {
                page(transaction(value = "0", input = "0xa9059cbb"))
            }
        }
        val result = provider.getTransfers(wallet)
        assertEquals(emptyList(), result.eth!!.items)
        assertEquals(listOf(7, 8), result.erc20!!.items.map { assertIs<Transfer.Erc20>(it).logIndex })
        result.erc20.items.forEach { assertEquals("21000000000000", it.fee!!.value.value.toString()) }
        assertEquals(2, requests.size)
    }

    @Test
    fun outgoing_logs_lookup_missing_parent_once_and_keep_estimated_fee() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val provider = provider { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/token-transfers")) {
                page(token(logIndex = 7), token(logIndex = 8))
            } else {
                assertTrue(request.url.encodedPath.endsWith("/transactions/$hash"))
                transaction(value = "0", block = "null", status = "null", feeType = "maximum")
            }
        }
        val result = provider.getTransfers(wallet, TransfersPageRequest.Skip, TransfersPageRequest.First)
        assertEquals(2, requests.size)
        result.items.forEach {
            assertEquals(TransactionStatus.Pending, it.status)
            assertTrue(it.fee!!.estimated)
        }
    }

    @Test
    fun incoming_tokens_need_no_fee_lookup_and_use_six_decimals_when_missing() = runTest {
        val provider = provider { request ->
            assertTrue(request.url.encodedPath.endsWith("/token-transfers"))
            page(token(from = other, to = wallet, decimals = "null"))
        }
        val item = assertIs<Transfer.Erc20>(
            provider.getTransfers(wallet, TransfersPageRequest.Skip, TransfersPageRequest.First).items.single(),
        )
        assertEquals(6, item.tokenDecimals)
        assertEquals("1500000", item.value)
        assertNull(item.fee)
    }

    @Test
    fun native_transfers_with_attached_data_are_preserved_in_both_directions() = runTest {
        val memo = "0x68656c6c6f"
        val provider = provider {
            page(
                transaction(input = memo),
                transaction(hash = "0x${"b".repeat(64)}", from = other, to = wallet, input = memo),
            )
        }

        val transfers = provider.getTransfers(wallet, TransfersPageRequest.First, TransfersPageRequest.Skip).items

        assertEquals(2, transfers.size)
        val outgoing = assertIs<Transfer.Eth>(transfers[0])
        val incoming = assertIs<Transfer.Eth>(transfers[1])
        assertEquals(wallet, outgoing.from)
        assertEquals(other, outgoing.to)
        assertEquals(other, incoming.from)
        assertEquals(wallet, incoming.to)
        assertEquals("1000000000000000", outgoing.valueWei.value.toString())
        assertEquals(outgoing.valueWei, incoming.valueWei)
    }

    @Test
    fun filtering_retains_raw_response_range_and_next_cursor() = runTest {
        val oldTime = "2026-09-01T08:00:00Z"
        val provider = provider { request ->
            if (request.url.encodedPath.endsWith("/transactions")) {
                page(
                    transaction(value = "0", timestamp = oldTime),
                    transaction(to = null),
                    transaction(input = "0xa9059cbb", types = """["coin_transfer","contract_call"]"""),
                    transaction(types = """["contract_call"]"""),
                    next = next,
                )
            } else {
                page(token(type = "ERC-721", value = "null", timestamp = oldTime), token(value = "0"), next = next)
            }
        }
        val result = provider.getTransfers(wallet)
        assertEquals(emptyList(), result.items)
        assertEquals(Instant.parse(oldTime).toEpochMilliseconds(), result.eth!!.oldestTimestamp)
        assertEquals(Instant.parse(oldTime).toEpochMilliseconds(), result.erc20!!.oldestTimestamp)
        assertIs<TransfersPageRequest.Next>(result.eth.nextRequest)
        assertIs<TransfersPageRequest.Next>(result.erc20.nextRequest)
    }

    @Test
    fun failed_parent_updates_outgoing_transfer_status_without_requiring_fee() = runTest {
        val provider = provider { request ->
            if (request.url.encodedPath.endsWith(
                    "/token-transfers",
                )
            ) {
                page(token())
            } else {
                page(transaction(status = "\"error\"", feeType = null))
            }
        }
        val result = provider.getTransfers(wallet)
        assertEquals(2, result.items.size)
        result.items.forEach {
            assertEquals(TransactionStatus.Failed, it.status)
            assertNull(it.fee)
        }
    }

    @Test
    fun missing_required_timestamp_fails_instead_of_creating_a_fallback() = runTest {
        val response = page(transaction()).replace("\"timestamp\":\"$time\"", "\"timestamp\":null")
        val provider = provider { response }
        assertFailsWith<JsonConvertException> {
            provider.getTransfers(wallet, TransfersPageRequest.First, TransfersPageRequest.Skip)
        }
    }

    @Test
    fun invalid_decimals_and_missing_outgoing_fee_fail_the_load() = runTest {
        val invalidDecimals = provider { page(token(from = other, to = wallet, decimals = "\"256\"")) }
        assertFailsWith<IllegalArgumentException> {
            invalidDecimals.getTransfers(wallet, TransfersPageRequest.Skip, TransfersPageRequest.First)
        }
        val missingFee = provider { page(transaction(feeType = null)) }
        assertFailsWith<IllegalArgumentException> {
            missingFee.getTransfers(wallet, TransfersPageRequest.First, TransfersPageRequest.Skip)
        }
    }

    @Test
    fun fee_lookup_failure_fails_combined_load() = runTest {
        val client = createHttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/token-transfers")) respondJson(page(token())) else error("Fee lookup failed")
            },
        )
        val provider = TransfersHistoryProviderBlockscout(client, "https://history.example.test")
        assertFailsWith<IllegalStateException> {
            provider.getTransfers(wallet, TransfersPageRequest.Skip, TransfersPageRequest.First)
        }
    }

    private fun provider(response: (HttpRequestData) -> String) = TransfersHistoryProviderBlockscout(
        createHttpClient(MockEngine { respondJson(response(it)) }),
        "https://history.example.test/",
    )

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content,
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun page(vararg items: String, next: String = "null") = """{"items":[${items.joinToString()}],"next_page_params":$next}"""

    private fun transaction(
        hash: String = this.hash,
        timestamp: String = time,
        from: EthAddress = wallet,
        to: EthAddress? = other,
        value: String = "1000000000000000",
        block: String = "123",
        status: String = "\"ok\"",
        feeType: String? = "actual",
        input: String = "0x",
        types: String = """["coin_transfer"]""",
    ) = """{
        "hash":"$hash","timestamp":"$timestamp","block_number":$block,
        "from":{"hash":"$from"},"to":${to?.let { """{"hash":"$it"}""" } ?: "null"},
        "value":"$value","status":$status,"raw_input":"$input","transaction_types":$types,
        "fee":${feeType?.let { """{"type":"$it","value":"21000000000000"}""" } ?: "null"}
    }"""

    private fun token(
        logIndex: Int = 7,
        from: EthAddress = wallet,
        to: EthAddress = other,
        type: String = "ERC-20",
        decimals: String = "\"6\"",
        value: String = "\"1500000\"",
        timestamp: String = time,
    ) = """{
        "transaction_hash":"$hash","log_index":$logIndex,"timestamp":"$timestamp",
        "from":{"hash":"$from"},"to":{"hash":"$to"},
        "token":{"address_hash":"0x${"3".repeat(40)}","symbol":"USDC","decimals":$decimals,"type":"$type"},
        "total":{"value":$value,"decimals":$decimals}
    }"""
}
