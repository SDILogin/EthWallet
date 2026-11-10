package me.sdi.ethwallet.core.security

/**
 * Wraps the master KEK under a wrapping key that [MasterKekStorageSoftware] keeps only as an opaque handle.
 */
interface MasterKekProtection {
    /** Creates a new wrapping key and returns its opaque handle, which is safe to persist. */
    suspend fun createWrappingKey(): ByteArray

    suspend fun wrap(wrappingKey: ByteArray, masterKek: ByteArray): ByteArray

    suspend fun unwrap(wrappingKey: ByteArray, wrappedMasterKek: ByteArray): ByteArray
}
