package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JsonRpcTransportKtorTest {
    @Test
    fun execute_posts_exact_json_and_returns_exact_response() = runTest {
        val requestJson = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\"}"
        val responseJson = "{\"jsonrpc\":\"2.0\",\"result\":\"0xaa36a7\"}"
        var request: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = Dispatchers.Unconfined
                addHandler {
                    request = it
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }

        val result = JsonRpcTransportKtor(client, "https://rpc.example.test").execute(requestJson)

        assertEquals(responseJson, result)
        assertEquals(HttpMethod.Post, request?.method)
        assertEquals("https://rpc.example.test", request?.url.toString())
        assertEquals(ContentType.Application.Json, request?.body?.contentType)
        assertEquals(ContentType.Application.Json, request?.headers?.get(HttpHeaders.Accept)?.let(ContentType::parse))
        assertEquals(requestJson, request?.body?.asText())
    }

    @Test
    fun execute_preserves_empty_and_malformed_response_bodies() = runTest {
        val responses = listOf("", "not-json")
        responses.forEach { responseJson ->
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { respond(responseJson, HttpStatusCode.OK) }
                }
            }

            assertEquals(responseJson, JsonRpcTransportKtor(client, "https://rpc.example.test").execute("{}"))
        }
    }

    @Test
    fun execute_preserves_transport_exception_identity() = runTest {
        val failure = SentinelTransportFailure()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = Dispatchers.Unconfined
                addHandler { throw failure }
            }
        }

        val thrown = assertFailsWith<SentinelTransportFailure> {
            JsonRpcTransportKtor(client, "https://rpc.example.test").execute("{}")
        }

        assertSame(failure, thrown)
    }

    @Test
    fun execute_preserves_cancellation_exception() = runTest {
        val failure = SentinelCancellationException()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = Dispatchers.Unconfined
                addHandler { throw failure }
            }
        }

        val thrown = assertFailsWith<SentinelCancellationException> {
            JsonRpcTransportKtor(client, "https://rpc.example.test").execute("{}")
        }

        assertSame(failure, thrown)
    }

    private fun OutgoingContent.asText(): String = when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        is TextContent -> text
        else -> error("Unexpected request body: $this")
    }

    private class SentinelTransportFailure : IllegalStateException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private class SentinelCancellationException : CancellationException(null) {
        override fun fillInStackTrace(): Throwable = this
    }
}
