package me.sdi.ethwallet.core.security

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Pure policy for escalating PIN lock durations. Every [ATTEMPTS_PER_WINDOW] failures trigger
 * a lock whose duration climbs one tier up [LOCK_DURATIONS], capping at the last entry.
 */
object PinLockPolicy {
    const val ATTEMPTS_PER_WINDOW = 3

    private val LOCK_DURATIONS = listOf(1.seconds, 30.seconds, 1.minutes, 5.minutes, 15.minutes, 1.hours)

    fun isLockTriggered(totalFailures: Int): Boolean = totalFailures > 0 && totalFailures % ATTEMPTS_PER_WINDOW == 0

    fun lockDuration(totalFailures: Int): Duration {
        val tier = totalFailures / ATTEMPTS_PER_WINDOW - 1
        return LOCK_DURATIONS[tier.coerceIn(0, LOCK_DURATIONS.lastIndex)]
    }

    fun remainingInWindow(totalFailures: Int): Int = ATTEMPTS_PER_WINDOW - (totalFailures % ATTEMPTS_PER_WINDOW)
}
