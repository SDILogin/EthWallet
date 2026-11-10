package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PasswordKekStorage

class FakePasswordKekStorage : PasswordKekStorage {
    var storedPassword: String? = null
    var storedKek: ByteArray? = null
    var readFailure: Exception? = null
    var createFailure: Exception? = null

    override suspend fun create(password: String): ByteArray {
        createFailure?.let { throw it }
        val kek = ByteArray(KEK_SIZE) { 7 }
        storedPassword = password
        storedKek = kek
        return kek.copyOf()
    }

    override suspend fun read(password: String): ByteArray? {
        readFailure?.let { throw it }
        val kek = checkNotNull(storedKek) { "No master KEK stored" }
        return if (password == storedPassword) kek.copyOf() else null
    }

    override suspend fun exists(): Boolean = storedKek != null

    private companion object {
        private const val KEK_SIZE = 32
    }
}
