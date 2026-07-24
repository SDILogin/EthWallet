package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PinSession

class FakePinSession : PinSession {
    var lastKek: ByteArray? = null
    var clearCallCount = 0

    override fun set(kek: ByteArray) {
        lastKek = kek
    }

    override fun clear() {
        lastKek = null
        clearCallCount++
    }

    override fun getOrThrow(): ByteArray = lastKek ?: throw IllegalStateException("PIN session is locked")
}
