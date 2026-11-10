package me.sdi.ethwallet.core.security

/**
 * Persists the random master KEK that wraps the wallet private keys.
 */
interface MasterKekStorage {
    /** Generates a new random master KEK, persists it in place of any previous one, and returns it. */
    suspend fun create(): ByteArray

    /**
     * @throws UserPresenceCancelledException if the user cancels the system authentication that guards the KEK.
     * @throws IllegalStateException if no master KEK is stored or it cannot be unwrapped.
     */
    suspend fun read(): ByteArray

    suspend fun clear()
}
