package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private const val JSON_RPC_VERSION = "2.0"
private const val REQUEST_ID = 1
private const val ZERO_QUANTITY = "0x0"

sealed class JsonRpcException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Rejected(
        val code: Int,
        val rpcMessage: String,
        val data: JsonElement?,
    ) : JsonRpcException(
            message = buildString {
                append("JSON-RPC error ")
                append(code)
                append(": ")
                append(rpcMessage)
                when (data) {
                    null, JsonNull -> Unit
                    is JsonPrimitive -> append(": ").append(if (data.isString) data.content else data)
                    else -> append(": ").append(data)
                }
            },
        )

    class InvalidResponse(cause: Throwable) : JsonRpcException(
        message = "Invalid JSON-RPC response",
        cause = cause,
    )
}

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
    return try {
        val jsonRpcResponse = jsonRpcJson.decodeFromString<JsonRpcResponse>(responseJson)
        jsonRpcResponse.extractResultOrThrow()
    } catch (exception: JsonRpcException) {
        throw exception
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        throw JsonRpcException.InvalidResponse(exception)
    }
}

private fun JsonRpcResponse.extractResultOrThrow(): JsonElement {
    require(jsonrpc == JSON_RPC_VERSION) {
        "Unsupported JSON-RPC version '$jsonrpc'"
    }
    val hasResult = result != null && result !is JsonNull
    val hasError = error != null
    require(hasResult xor hasError) {
        "Response must contain exactly one non-null result or error"
    }
    if (hasError) {
        require(id == null || id == JsonPrimitive(REQUEST_ID)) {
            "Response id $id does not match request id $REQUEST_ID"
        }
        val rejected = requireNotNull(error)
        throw JsonRpcException.Rejected(
            code = rejected.code,
            rpcMessage = rejected.message,
            data = rejected.data,
        )
    }
    require(id == JsonPrimitive(REQUEST_ID)) {
        "Response id $id does not match request id $REQUEST_ID"
    }
    return requireNotNull(result)
}

internal fun JsonElement.asQuantity(): UInt256 {
    val hex = try {
        jsonPrimitiveContent().also { string ->
            require(string.matches(HEX_NUMBER_NO_LEADING_ZEROS_64_SYMBOLS_MAX)) {
                "Result is not a JSON-RPC quantity: '$string'"
            }
        }
    } catch (exception: JsonRpcException) {
        throw exception
    } catch (exception: Exception) {
        throw JsonRpcException.InvalidResponse(exception)
    }
    return try {
        UInt256(hex)
    } catch (exception: Exception) {
        throw JsonRpcException.InvalidResponse(exception)
    }
}

internal fun JsonElement.asAbiWord(): UInt256 {
    val hex = try {
        jsonPrimitiveContent().also { string ->
            require(string.matches(HEX_STRING_64_SYMBOLS_EXACTLY)) {
                "Result is not a 32-byte ABI word: '$string'"
            }
        }
    } catch (exception: JsonRpcException) {
        throw exception
    } catch (exception: Exception) {
        throw JsonRpcException.InvalidResponse(exception)
    }
    val digits = hex.substring(2).trimStart('0')
    return try {
        UInt256(if (digits.isEmpty()) ZERO_QUANTITY else "0x$digits")
    } catch (exception: Throwable) {
        throw JsonRpcException.InvalidResponse(exception)
    }
}

private fun JsonElement.jsonPrimitiveContent() = (this as JsonPrimitive).content
