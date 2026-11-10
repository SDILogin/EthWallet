package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.KekCipher
import me.sdi.ethwallet.core.security.MasterKekProtection
import java.security.SecureRandom

private const val WRAPPING_KEY_BYTES = 32

class FakeMasterKekProtection : MasterKekProtection {
    override suspend fun createWrappingKey(): ByteArray = ByteArray(WRAPPING_KEY_BYTES).also(SecureRandom()::nextBytes)

    override suspend fun wrap(wrappingKey: ByteArray, masterKek: ByteArray): ByteArray =
        KekCipher.encrypt(wrappingKey, masterKek)

    override suspend fun unwrap(wrappingKey: ByteArray, wrappedMasterKek: ByteArray): ByteArray =
        KekCipher.decrypt(wrappingKey, wrappedMasterKek)
}
