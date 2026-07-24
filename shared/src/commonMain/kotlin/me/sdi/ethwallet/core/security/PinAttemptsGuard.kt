package me.sdi.ethwallet.core.security

import kotlin.time.Duration

/**
 * Tracks PIN verification failures and derives the resulting lock state.
 * Implementations persist failure counts so the lock survives process death.
 */
interface PinAttemptsGuard {
    /**
     * Establishes fresh guard state for a newly set PIN. Idempotent and never reports tamper.
     */
    suspend fun bootstrap()

    /**
     * Validates the persisted state and returns the current lock state.
     */
    suspend fun status(): PinLockState

    suspend fun onFailedAttempt(): PinLockState

    /**
     * Resets the failure count to zero after a successful verification.
     */
    suspend fun onSuccess()

    /**
     * Removes all guard state, e.g. when the PIN itself is removed.
     */
    suspend fun clear()
}

sealed interface PinLockState {
    data object Tampered : PinLockState

    /**
     * @param lockRemaining Time left before unlock; [Duration.ZERO] means unlocked.
     */
    data class Active(val totalFailures: Int, val remainingInWindow: Int, val lockRemaining: Duration) : PinLockState
}
