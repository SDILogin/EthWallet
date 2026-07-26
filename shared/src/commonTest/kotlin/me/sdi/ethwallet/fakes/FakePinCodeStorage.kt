package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PinCodeStorage

class FakePinCodeStorage : PinCodeStorage {
    var storedPin: String? = null
    var shouldThrowException = false
    var clearPinCalledCount = 0
    var storePinCalledCount = 0

    override suspend fun storePinCode(pinCode: String) {
        if (shouldThrowException) throw SecurityException("Simulated storage failure")
        storedPin = pinCode
        storePinCalledCount++
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        if (shouldThrowException) throw SecurityException("Simulated verification failure")
        return pinCode == storedPin
    }

    override suspend fun hasPinCode(): Boolean {
        if (shouldThrowException) throw SecurityException("Simulated check failure")
        return storedPin != null
    }

    override suspend fun clearPinCode() {
        if (shouldThrowException) throw SecurityException("Simulated clear failure")
        storedPin = null
        clearPinCalledCount++
    }
}
