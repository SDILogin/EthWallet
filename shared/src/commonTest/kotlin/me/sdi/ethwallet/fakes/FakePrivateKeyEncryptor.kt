package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import java.math.BigInteger

class FakePrivateKeyEncryptor : PrivateKeyEncryptor {
    var shouldThrowException = false

    override suspend fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
        if (shouldThrowException) throw RuntimeException("Encryption failed")
        return "encrypted_" + privateKey.toString(16)
    }

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
        if (shouldThrowException) throw RuntimeException("Decryption failed")
        return BigInteger(encryptedBase64.removePrefix("encrypted_"), 16)
    }
}
