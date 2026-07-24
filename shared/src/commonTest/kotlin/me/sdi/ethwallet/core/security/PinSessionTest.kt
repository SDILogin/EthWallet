package me.sdi.ethwallet.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PinSessionTest {
    private val pinSession = InMemoryPinSession()

    @Test
    fun getOrThrow_returns_the_KEK_passed_to_set() {
        val kek = byteArrayOf(1, 2, 3, 4)

        pinSession.set(kek)

        assertContentEquals(kek, pinSession.getOrThrow(), "require() should return the KEK passed to set()")
    }

    @Test
    fun getOrThrow_throws_when_no_session_was_set() {
        assertFailsWith<IllegalStateException>("require() should throw when locked") {
            pinSession.getOrThrow()
        }
    }

    @Test
    fun getOrThrow_throws_after_clear() {
        pinSession.set(byteArrayOf(1, 2, 3, 4))

        pinSession.clear()

        assertFailsWith<IllegalStateException>("require() should throw after clear()") {
            pinSession.getOrThrow()
        }
    }

    @Test
    fun clear_zero_fills_the_KEK_bytes() {
        val kek = byteArrayOf(1, 2, 3, 4)
        pinSession.set(kek)

        pinSession.clear()

        assertTrue(kek.all { it == 0.toByte() }, "clear() should zero-fill the KEK bytes in place")
    }
}
