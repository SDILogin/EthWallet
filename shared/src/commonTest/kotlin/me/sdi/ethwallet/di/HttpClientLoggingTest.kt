package me.sdi.ethwallet.di

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import me.sdi.ethwallet.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpClientLoggingTest {
    @AfterTest
    fun tearDown() {
        Logger.enabled = false
    }

    @Test
    fun `disabled logger does not install HTTP logging`() {
        Logger.enabled = false

        val client = createHttpClient(MockEngine { respondOk() })

        assertNull(client.pluginOrNull(Logging), "HTTP logging must not be installed when the logger is disabled")
        client.close()
    }

    @Test
    fun `enabled logger installs HTTP logging`() {
        Logger.enabled = true

        val client = createHttpClient(MockEngine { respondOk() })

        assertNotNull(client.pluginOrNull(Logging), "HTTP logging must be installed when the logger is enabled")
        client.close()
    }
}
