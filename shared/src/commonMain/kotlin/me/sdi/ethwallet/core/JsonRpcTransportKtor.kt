package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import me.sdi.ethwallet.kotlin.JsonRpcTransport

class JsonRpcTransportKtor(
    private val httpClient: HttpClient,
    private val rpcUrl: String,
) : JsonRpcTransport {
    override suspend fun execute(requestJson: String): String =
        httpClient
            .post(rpcUrl) {
                setBody(requestJson)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
            .body()
}
