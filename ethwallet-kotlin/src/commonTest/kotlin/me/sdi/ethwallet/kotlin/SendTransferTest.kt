package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val SENDER = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf"
private const val ETH_DESTINATION = "0x9858effd232b4033e47d90003d41ec34ecaeda94"
private const val ERC20_CONTRACT = "0x1111111111111111111111111111111111111111"
private const val ERC20_DESTINATION = "0x2222222222222222222222222222222222222222"
private val TRANSACTION_HASH = "0x${"a".repeat(64)}"

class SendTransferTest {
    @Test
    fun sendEthTransferResolvesPendingNonceAndSubmitsSignedTransaction() = runTest {
        val destination = EthAddress(ETH_DESTINATION)
        val privateKey = privateKeyOne()
        val transport = TransferRecordingTransport(
            responses = listOf(successResponse("0x7"), successResponse(TRANSACTION_HASH)),
        )

        val request = transferRequest(destination = destination, asset = Asset.Eth, amount = UInt256("0x1"))

        assertEquals(
            TransactionHash(TRANSACTION_HASH),
            EthWalletKotlin.sendTransfer(network(transport), privateKey, request),
        )
        assertNonceRequest(transport.requests[0])
        assertEquals("eth_sendRawTransaction", requestMethod(transport.requests[1]))
        assertEquals(
            signedTransaction(
                privateKey = privateKey,
                chainId = 1L,
                nonce = "0x7",
                destination = destination,
                value = "0x1",
            ),
            rawTransactionValue(transport.requests[1]),
        )
    }

    @Test
    fun sendErc20TransferUsesContractDestinationZeroEthValueAndTransferCalldata() = runTest {
        val recipient = EthAddress(ERC20_DESTINATION)
        val contract = EthAddress(ERC20_CONTRACT)
        val privateKey = privateKeyOne()
        val transport = TransferRecordingTransport(
            responses = listOf(successResponse("0x7"), successResponse(TRANSACTION_HASH)),
        )
        val amount = UInt256("0x1234")
        val request = transferRequest(
            destination = recipient,
            asset = Asset.Erc20(contract),
            amount = amount,
        )

        assertEquals(
            TransactionHash(TRANSACTION_HASH),
            EthWalletKotlin.sendTransfer(network(transport), privateKey, request),
        )
        assertNonceRequest(transport.requests[0])
        assertEquals("eth_sendRawTransaction", requestMethod(transport.requests[1]))
        assertEquals(
            signedTransaction(
                privateKey = privateKey,
                chainId = 1L,
                nonce = "0x7",
                destination = contract,
                value = "0x0",
                data = "0xa9059cbb" +
                    ERC20_DESTINATION.removePrefix("0x").padStart(64, '0') +
                    amount.hex.removePrefix("0x").padStart(64, '0'),
            ),
            rawTransactionValue(transport.requests[1]),
        )
    }

    @Test
    fun nonceRpcRejectionIsPreservedAndPreventsSubmission() = runTest {
        val transport = TransferRecordingTransport(
            responses = listOf(errorResponse(code = -32000, message = "nonce unavailable")),
        )

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
        }

        assertEquals(-32000, rejected.code)
        assertEquals("nonce unavailable", rejected.rpcMessage)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun rawSubmissionRpcRejectionIsPreserved() = runTest {
        val transport = TransferRecordingTransport(
            responses = listOf(
                successResponse("0x7"),
                errorResponse(code = -32003, message = "insufficient funds"),
            ),
        )

        val rejected = assertSuspendFailsWith<JsonRpcException.Rejected> {
            EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
        }

        assertEquals(-32003, rejected.code)
        assertEquals("insufficient funds", rejected.rpcMessage)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun malformedNonceResponseIsInvalidResponseAndPreventsSubmission() = runTest {
        val malformedResponses = listOf(
            "[]",
            """{"jsonrpc":"1.0","id":1,"result":"0x7"}""",
            """{"jsonrpc":"2.0","id":2,"result":"0x7"}""",
            """{"jsonrpc":"2.0","id":1,"result":"0x07"}""",
            """{"jsonrpc":"2.0","id":1,"result":7}""",
            """{"jsonrpc":"2.0","id":1,"result":null}""",
        )

        for (response in malformedResponses) {
            val transport = TransferRecordingTransport(responses = listOf(response))
            val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse>(response) {
                EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
            }

            assertTrue(invalid.cause != null)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun malformedTransactionHashResponseIsInvalidResponse() = runTest {
        val malformedResponses = listOf(
            successResponse("0x7") to successResponse("0x1"),
            successResponse("0x7") to successResponse("0x${"a".repeat(63)}"),
            successResponse("0x7") to successResponse("0x${"g".repeat(64)}"),
            successResponse("0x7") to """{"jsonrpc":"2.0","id":1,"result":7}""",
            successResponse("0x7") to "[]",
        )

        for ((nonceResponse, hashResponse) in malformedResponses) {
            val transport = TransferRecordingTransport(responses = listOf(nonceResponse, hashResponse))
            val invalid = assertSuspendFailsWith<JsonRpcException.InvalidResponse>(hashResponse) {
                EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
            }

            assertTrue(invalid.cause != null)
            assertEquals(2, transport.requests.size)
        }
    }

    @Test
    fun transportFailurePropagatesUnchangedFromEitherRpcCall() = runTest {
        for (failureAtRequestIndex in 0..1) {
            val failure = IllegalStateException("offline")
            val transport = TransferRecordingTransport(
                responses = listOf(successResponse("0x7")),
                failure = failure,
                failureAtRequestIndex = failureAtRequestIndex,
            )

            val thrown = assertSuspendFailsWith<IllegalStateException> {
                EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
            }

            assertSame(failure, thrown)
            assertEquals(failureAtRequestIndex + 1, transport.requests.size)
        }
    }

    @Test
    fun transportCancellationPropagatesUnchangedFromEitherRpcCall() = runTest {
        for (failureAtRequestIndex in 0..1) {
            val cancellation = CancellationException("cancelled")
            val transport = TransferRecordingTransport(
                responses = listOf(successResponse("0x7")),
                failure = cancellation,
                failureAtRequestIndex = failureAtRequestIndex,
            )

            val thrown = assertSuspendFailsWith<CancellationException> {
                EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), ethRequest())
            }

            assertSame(cancellation, thrown)
            assertEquals(failureAtRequestIndex + 1, transport.requests.size)
        }
    }

    @Test
    fun signingFailureDoesNotAttemptRawSubmission() = runTest {
        val transport = TransferRecordingTransport(responses = listOf(successResponse("0x7")))
        val invalidFeeRequest = ethRequest(
            maxPriorityFeePerGas = UInt256("0x2"),
            maxFeePerGas = UInt256("0x1"),
        )

        assertSuspendFailsWith<IllegalArgumentException> {
            EthWalletKotlin.sendTransfer(network(transport), privateKeyOne(), invalidFeeRequest)
        }

        assertEquals(1, transport.requests.size)
        assertEquals("eth_getTransactionCount", requestMethod(transport.requests.single()))
    }
}

private class TransferRecordingTransport(
    private val responses: List<String> = emptyList(),
    private val failure: Throwable? = null,
    private val failureAtRequestIndex: Int = 0,
) : JsonRpcTransport {
    val requests = mutableListOf<String>()

    override suspend fun execute(requestJson: String): String {
        requests += requestJson
        if (requests.lastIndex == failureAtRequestIndex) {
            failure?.let { throw it }
        }
        return responses.getOrNull(requests.lastIndex)
            ?: error("TransferRecordingTransport has no response for request ${requests.size}")
    }
}

private fun privateKeyOne(): PrivateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 1 })

private fun network(transport: JsonRpcTransport) = EthereumNetwork(chainId = 1L, transport = transport)

private fun ethRequest(
    maxPriorityFeePerGas: UInt256 = UInt256("0x1"),
    maxFeePerGas: UInt256 = UInt256("0x2"),
) = transferRequest(
    destination = EthAddress(ETH_DESTINATION),
    asset = Asset.Eth,
    amount = UInt256("0x1"),
    maxPriorityFeePerGas = maxPriorityFeePerGas,
    maxFeePerGas = maxFeePerGas,
)

private fun transferRequest(
    destination: EthAddress,
    asset: Asset,
    amount: UInt256,
    maxPriorityFeePerGas: UInt256 = UInt256("0x1"),
    maxFeePerGas: UInt256 = UInt256("0x2"),
) = TransferRequest(
    destination = destination,
    asset = asset,
    amount = amount,
    gasUnits = UInt256("0x5208"),
    maxPriorityFeePerGas = maxPriorityFeePerGas,
    maxFeePerGas = maxFeePerGas,
)

private suspend fun signedTransaction(
    privateKey: PrivateKey,
    chainId: Long,
    nonce: String,
    destination: EthAddress,
    value: String,
    data: String = "0x",
): String = "0x${privateKey.signTransaction(
    Eip1559TransactionPayload(
        chainId = chainId,
        nonce = UInt256(nonce),
        maxPriorityFeePerGas = UInt256("0x1"),
        maxFeePerGas = UInt256("0x2"),
        gasLimit = UInt256("0x5208"),
        destination = destination,
        value = UInt256(value),
        data = data,
    ),
).toHexString()}"

private fun assertNonceRequest(request: String) {
    val json = Json.parseToJsonElement(request).jsonObject
    assertEquals(JsonPrimitive("eth_getTransactionCount"), json["method"])
    assertEquals(
        buildJsonArray {
            add(SENDER)
            add("pending")
        },
        json["params"],
    )
}

private fun rawTransactionValue(request: String): String = Json.parseToJsonElement(request)
    .jsonObject["params"]!!.jsonArray.single().jsonPrimitive.content

private fun requestMethod(request: String): String = Json.parseToJsonElement(request)
    .jsonObject["method"]!!.jsonPrimitive.content

private fun successResponse(result: String): String = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", 1)
    put("result", result)
}.toString()

private fun errorResponse(code: Int, message: String): String = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", 1)
    put(
        "error",
        buildJsonObject {
            put("code", code)
            put("message", message)
        },
    )
}.toString()
