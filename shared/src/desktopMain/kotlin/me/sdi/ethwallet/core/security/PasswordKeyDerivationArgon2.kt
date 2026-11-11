package me.sdi.ethwallet.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

private const val KEY_BYTES = 32

/** Argon2id (version 1.3) producing a 256-bit key. */
class PasswordKeyDerivationArgon2(
    private val memoryKib: Int,
    private val iterations: Int,
    private val parallelism: Int,
) : PasswordKeyDerivation {
    override suspend fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        ByteArray(KEY_BYTES).also { key -> generator.generateBytes(password, key) }
    }
}
