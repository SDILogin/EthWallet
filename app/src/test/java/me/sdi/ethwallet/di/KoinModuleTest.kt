package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class KoinModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun checkAllModules() {
        appModule.verify(
            injections = injectedParameters(
                definition<HttpClient>(HttpClientEngine::class),
            ),
        )
    }
}
