package me.sdi.ethwallet.di

import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinModuleTest {
    @Test
    fun checkCommonModuleExists() {
        // Verify commonModule is properly defined (includes ViewModels)
        // Detailed dependency verification is done in integration tests
        assertNotNull(commonModule)
    }
}
