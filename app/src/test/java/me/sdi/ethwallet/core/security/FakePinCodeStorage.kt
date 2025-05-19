package me.sdi.ethwallet.core.security

/**
 * A fake implementation of PinCodeStorage for testing purposes.
 * Stores the PIN in-memory without any encryption.
 */
class FakePinCodeStorage : PinCodeStorage {
    private var storedPin: String? = null
    private var shouldThrowException = false

    /**
     * Set this to true to simulate failures in the storage operations.
     */
    fun setShouldThrowException(shouldThrow: Boolean) {
        shouldThrowException = shouldThrow
    }

    override suspend fun storePinCode(pinCode: String): Boolean {
        if (shouldThrowException) {
            throw SecurityException("Simulated storage failure")
        }
        storedPin = pinCode
        return true
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        if (shouldThrowException) {
            throw SecurityException("Simulated verification failure")
        }
        return pinCode == storedPin
    }

    override suspend fun hasPinCode(): Boolean {
        if (shouldThrowException) {
            throw SecurityException("Simulated check failure")
        }
        return storedPin != null
    }

    override suspend fun clearPinCode(): Boolean {
        if (shouldThrowException) {
            throw SecurityException("Simulated clear failure")
        }
        storedPin = null
        return true
    }
}
