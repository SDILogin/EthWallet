package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakePinSession
import kotlin.test.Test
import kotlin.test.assertEquals

class PinSessionDataCleanerTest {
    @Test
    fun clean_clears_the_pin_session() = runTest {
        val pinSession = FakePinSession()

        PinSessionDataCleaner(pinSession).clean()

        assertEquals(1, pinSession.clearCallCount, "clean() should clear the PIN session")
    }
}
