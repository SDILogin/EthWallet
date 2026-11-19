package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface HttpClientProvider {
    suspend fun provide(): HttpClient
}

class HttpClientProviderLazy(create: () -> HttpClient) : HttpClientProvider {
    private val client by lazy(create)

    override suspend fun provide(): HttpClient = withContext(Dispatchers.IO) { client }
}
