package me.sdi.ethwallet.core.security

/** Derives a 256-bit key from password bytes; brute-force resistant (memory-hard) in production. */
interface PasswordKeyDerivation {
    suspend fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray
}
