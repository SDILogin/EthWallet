package me.sdi.ethwallet.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.stopKoin
import org.koin.test.verify.verify

class KoinModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun checkAllModules() {
        try {
            stopKoin()
        } catch (e: Exception) {
            // Koin might not be running
        }
        appModule.verify()
    }
}
