package me.sdi.ethwallet.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.kotlin.EthereumNetwork
import me.sdi.ethwallet.kotlin.JsonRpcException
import me.sdi.ethwallet.kotlin.JsonRpcTransport
import me.sdi.ethwallet.kotlin.PrivateKey
import me.sdi.ethwallet.kotlin.deriveAddress
import me.sdi.ethwallet.ui.usdcOnMainnet
import me.sdi.ethwallet.ui.usdtOnMainnet
import java.math.BigInteger
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EthManagerKotlinTest {
    private val destination = EthAddress("0x9876543210987654321098765432109876543210")
    private val network = ChainConfig(
        rpcUrl = "https://rpc.example.test",
        chainId = 1L,
        explorerBaseUrl = "https://explorer.example.test",
    )

    @Test
    fun importWallet_normalizes_mnemonic_and_derives_canonical_account() = runTest {
        val manager = manager(RecordingTransport())
        val mnemonic = "  ABANDON abandon\tabandon abandon abandon abandon " +
            "abandon abandon abandon  abandon abandon about  "

        val result = manager.importWallet(mnemonic)

        assertEquals("0x9858effd232b4033e47d90003d41ec34ecaeda94", result.address.toString().lowercase())
        assertEquals(List(11) { "abandon" } + "about", result.seedPhrase)
        assertEquals(result.address, EthAddress(result.privateKey.deriveAddress()))
    }

    @Test
    fun importWallet_rejects_invalid_mnemonic() = runTest {
        val manager = manager(RecordingTransport())

        assertFailsWith<IllegalArgumentException> {
            manager.importWallet("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon")
        }
    }

    @Test
    fun createWallet_returns_valid_round_trippable_wallet() = runTest {
        val manager = manager(RecordingTransport())

        val created = manager.createWallet()
        val imported = manager.importWallet(created.seedPhrase.joinToString(" "))

        assertEquals(12, created.seedPhrase.size)
        assertEquals(created.address, imported.address)
        assertEquals(created.privateKey.copyBytes().toList(), imported.privateKey.copyBytes().toList())
    }

    @Test
    fun getBalance_maps_eth_and_token_balances_and_preserves_individual_failures() = runTest {
        val transport = RecordingTransport { request ->
            when (request.method) {
                "eth_getBalance" -> jsonStringResult("0x2a")
                "eth_call" -> if (request.to == usdtOnMainnet.ethAddress.toString().lowercase()) {
                    jsonError(-32000, "USDT unavailable")
                } else {
                    abiWord("0x2b")
                }
                else -> error("Unexpected RPC method ${request.method}")
            }
        }
        val manager = manager(transport)

        val balances = manager.getBalance(EthAddress("0x1234567890123456789012345678901234567890"))

        assertEquals(BigInteger.valueOf(42), balances.getValue(EthContractAddress).getOrThrow())
        assertTrue(balances.getValue(usdcOnMainnet.ethAddress).isSuccess)
        val failure = balances.getValue(usdtOnMainnet.ethAddress).exceptionOrNull()
        assertIs<Exception>(failure)
        assertEquals("JSON-RPC error -32000: USDT unavailable", failure.message)
    }

    @Test
    fun getBalance_rethrows_cancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val manager = manager(RecordingTransport { throw cancellation })

        val thrown = assertFailsWith<CancellationException> {
            manager.getBalance(EthAddress("0x1234567890123456789012345678901234567890"))
        }

        assertEquals(cancellation.message, thrown.message)
    }

    @Test
    fun fetchFeeSuggestion_maps_library_fee_fields_to_wei() = runTest {
        val manager = manager(
            RecordingTransport { request ->
                assertEquals("eth_feeHistory", request.method)
                jsonRawResult(
                    "{" +
                        "\"oldestBlock\":\"0x1\"," +
                        "\"baseFeePerGas\":[\"0x64\",\"0x78\"]," +
                        "\"gasUsedRatio\":[0.5]," +
                        "\"reward\":[[\"0x5\"]]" +
                        "}",
                )
            },
        )

        assertEquals(
            expected = me.sdi.ethwallet.core.transfer.FeeSuggestion(
                baseFee = Wei(BigInteger.valueOf(120)),
                priorityFee = Wei(BigInteger.valueOf(5)),
            ),
            actual = manager.fetchFeeSuggestion(),
        )
    }

    @Test
    fun fetchFeeSuggestion_surfaces_rpc_rejection() = runTest {
        val manager = manager(RecordingTransport { jsonError(-32000, "fee unavailable") })

        val failure = assertFailsWith<JsonRpcException.Rejected> {
            manager.fetchFeeSuggestion()
        }

        assertEquals("fee unavailable", failure.rpcMessage)
    }

    @Test
    fun fetchFeeSuggestion_wraps_malformed_rpc_response() = runTest {
        val manager = manager(RecordingTransport { "not-json" })

        assertIs<JsonRpcException.InvalidResponse>(
            assertFailsWith<Exception> {
                manager.fetchFeeSuggestion()
            },
        )
    }

    @Test
    fun fetchFeeSuggestion_surfaces_transport_failure() = runTest {
        val transportFailure = IllegalStateException("socket closed")
        val manager = manager(RecordingTransport { throw transportFailure })

        val failure = assertFailsWith<IllegalStateException> {
            manager.fetchFeeSuggestion()
        }

        assertEquals(transportFailure, failure)
    }

    @Test
    fun sendTransfer_maps_eth_request_and_returns_transaction_hash() = runTest {
        val transport = RecordingTransport { request ->
            when (request.method) {
                "eth_getTransactionCount" -> jsonStringResult("0x3")
                "eth_sendRawTransaction" -> jsonStringResult("0x${"ab".repeat(32)}")
                else -> error("Unexpected RPC method ${request.method}")
            }
        }
        val manager = manager(transport)

        val privateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 123 })
        val hash = manager.sendTransfer(
            privateKey = privateKey,
            contractAddress = EthContractAddress,
            destinationAddress = destination,
            amount = BigInteger.valueOf(7),
            gasUnits = BigInteger.valueOf(21_000),
            maxPriorityFeePerGas = BigInteger.valueOf(5),
            maxFeePerGas = BigInteger.valueOf(100),
        )

        assertEquals("0x${"ab".repeat(32)}", hash)
        assertEquals(listOf("eth_getTransactionCount", "eth_sendRawTransaction"), transport.methods)
        assertTrue(privateKey.deriveAddress().startsWith("0x"))
    }

    @Test
    fun sendTransfer_maps_erc20_request_and_returns_transaction_hash() = runTest {
        val transport = RecordingTransport { request ->
            when (request.method) {
                "eth_getTransactionCount" -> jsonStringResult("0x0")
                "eth_sendRawTransaction" -> jsonStringResult("0x${"cd".repeat(32)}")
                else -> error("Unexpected RPC method ${request.method}")
            }
        }
        val manager = manager(transport)

        val hash = withTestPrivateKey { privateKey ->
            manager.sendTransfer(
                privateKey = privateKey,
                contractAddress = usdtOnMainnet.ethAddress,
                destinationAddress = destination,
                amount = BigInteger("1000000"),
                gasUnits = BigInteger.valueOf(65_000),
                maxPriorityFeePerGas = BigInteger.valueOf(5),
                maxFeePerGas = BigInteger.valueOf(100),
            )
        }

        assertEquals("0x${"cd".repeat(32)}", hash)
        val rawTransaction = transport.requests.last()["params"]!!
            .jsonArray.first().jsonPrimitive.content
        assertTrue(rawTransaction.startsWith("0x"))
    }

    @Test
    fun sendTransfer_accepts_canonical_uint256_zero_and_maximum_values() = runTest {
        val transport = RecordingTransport { request ->
            when (request.method) {
                "eth_getTransactionCount" -> jsonStringResult("0x0")
                "eth_sendRawTransaction" -> jsonStringResult("0x${"ef".repeat(32)}")
                else -> error("Unexpected RPC method ${request.method}")
            }
        }
        val manager = manager(transport)
        val maximumUInt256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

        val hash = withTestPrivateKey { privateKey ->
            manager.sendTransfer(
                privateKey = privateKey,
                contractAddress = EthContractAddress,
                destinationAddress = destination,
                amount = maximumUInt256,
                gasUnits = BigInteger.valueOf(21_000),
                maxPriorityFeePerGas = BigInteger.ZERO,
                maxFeePerGas = maximumUInt256,
            )
        }

        assertEquals("0x${"ef".repeat(32)}", hash)
        assertEquals(listOf("eth_getTransactionCount", "eth_sendRawTransaction"), transport.methods)
    }

    @Test
    fun sendTransfer_rejects_negative_and_overflowing_uint256_values() = runTest {
        val manager = manager(RecordingTransport())

        withTestPrivateKey { privateKey ->
            assertFailsWith<IllegalArgumentException> {
                manager.sendTransfer(
                    privateKey = privateKey,
                    contractAddress = EthContractAddress,
                    destinationAddress = destination,
                    amount = BigInteger.valueOf(-1),
                    gasUnits = BigInteger.ONE,
                    maxPriorityFeePerGas = BigInteger.ONE,
                    maxFeePerGas = BigInteger.ONE,
                )
            }
        }
        withTestPrivateKey { privateKey ->
            assertFailsWith<IllegalArgumentException> {
                manager.sendTransfer(
                    privateKey = privateKey,
                    contractAddress = EthContractAddress,
                    destinationAddress = destination,
                    amount = BigInteger.ONE.shiftLeft(256),
                    gasUnits = BigInteger.ONE,
                    maxPriorityFeePerGas = BigInteger.ONE,
                    maxFeePerGas = BigInteger.ONE,
                )
            }
        }
    }

    private fun manager(transport: RecordingTransport) = EthManagerKotlin(
        EthereumNetwork(network.chainId, transport),
    )

    private suspend fun <T> withTestPrivateKey(block: suspend (PrivateKey) -> T): T {
        val privateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 123 })
        return block(privateKey)
    }

    private class RecordingTransport(
        private val responder: suspend (RecordedRequest) -> String = { request ->
            error("Unexpected RPC method ${request.method}")
        },
    ) : JsonRpcTransport {
        val requests = Collections.synchronizedList(mutableListOf<kotlinx.serialization.json.JsonObject>())
        val methods get() = requests.map { it["method"]!!.jsonPrimitive.content }

        override suspend fun execute(requestJson: String): String {
            val request = Json.parseToJsonElement(requestJson).jsonObject
            requests += request
            val to = request["params"]?.jsonArray?.firstOrNull()
                ?.let { element -> element as? kotlinx.serialization.json.JsonObject }
                ?.get("to")
                ?.jsonPrimitive
                ?.content
            return responder(RecordedRequest(request["method"]!!.jsonPrimitive.content, to, request))
        }
    }

    private data class RecordedRequest(
        val method: String,
        val to: String?,
        val jsonObject: kotlinx.serialization.json.JsonObject,
    )

    private fun jsonRawResult(result: String): String =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":$result}"

    private fun jsonStringResult(result: String): String = jsonRawResult("\"$result\"")

    private fun jsonError(code: Int, message: String): String =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":$code,\"message\":\"$message\"}}"

    private fun abiWord(quantity: String): String =
        jsonStringResult("0x${quantity.removePrefix("0x").padStart(64, '0')}")
}
