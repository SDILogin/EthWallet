package me.sdi.ethwallet.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * Holds the master key-encryption key (KEK) for the current unlocked PIN session,
 * in memory only. Populated on PIN set/verify, cleared on lock, logout, or process death.
 */
interface PinSession {
    /**
     * Emits `true` while a KEK is held. This is the single source of truth for whether the app is
     * unlocked: a KEK never survives process death, so a restored process always starts locked.
     */
    val isUnlocked: StateFlow<Boolean>

    fun set(kek: ByteArray)

    fun clear()

    /**
     * @throws IllegalStateException if no session is currently unlocked.
     */
    fun getOrThrow(): ByteArray
}

class InMemoryPinSession : PinSession {
    override val isUnlocked: StateFlow<Boolean>
        field = MutableStateFlow(false)

    @Volatile
    private var kek: ByteArray? = null

    override fun set(kek: ByteArray) {
        this.kek = kek
        isUnlocked.value = true
    }

    override fun clear() {
        kek?.fill(0)
        kek = null
        isUnlocked.value = false
    }

    override fun getOrThrow(): ByteArray = kek ?: throw IllegalStateException("PIN session is locked")
}
