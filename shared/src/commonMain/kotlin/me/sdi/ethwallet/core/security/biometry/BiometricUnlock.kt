package me.sdi.ethwallet.core.security.biometry

/**
 * `status()`/`enable()`/`unlock()` never throw except `CancellationException`.
 * `unlock()` ends with the session KEK set and the app unlocked on [BiometricResult.Success].
 * [BiometricResult.Invalidated] means the enrollment changed and artifacts were auto-reset.
 */
interface BiometricUnlock {
    suspend fun status(): BiometricStatus

    suspend fun enable(): BiometricResult

    suspend fun disable()

    suspend fun unlock(): BiometricResult
}
