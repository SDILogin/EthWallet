package me.sdi.ethwallet.core.security

import me.sdi.ethwallet.data.UserActivityManager

class PasswordInteractor(
    private val passwordKekStorage: PasswordKekStorage,
    private val pinSession: PinSession,
    private val userActivityManager: UserActivityManager,
) {
    /** Sets the wallet password, creates the master KEK and unlocks the app. */
    suspend fun setPassword(password: String) {
        pinSession.set(passwordKekStorage.create(password))
        userActivityManager.onAppUnlocked()
    }

    /** @return True if [password] matched and the app is unlocked. */
    suspend fun unlock(password: String): Boolean {
        val masterKek = passwordKekStorage.read(password)
        if (masterKek != null) {
            pinSession.set(masterKek)
            userActivityManager.onAppUnlocked()
            return true
        } else {
            return false
        }
    }

    suspend fun hasPassword(): Boolean = passwordKekStorage.exists()
}
