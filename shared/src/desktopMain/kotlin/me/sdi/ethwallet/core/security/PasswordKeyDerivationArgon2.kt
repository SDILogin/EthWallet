package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

private const val ARGON2_MEMORY_KIB = 65_536
private const val ARGON2_ITERATIONS = 3
private const val ARGON2_PARALLELISM = 1
private const val KEY_BYTES = 32

/** Argon2id with m=64 MiB, t=3, p=1, above the OWASP minimum (m=19 MiB, t=2, p=1). */
class PasswordKeyDerivationArgon2 : PasswordKeyDerivation {
    override suspend fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(ARGON2_MEMORY_KIB)
            .withIterations(ARGON2_ITERATIONS)
            .withParallelism(ARGON2_PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        ByteArray(KEY_BYTES).also { key -> generator.generateBytes(password, key) }
    }
}
