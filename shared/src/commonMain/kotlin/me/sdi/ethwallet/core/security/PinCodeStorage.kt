package me.sdi.ethwallet.core.security

/**
 * Persists the salted hash of the app-lock PIN.
 */
interface PinCodeStorage {
    suspend fun storePinCode(pinCode: String)

    /**
     * @return True if [pinCode] matches the stored PIN.
     * @throws IllegalStateException if no PIN is stored.
     */
    suspend fun verifyPinCode(pinCode: String): Boolean

    suspend fun hasPinCode(): Boolean

    /** Replaces the stored PIN hash with one for [newPin]. */
    suspend fun changePinCode(newPin: String)

    suspend fun clearPinCode()
}
