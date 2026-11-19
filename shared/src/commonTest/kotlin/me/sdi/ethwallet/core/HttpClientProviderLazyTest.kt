package me.sdi.ethwallet.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HttpClientProviderLazyTest {
    @Test
    fun provideCreatesClientOnceOffCallerThread() = runTest {
        val callerThread = Thread.currentThread()
        val creationThreads = mutableListOf<Thread>()
        val provider = HttpClientProviderLazy {
            creationThreads += Thread.currentThread()
            HttpClient(MockEngine { respondOk() })
        }

        val first = provider.provide()
        val second = provider.provide()

        assertSame(first, second, "Provider should return the same client for each call")
        assertNotSame(callerThread, creationThreads.single(), "Client should be created off the caller thread")
    }
}
