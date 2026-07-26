package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.sdi.ethwallet.core.security.PinSession

class FakePinSession : PinSession {
    var lastKek: ByteArray? = null
    var clearCallCount = 0

    override val isUnlocked: StateFlow<Boolean>
        field = MutableStateFlow(false)

    override fun set(kek: ByteArray) {
        lastKek = kek
        isUnlocked.value = true
    }

    override fun clear() {
        lastKek = null
        clearCallCount++
        isUnlocked.value = false
    }

    override fun getOrThrow(): ByteArray = lastKek ?: throw IllegalStateException("PIN session is locked")
}
