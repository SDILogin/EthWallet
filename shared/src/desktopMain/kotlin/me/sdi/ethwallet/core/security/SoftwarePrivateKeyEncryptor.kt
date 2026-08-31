package me.sdi.ethwallet.core.security

import me.sdi.ethwallet.kotlin.PrivateKey
import kotlin.io.encoding.Base64

/**
 * Desktop [PrivateKeyEncryptor]: a single software AES-256-GCM layer ([KekCipher]) keyed by the
 * master KEK of the current [PinSession], so signing is impossible while the app is locked.
 *
 * There is no hardware-backed outer layer on desktop, so a stolen ciphertext is only as strong as
 * the PIN-derived envelope that guards the KEK.
 */
class SoftwarePrivateKeyEncryptor(
    private val pinSession: PinSession,
) : PrivateKeyEncryptor {
    override suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String {
        val plaintext = privateKey.copyBytes()
        return try {
            Base64.encode(KekCipher.encrypt(pinSession.getOrThrow(), plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): PrivateKey {
        val decryptedBytes = KekCipher.decrypt(pinSession.getOrThrow(), Base64.decode(encryptedBase64))
        return try {
            PrivateKey.fromBytes(decryptedBytes)
        } finally {
            decryptedBytes.fill(0)
        }
    }
}
