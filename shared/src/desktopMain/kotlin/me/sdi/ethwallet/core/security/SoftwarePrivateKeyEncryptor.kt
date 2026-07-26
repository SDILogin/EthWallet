package me.sdi.ethwallet.core.security

import java.math.BigInteger
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
    override suspend fun encryptPrivateKey(privateKey: BigInteger, address: String): String =
        Base64.encode(KekCipher.encrypt(pinSession.getOrThrow(), privateKey.toByteArray()))

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger =
        BigInteger(KekCipher.decrypt(pinSession.getOrThrow(), Base64.decode(encryptedBase64)))
}
