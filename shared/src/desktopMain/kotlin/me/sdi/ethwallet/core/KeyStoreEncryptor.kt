package me.sdi.ethwallet.core

import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import java.math.BigInteger

class KeyStoreEncryptor : PrivateKeyEncryptor {
    override suspend fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
        return privateKey.toString(16)
    }

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
        return BigInteger(encryptedBase64, 16)
    }
}
