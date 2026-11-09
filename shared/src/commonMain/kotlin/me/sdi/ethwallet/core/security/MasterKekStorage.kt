package me.sdi.ethwallet.core.security

/**
 * Persists the random master KEK that wraps the wallet private keys.
 */
interface MasterKekStorage {
    /** Generates a new random master KEK, persists it in place of any previous one, and returns it. */
    suspend fun create(): ByteArray

    /** @throws IllegalStateException if no master KEK is stored. */
    suspend fun read(): ByteArray

    suspend fun clear()
}
