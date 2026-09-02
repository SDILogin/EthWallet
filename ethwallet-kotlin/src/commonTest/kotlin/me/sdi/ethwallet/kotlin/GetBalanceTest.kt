package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val OWNER = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
private const val CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7"
private const val BALANCE_OF_OWNER_CALLDATA =
    "0x70a08231" + "000000000000000000000000" + "9858effd232b4033e47d90003d41ec34ecaeda94"
private const val ONE_ETHER_WORD =
    "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000"
private const val ZERO_WORD =
    "0x0000000000000000000000000000000000000000000000000000000000000000"

private class RecordingTransport(
    private val response: String? = null,
    private val failure: Throwable? = null,
) : JsonRpcTransport {
    var lastRequest: String? = null
        private set

    override suspend fun execute(requestJson: String): String {
        lastRequest = requestJson
        failure?.let { throw it }
        return checkNotNull(response) { "RecordingTransport has no response" }
    }
}

class GetBalanceTest {
    private val owner = OWNER
    private val erc20 = Asset.Erc20(CONTRACT_ADDRESS)

    @Test
    fun ethBalanceRequestIsEthGetBalanceAtLatestBlock() = runTest {
        val transport = RecordingTransport(success("0x1"))

        EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)

        val request = Json.parseToJsonElement(checkNotNull(transport.lastRequest)).jsonObject
        assertEquals(JsonPrimitive("2.0"), request["jsonrpc"], "jsonrpc version")
        assertEquals(JsonPrimitive(1), request["id"], "request id")
        assertEquals(JsonPrimitive("eth_getBalance"), request["method"], "method")
        assertEquals(
            buildJsonArray {
                add(OWNER)
                add("latest")
            },
            request["params"],
            "params",
        )
    }

    @Test
    fun ethBalanceResultIsReturnedAsQuantity() = runTest {
        val balance = EthWalletKotlin.getBalance(network(RecordingTransport(success("0x1bc16d674ec80000"))), owner, Asset.Eth)

        assertEquals(UInt256("0x1bc16d674ec80000"), balance, "two ether in wei")
    }

    @Test
    fun zeroEthBalanceIsReturnedAsZeroQuantity() = runTest {
        val balance = EthWalletKotlin.getBalance(network(RecordingTransport(success("0x0"))), owner, Asset.Eth)

        assertEquals(UInt256("0x0"), balance, "zero balance")
    }

    @Test
    fun largestEthBalanceIsReturnedAsQuantity() = runTest {
        val largest = "0x" + "f".repeat(64)

        val balance = EthWalletKotlin.getBalance(network(RecordingTransport(success(largest))), owner, Asset.Eth)

        assertEquals(UInt256(largest), balance, "largest balance")
    }

    @Test
    fun unknownResponseKeysAreIgnored() = runTest {
        val transport = RecordingTransport("""{"jsonrpc":"2.0","id":1,"result":"0x1","extra":true}""")

        val balance = EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)

        assertEquals(UInt256("0x1"), balance, "balance with extra keys")
    }

    @Test
    fun erc20BalanceRequestIsEthCallOfBalanceOf() = runTest {
        val transport = RecordingTransport(success(ONE_ETHER_WORD))

        EthWalletKotlin.getBalance(network(transport), owner, erc20)

        val request = Json.parseToJsonElement(checkNotNull(transport.lastRequest)).jsonObject
        assertEquals(JsonPrimitive("eth_call"), request["method"], "method")
        val params = checkNotNull(request["params"]).jsonArray
        assertEquals(2, params.size, "params size")
        val call = params[0].jsonObject
        assertEquals(JsonPrimitive(CONTRACT_ADDRESS), call["to"], "call target")
        assertEquals(JsonPrimitive(BALANCE_OF_OWNER_CALLDATA), call["data"], "balanceOf calldata")
        assertEquals(JsonPrimitive("latest"), params[1], "block tag")
    }

    @Test
    fun erc20BalanceWordIsReturnedAsQuantity() = runTest {
        val balance = EthWalletKotlin.getBalance(network(RecordingTransport(success(ONE_ETHER_WORD))), owner, erc20)

        assertEquals(UInt256("0xde0b6b3a7640000"), balance, "one token unit with 18 decimals")
    }

    @Test
    fun zeroErc20BalanceWordIsReturnedAsZeroQuantity() = runTest {
        val balance = EthWalletKotlin.getBalance(network(RecordingTransport(success(ZERO_WORD))), owner, erc20)

        assertEquals(UInt256("0x0"), balance, "zero token balance")
    }

    @Test
    fun errorObjectIsRethrownAsStandardException() = runTest {
        val transport = RecordingTransport(
            """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"insufficient funds","data":"0xabc"}}""",
        )

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)
        }

        assertEquals(-32000, rejected.code)
        assertEquals("insufficient funds", rejected.rpcMessage)
        assertEquals(JsonPrimitive("0xabc"), rejected.data)
        assertEquals("JSON-RPC error -32000: insufficient funds: 0xabc", rejected.message)
    }

    @Test
    fun errorWithNullIdIsStillReported() = runTest {
        val transport = RecordingTransport("""{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse error"}}""")

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)
        }

        assertEquals(-32700, rejected.code)
        assertEquals("parse error", rejected.rpcMessage)
        assertEquals(null, rejected.data)
        assertEquals("JSON-RPC error -32700: parse error", rejected.message)
    }

    @Test
    fun nullErrorDataIsReportedWithoutExtraText() = runTest {
        val transport = RecordingTransport("""{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"reverted","data":null}}""")

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)
        }

        assertEquals(null, rejected.data)
        assertEquals("JSON-RPC error 3: reverted", rejected.message)
    }

    @Test
    fun structuredErrorDataIsIncludedInTheMessage() = runTest {
        val transport = RecordingTransport(
            """{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"reverted","data":{"reason":"paused"}}}""",
        )

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.getBalance(network(transport), owner, Asset.Eth)
        }

        assertEquals(Json.parseToJsonElement("{\"reason\":\"paused\"}"), rejected.data)
        assertEquals("JSON-RPC error 3: reverted: {\"reason\":\"paused\"}", rejected.message)
    }

    @Test
    fun mismatchedErrorIdIsAnInvalidResponse() = runTest {
        val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse> {
            EthWalletKotlin.getBalance(
                network(
                    RecordingTransport(
                        """{"jsonrpc":"2.0","id":2,"error":{"code":3,"message":"reverted"}}""",
                    ),
                ),
                owner,
                Asset.Eth,
            )
        }

        assertTrue(invalid.cause is IllegalArgumentException)
    }

    @Test
    fun responseWithResultAndErrorIsAnInvalidResponse() = runTest {
        val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse> {
            EthWalletKotlin.getBalance(
                network(
                    RecordingTransport(
                        """{"jsonrpc":"2.0","id":1,"result":"0x1","error":{"code":3,"message":"reverted"}}""",
                    ),
                ),
                owner,
                Asset.Eth,
            )
        }

        assertTrue(invalid.cause is IllegalArgumentException)
    }

    @Test
    fun nonJsonBodyKeepsParserCause() = runTest {
        val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse> {
            EthWalletKotlin.getBalance(network(RecordingTransport("<html>502</html>")), owner, Asset.Eth)
        }

        assertTrue(invalid.cause is SerializationException)
    }

    @Test
    fun malformedEthResponsesAreInvalid() = runTest {
        val bodies = listOf(
            "[]",
            """{"jsonrpc":"1.0","id":1,"result":"0x1"}""",
            """{"jsonrpc":"2.0","id":"1","result":"0x1"}""",
            """{"jsonrpc":"2.0","id":2,"result":"0x1"}""",
            """{"jsonrpc":"2.0","id":1,"result":null}""",
            """{"jsonrpc":"2.0","id":1}""",
            """{"jsonrpc":"2.0","id":1,"error":{"message":"no code"}}""",
            """{"jsonrpc":"2.0","id":1,"result":1}""",
            """{"jsonrpc":"2.0","id":1,"result":"0x01"}""",
        )

        for (body in bodies) {
            val failure = assertSuspendFailsWith<JsonRpcException.InvalidResponse>("Body $body") {
                EthWalletKotlin.getBalance(network(RecordingTransport(body)), owner, Asset.Eth)
            }
            assertTrue(failure.cause != null)
        }
    }

    @Test
    fun erc20WordsOfWrongLengthAreInvalid() = runTest {
        val bodies = listOf(
            success(ZERO_WORD.dropLast(1)),
            success("0x"),
        )

        for (body in bodies) {
            assertSuspendFailsWith<JsonRpcException.InvalidResponse>("Body $body") {
                EthWalletKotlin.getBalance(network(RecordingTransport(body)), owner, erc20)
            }
        }
    }

    @Test
    fun nonCanonicalEthQuantitiesAreInvalidResponses() = runTest {
        val invalidQuantities = listOf(
            "0x",
            "0x00",
            "0x01",
            "0x" + "f".repeat(65),
            "0xA",
            "1",
        )

        for (quantity in invalidQuantities) {
            val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse>("Quantity $quantity") {
                EthWalletKotlin.getBalance(network(RecordingTransport(success(quantity))), owner, Asset.Eth)
            }
            assertTrue(invalid.cause is IllegalArgumentException, "Quantity $quantity cause")
        }
    }

    @Test
    fun nonStringEthQuantityIsAnInvalidResponse() = runTest {
        val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse> {
            EthWalletKotlin.getBalance(
                network(RecordingTransport("""{"jsonrpc":"2.0","id":1,"result":1}""")),
                owner,
                Asset.Eth,
            )
        }

        assertTrue(invalid.cause is IllegalArgumentException)
    }

    @Test
    fun uint256AcceptsCanonicalQuantities() {
        val largest = "0x" + "f".repeat(64)

        assertEquals("0x0", UInt256("0x0").hex, "zero")
        assertEquals(largest, UInt256(largest).hex, "largest 256-bit value")
    }

    @Test
    fun uint256RejectsNonCanonicalHex() {
        val invalid = listOf("", "0x", "1", "0x00", "0xAB", "0x1" + "0".repeat(64))

        for (hex in invalid) {
            assertFailsWith<IllegalArgumentException>("Hex $hex") { UInt256(hex) }
        }
    }

    @Test
    fun getBalanceRejectsMalformedOwnerAddress() = runTest {
        val invalid = listOf(OWNER.dropLast(1), OWNER + "0", OWNER.substring(2), "0x" + "g".repeat(40))

        for (address in invalid) {
            assertSuspendFailsWith<IllegalArgumentException>("Address $address") {
                EthWalletKotlin.getBalance(network(RecordingTransport(success("0x0"))), address, Asset.Eth)
            }
        }
    }

    @Test
    fun erc20AssetRejectsMalformedContractAddress() {
        val invalid = listOf(CONTRACT_ADDRESS.dropLast(1), CONTRACT_ADDRESS + "0", CONTRACT_ADDRESS.substring(2))

        for (address in invalid) {
            assertFailsWith<IllegalArgumentException>("Address $address") { Asset.Erc20(address) }
        }
    }

    @Test
    fun transportFailurePropagatesUnchanged() = runTest {
        val failure = IllegalStateException("offline")

        val thrown = assertSuspendFailsWith<IllegalStateException> {
            EthWalletKotlin.getBalance(network(RecordingTransport(failure = failure)), owner, Asset.Eth)
        }

        assertSame(failure, thrown, "transport failure instance")
    }

    @Test
    fun transportCancellationPropagatesUnchanged() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertSuspendFailsWith<CancellationException> {
            EthWalletKotlin.getBalance(network(RecordingTransport(failure = cancellation)), owner, Asset.Eth)
        }

        assertSame(cancellation, thrown, "cancellation instance")
    }
}

private fun network(transport: JsonRpcTransport) = EthereumNetwork(chainId = 1L, transport = transport)

private fun success(result: String) = """{"jsonrpc":"2.0","id":1,"result":"$result"}"""
