package me.sdi.ethwallet.core.security

/**
 * Persists the random master KEK protected by the wallet password.
 */
interface PasswordKekStorage {
    /** Generates a new random master KEK, persists it under [password] in place of any previous one, and returns it. */
    suspend fun create(password: String): ByteArray

    /**
     * @return the master KEK, or null if [password] is wrong.
     * @throws IllegalStateException if no master KEK is stored or it cannot be unwrapped on this device.
     */
    suspend fun read(password: String): ByteArray?

    suspend fun exists(): Boolean
}
