package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.kotlin.PrivateKey
import java.math.BigInteger

class FakePrivateKeyEncryptor : PrivateKeyEncryptor {
    var shouldThrowException = false

    override suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String {
        if (shouldThrowException) throw RuntimeException("Encryption failed")
        val bytes = privateKey.copyBytes()
        return "encrypted_" + BigInteger(1, bytes).toString(16)
    }

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): PrivateKey {
        if (shouldThrowException) throw RuntimeException("Decryption failed")
        val value = BigInteger(encryptedBase64.removePrefix("encrypted_"), 16)
        val source = value.toByteArray()
        val bytes = ByteArray(32)
        val sourceStart = if (source.size == bytes.size + 1) 1 else 0
        require(source.size <= bytes.size + 1 && (sourceStart == 0 || source[0].toInt() == 0)) {
            "Private key exceeds 32 bytes"
        }
        val copyLength = source.size - sourceStart
        source.copyInto(bytes, bytes.size - copyLength, sourceStart)
        return PrivateKey.fromBytes(bytes)
    }
}
