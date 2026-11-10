package me.sdi.ethwallet.core.security

/**
 * Wraps key material under a wrapping key that the caller keeps only as an opaque handle.
 */
interface MasterKekProtection {
    /** Creates a new wrapping key and returns its opaque handle, which is safe to persist. */
    suspend fun createWrappingKey(): ByteArray

    suspend fun wrap(wrappingKey: ByteArray, payload: ByteArray): ByteArray

    suspend fun unwrap(wrappingKey: ByteArray, wrapped: ByteArray): ByteArray
}
