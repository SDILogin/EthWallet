package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.MasterKekStorage

class FakeMasterKekStorage : MasterKekStorage {
    var storedKek: ByteArray? = null
    var createCallCount = 0
    var readFailure: Exception? = null

    override suspend fun create(): ByteArray {
        createCallCount++
        val kek = ByteArray(KEK_SIZE) { createCallCount.toByte() }
        storedKek = kek
        return kek.copyOf()
    }

    override suspend fun read(): ByteArray {
        readFailure?.let { throw it }
        return checkNotNull(storedKek) { "No master KEK stored" }.copyOf()
    }

    override suspend fun clear() {
        storedKek = null
    }

    private companion object {
        private const val KEK_SIZE = 32
    }
}
