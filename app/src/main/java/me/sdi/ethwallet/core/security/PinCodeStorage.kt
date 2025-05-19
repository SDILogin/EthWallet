package me.sdi.ethwallet.core.security

/**
 * Interface for securely storing and verifying PIN codes.
 * Implementations should use secure storage mechanisms like Android KeyStore.
 */
interface PinCodeStorage {
    suspend fun storePinCode(pinCode: String)

    /**
     * Verifies if a provided PIN code matches the stored PIN.
     *
     * @param pinCode The PIN code to verify
     * @return True if the PIN matches the stored PIN, false otherwise
     */
    suspend fun verifyPinCode(pinCode: String): Boolean

    /**
     * Checks if a PIN code has been set.
     *
     * @return True if a PIN code is stored, false otherwise
     */
    suspend fun hasPinCode(): Boolean

    suspend fun clearPinCode()
}
