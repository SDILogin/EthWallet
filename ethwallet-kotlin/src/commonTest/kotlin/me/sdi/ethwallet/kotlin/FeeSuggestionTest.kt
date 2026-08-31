package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeeSuggestionTest {
    @Test
    fun feeHistoryRequestUsesBoundedLatestMedianParameters() = runTest {
        val transport = FeeRecordingTransport(listOf(standardFeeHistoryResponse()))

        EthWalletKotlin.getFeeSuggestion(network(transport))

        val request = Json.parseToJsonElement(transport.requests.single()).jsonObject
        assertEquals(JsonPrimitive("2.0"), request["jsonrpc"], "jsonrpc version")
        assertEquals(JsonPrimitive(1), request["id"], "request id")
        assertEquals(JsonPrimitive("eth_feeHistory"), request["method"], "method")
        assertEquals(
            buildJsonArray {
                add("0x5")
                add("latest")
                addJsonArray { add(50.0) }
            },
            request["params"],
            "params",
        )
    }

    @Test
    fun nextBaseFeeAndUpperMedianNonZeroRewardAreReturned() = runTest {
        val response = feeHistoryResponse(
            baseFeePerGas = listOf("0x10", "0x11", "0x12", "0x13", "0x14", "0x20"),
            gasUsedRatio = listOf(0.1, 0.2, 0.3, 0.4, 0.5),
            reward = listOf(listOf("0x10"), listOf("0x2"), listOf("0x0"), listOf("0xf"), listOf("0x3")),
        )

        val suggestion = EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))

        assertEquals(UInt256("0x20"), suggestion.baseFeePerGas, "next-block base fee")
        assertEquals(UInt256("0xf"), suggestion.priorityFeePerGas, "upper median of non-zero rewards")
    }

    @Test
    fun partialHistoryReturnedByTheNodeIsAccepted() = runTest {
        val response = feeHistoryResponse(
            baseFeePerGas = listOf("0x10", "0x11", "0x12"),
            gasUsedRatio = listOf(0.4, 0.6),
            reward = listOf(listOf("0x1"), listOf("0x2")),
        )

        val suggestion = EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))

        assertEquals(UInt256("0x12"), suggestion.baseFeePerGas)
        assertEquals(UInt256("0x2"), suggestion.priorityFeePerGas)
    }

    @Test
    fun missingRewardsUseMaxPriorityFeeFallback() = runTest {
        val transport = FeeRecordingTransport(
            responses = listOf(
                standardFeeHistoryResponse(reward = null),
                quantityResponse("0x5"),
            ),
        )

        val suggestion = EthWalletKotlin.getFeeSuggestion(network(transport))

        assertEquals(UInt256("0x5"), suggestion.priorityFeePerGas)
        assertEquals(2, transport.requests.size, "request count")
        val fallback = Json.parseToJsonElement(transport.requests[1]).jsonObject
        assertEquals(JsonPrimitive("eth_maxPriorityFeePerGas"), fallback["method"], "fallback method")
        assertEquals(buildJsonArray {}, fallback["params"], "fallback params")
    }

    @Test
    fun allZeroRewardsUseMaxPriorityFeeFallback() = runTest {
        val transport = FeeRecordingTransport(
            responses = listOf(
                standardFeeHistoryResponse(reward = List(5) { listOf("0x0") }),
                quantityResponse("0x0"),
            ),
        )

        val suggestion = EthWalletKotlin.getFeeSuggestion(network(transport))

        assertEquals(UInt256("0x0"), suggestion.priorityFeePerGas, "explicit zero fallback")
        assertEquals(2, transport.requests.size, "request count")
    }

    @Test
    fun malformedFeeHistoryShapesAreInvalidResponses() = runTest {
        val malformedResponses = listOf(
            feeHistoryResponse(baseFeePerGas = listOf("0x1"), gasUsedRatio = emptyList(), reward = emptyList()),
            feeHistoryResponse(
                baseFeePerGas = List(7) { "0x1" },
                gasUsedRatio = List(6) { 0.5 },
                reward = List(6) { listOf("0x1") },
            ),
            feeHistoryResponse(
                baseFeePerGas = listOf("0x1"),
                gasUsedRatio = listOf(0.5),
                reward = listOf(listOf("0x1")),
            ),
            feeHistoryResponse(
                baseFeePerGas = listOf("0x1", "0x2"),
                gasUsedRatio = listOf(1.1),
                reward = listOf(listOf("0x1")),
            ),
            feeHistoryResponse(
                baseFeePerGas = listOf("0x1", "0x2", "0x3"),
                gasUsedRatio = listOf(0.4, 0.6),
                reward = listOf(listOf("0x1")),
            ),
            feeHistoryResponse(
                baseFeePerGas = listOf("0x1", "0x2"),
                gasUsedRatio = listOf(0.5),
                reward = listOf(listOf("0x1", "0x2")),
            ),
        )

        for (response in malformedResponses) {
            val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse>(response) {
                EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))
            }
            assertTrue(invalid.cause is IllegalArgumentException, "validation cause")
        }
    }

    @Test
    fun nonCanonicalFeeHistoryQuantitiesAreInvalidResponses() = runTest {
        val invalidResponses = listOf(
            standardFeeHistoryResponse(oldestBlock = "0x01"),
            standardFeeHistoryResponse(baseFeePerGas = listOf("0x01", "0x2", "0x3", "0x4", "0x5", "0x6")),
            standardFeeHistoryResponse(baseFeePerGas = listOf("0x1", "0x2", "0x3", "0x4", "0x5", "0xA")),
            standardFeeHistoryResponse(
                reward = listOf(
                    listOf("0x1"),
                    listOf("0x2"),
                    listOf("0x03"),
                    listOf("0x4"),
                    listOf("0x5"),
                ),
            ),
        )

        for (response in invalidResponses) {
            val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse>(response) {
                EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))
            }
            assertTrue(invalid.cause is IllegalArgumentException, "quantity cause")
        }
    }

    @Test
    fun largestQuantitiesAreAccepted() = runTest {
        val largest = "0x" + "f".repeat(64)
        val response = standardFeeHistoryResponse(
            baseFeePerGas = listOf("0x1", "0x2", "0x3", "0x4", "0x5", largest),
            reward = List(5) { listOf(largest) },
        )

        val suggestion = EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))

        assertEquals(UInt256(largest), suggestion.baseFeePerGas)
        assertEquals(UInt256(largest), suggestion.priorityFeePerGas)
    }

    @Test
    fun malformedResultDtoIsAnInvalidResponse() = runTest {
        val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse> {
            EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(quantityResponse("0x1")))))
        }

        assertTrue(invalid.cause is SerializationException)
    }

    @Test
    fun feeHistoryRpcErrorIsPreserved() = runTest {
        val response = """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"fee history unavailable"}}"""

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(listOf(response))))
        }

        assertEquals(-32000, rejected.code)
        assertEquals("fee history unavailable", rejected.rpcMessage)
    }

    @Test
    fun fallbackRpcErrorIsPreserved() = runTest {
        val fallbackError = """{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"method not found"}}"""
        val transport = FeeRecordingTransport(
            responses = listOf(standardFeeHistoryResponse(reward = null), fallbackError),
        )

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getFeeSuggestion(network(transport))
        }

        assertEquals(-32601, rejected.code)
        assertEquals("method not found", rejected.rpcMessage)
    }

    @Test
    fun transportFailurePropagatesUnchanged() = runTest {
        val failure = IllegalStateException("offline")

        val thrown = assertSuspendFailsWith<IllegalStateException> {
            EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(failure = failure)))
        }

        assertSame(failure, thrown)
    }

    @Test
    fun transportCancellationPropagatesUnchanged() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertSuspendFailsWith<CancellationException> {
            EthWalletKotlin.getFeeSuggestion(network(FeeRecordingTransport(failure = cancellation)))
        }

        assertSame(cancellation, thrown)
    }
}

private fun network(transport: JsonRpcTransport) = EthereumNetwork(chainId = 1L, transport = transport)

private fun standardFeeHistoryResponse(
    oldestBlock: String = "0x10",
    baseFeePerGas: List<String> = listOf("0x10", "0x11", "0x12", "0x13", "0x14", "0x15"),
    reward: List<List<String>>? = List(5) { listOf("0x2") },
): String = feeHistoryResponse(
    oldestBlock = oldestBlock,
    baseFeePerGas = baseFeePerGas,
    gasUsedRatio = listOf(0.1, 0.2, 0.3, 0.4, 0.5),
    reward = reward,
)

private fun feeHistoryResponse(
    oldestBlock: String = "0x10",
    baseFeePerGas: List<String>,
    gasUsedRatio: List<Double>,
    reward: List<List<String>>?,
): String {
    val result = buildJsonObject {
        put("oldestBlock", oldestBlock)
        putJsonArray("baseFeePerGas") {
            baseFeePerGas.forEach(::add)
        }
        putJsonArray("gasUsedRatio") {
            gasUsedRatio.forEach(::add)
        }
        reward?.let { rows ->
            putJsonArray("reward") {
                rows.forEach { row ->
                    addJsonArray {
                        row.forEach(::add)
                    }
                }
            }
        }
    }
    return successResponse(result)
}

private fun quantityResponse(quantity: String) = successResponse(JsonPrimitive(quantity))

private fun successResponse(result: JsonElement): String = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", 1)
    put("result", result)
}.toString()

private class FeeRecordingTransport(
    private val responses: List<String> = emptyList(),
    private val failure: Throwable? = null,
) : JsonRpcTransport {
    val requests = mutableListOf<String>()

    override suspend fun execute(requestJson: String): String {
        requests += requestJson
        failure?.let { throw it }
        return responses.getOrNull(requests.lastIndex)
            ?: error("FeeRecordingTransport has no response for request ${requests.size}")
    }
}
