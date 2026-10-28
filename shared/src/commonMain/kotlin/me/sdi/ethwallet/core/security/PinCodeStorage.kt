package me.sdi.ethwallet.core.security

/**
 * Interface for securely storing and verifying PIN codes.
 * Implementations should use secure storage mechanisms like Android KeyStore.
 */
interface PinCodeStorage {
    suspend fun storePinCode(pinCode: String)

    /**
     * Verifies if a provided PIN code matches the stored PIN. On a match, publishes the master KEK to
     * the [PinSession] and unlocks the app.
     *
     * @param pinCode The PIN code to verify
     * @return True if the PIN matches the stored PIN, false otherwise
     * @throws IllegalStateException if no PIN is stored.
     */
    suspend fun verifyPinCode(pinCode: String): Boolean

    /**
     * Checks if a PIN code has been set.
     *
     * @return True if a PIN code is stored, false otherwise
     */
    suspend fun hasPinCode(): Boolean

    /**
     * Replaces the stored PIN verifier with one for [newPin]. The master KEK, the session and the
     * attempt guard are left untouched.
     *
     * @throws IllegalStateException if no PIN is stored.
     */
    suspend fun changePinCode(newPin: String)

    suspend fun clearPinCode()
}
