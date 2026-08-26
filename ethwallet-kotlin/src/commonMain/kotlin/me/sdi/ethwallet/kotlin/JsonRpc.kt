package me.sdi.ethwallet.kotlin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private const val JSON_RPC_VERSION = "2.0"
private const val REQUEST_ID = 1
private const val ZERO_QUANTITY = "0x0"

@Serializable
private class JsonRpcRequest(
    val jsonrpc: String = JSON_RPC_VERSION,
    val id: Int = REQUEST_ID,
    val method: String,
    val params: JsonArray,
)

@Serializable
internal class CallRequest(
    val to: String,
    val data: String,
)

@Serializable
private class JsonRpcResponse(
    val jsonrpc: String,
    val id: JsonPrimitive? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
private class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

internal val jsonRpcJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal suspend fun JsonRpcTransport.call(
    method: String,
    params: JsonArray,
): JsonElement {
    val jsonRpcRequest = JsonRpcRequest(method = method, params = params)
    val requestJson = jsonRpcJson.encodeToString(jsonRpcRequest)
    val responseJson = execute(requestJson)
    val jsonRpcResponse = jsonRpcJson.decodeFromString<JsonRpcResponse>(responseJson)
    return jsonRpcResponse.extractResultOrThrow()
}

private fun JsonRpcResponse.extractResultOrThrow(): JsonElement {
    check(jsonrpc == JSON_RPC_VERSION) {
        "Unsupported JSON-RPC version '${jsonrpc}'"
    }
    error?.let { error ->
        val data = when (val errorData = error.data) {
            null, JsonNull -> ""
            is JsonPrimitive -> ": ${if (errorData.isString) errorData.content else errorData}"
            else -> ": $errorData"
        }
        throw IllegalStateException("JSON-RPC error ${error.code}: ${error.message}$data")
    }
    check(id == JsonPrimitive(REQUEST_ID)) {
        "Response id ${id} does not match request id $REQUEST_ID"
    }
    return checkNotNull(result?.takeUnless { it is JsonNull }) {
        "Response has no result and no error"
    }
}

internal fun JsonElement.asQuantity(): UInt256 {
    val hex = jsonPrimitiveContent().also { string ->
        if (!string.matches(HEX_STRING_64_SYMBOLS_EXACTLY)) {
            throw IllegalStateException("Result is not a JSON-RPC quantity: '$string'")
        }
    }
    return UInt256(hex)
}

internal fun JsonElement.asAbiWord(): UInt256 {
    val hex = jsonPrimitiveContent().also { string ->
        if (!string.matches(HEX_STRING_64_SYMBOLS_EXACTLY)) {
            throw IllegalStateException("Result is not a 32-byte ABI word: '$string'")
        }
    }
    val digits = hex.substring(2).trimStart('0')
    return UInt256(if (digits.isEmpty()) ZERO_QUANTITY else "0x$digits")
}

private fun JsonElement.jsonPrimitiveContent() = (this as JsonPrimitive).content
