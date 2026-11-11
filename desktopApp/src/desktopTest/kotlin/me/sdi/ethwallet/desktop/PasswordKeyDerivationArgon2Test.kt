package me.sdi.ethwallet.desktop

import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.security.PasswordKeyDerivationArgon2
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordKeyDerivationArgon2Test {
    @Test
    fun argon2id_matches_the_reference_implementation_test_vector() = runBlocking {
        // phc-winner-argon2 test vector: Argon2id v1.3, m=65536 KiB, t=2, p=1, "password" / "somesalt".
        val keyDerivation = PasswordKeyDerivationArgon2(memoryKib = 65_536, iterations = 2, parallelism = 1)

        val key = keyDerivation.deriveKey(password = "password".encodeToByteArray(), salt = "somesalt".encodeToByteArray())

        assertEquals(
            "Argon2id must match the reference implementation",
            "09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7",
            key.toHexString(),
        )
    }
}
