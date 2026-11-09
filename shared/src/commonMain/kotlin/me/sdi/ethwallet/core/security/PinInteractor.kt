package me.sdi.ethwallet.core.security

import me.sdi.ethwallet.data.UserActivityManager

class PinInteractor(
    private val pinCodeStorage: PinCodeStorage,
    private val masterKekStorage: MasterKekStorage,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val pinSession: PinSession,
    private val userActivityManager: UserActivityManager,
) {
    /** Sets the first PIN, creates the master KEK and unlocks the app. */
    suspend fun setPin(pin: String) {
        pinAttemptsGuard.bootstrap()
        // The PIN hash is written last: once hasPin() is true, the master KEK is readable.
        val masterKek = masterKekStorage.create()
        pinCodeStorage.storePinCode(pin)
        pinSession.set(masterKek)
        userActivityManager.onAppUnlocked()
    }

    /**
     * Unlocks the app with the master KEK when [pin] matches the stored PIN.
     *
     * @return True if the PIN matched.
     * @throws IllegalStateException if no PIN is stored.
     */
    suspend fun unlock(pin: String): Boolean {
        if (pinCodeStorage.verifyPinCode(pin)) {
            pinSession.set(masterKekStorage.read())
            userActivityManager.onAppUnlocked()
            return true
        } else {
            return false
        }
    }

    /** Replaces the PIN. The master KEK and the unlocked session are kept. */
    suspend fun changePin(pin: String) {
        pinCodeStorage.changePinCode(pin)
    }

    suspend fun hasPin(): Boolean = pinCodeStorage.hasPinCode()
}
