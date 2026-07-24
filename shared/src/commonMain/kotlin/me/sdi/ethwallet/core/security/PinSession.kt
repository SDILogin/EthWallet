package me.sdi.ethwallet.core.security

import kotlin.concurrent.Volatile

/**
 * Holds the master key-encryption key (KEK) for the current unlocked PIN session,
 * in memory only. Populated on PIN set/verify, cleared on lock, logout, or process death.
 */
interface PinSession {
    fun set(kek: ByteArray)

    fun clear()

    /**
     * @throws IllegalStateException if no session is currently unlocked.
     */
    fun getOrThrow(): ByteArray
}

class InMemoryPinSession : PinSession {
    @Volatile
    private var kek: ByteArray? = null

    override fun set(kek: ByteArray) {
        this.kek = kek
    }

    override fun clear() {
        kek?.fill(0)
        kek = null
    }

    override fun getOrThrow(): ByteArray = kek ?: throw IllegalStateException("PIN session is locked")
}
