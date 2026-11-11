package me.sdi.ethwallet.desktop

import me.sdi.ethwallet.core.security.KekCipher
import me.sdi.ethwallet.core.security.MasterKekProtection
import java.security.SecureRandom

private const val WRAPPING_KEY_BYTES = 32

/** Replaces the Secure Enclave, the only hardware-only part of the Desktop key protection. */
class FakeMasterKekProtection : MasterKekProtection {
    override suspend fun createWrappingKey(): ByteArray = ByteArray(WRAPPING_KEY_BYTES).also(SecureRandom()::nextBytes)

    override suspend fun wrap(wrappingKey: ByteArray, payload: ByteArray): ByteArray =
        KekCipher.encrypt(wrappingKey, payload)

    override suspend fun unwrap(wrappingKey: ByteArray, wrapped: ByteArray): ByteArray =
        KekCipher.decrypt(wrappingKey, wrapped)
}
