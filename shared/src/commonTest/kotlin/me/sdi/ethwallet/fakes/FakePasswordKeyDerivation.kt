package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PasswordKeyDerivation
import java.security.MessageDigest

class FakePasswordKeyDerivation : PasswordKeyDerivation {
    /** The password arrays as passed in (not copies), so a test can check that the caller zeroed them. */
    val passedPasswords = mutableListOf<ByteArray>()

    override suspend fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        passedPasswords += password
        return MessageDigest.getInstance("SHA-256").digest(salt + password)
    }
}
