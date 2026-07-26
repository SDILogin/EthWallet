package me.sdi.ethwallet.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.PinSession
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TAG = "UserActivity"

/**
 * Decides when an idle app must drop the master key held by [PinSession].
 *
 * It only clears the key; whether the lock screen is shown is derived from [PinSession.isUnlocked]
 * by the UI, so a process restart also counts as locked.
 */
interface UserActivityManager {
    /**
     * Should be called when a user performs any action in the app; it postpones the inactivity
     * timeout. It must be enough to call this method only from `Activity#onUserInteraction()`.
     */
    fun onAnyUserAction()

    /**
     * Call this function when the app has been unlocked (e.g., after the user enters their PIN).
     */
    fun onAppUnlocked()

    /**
     * Call this function when the app leaves the foreground, so the time spent in the background
     * counts towards the inactivity timeout even when the process is frozen and timers stall.
     */
    fun onAppBackgrounded()

    /**
     * Call this function when the app returns to the foreground.
     */
    fun onAppForegrounded()
}

class ProcessLifetimeUserActivityManager(
    private val pinSession: PinSession,
    private val applicationScope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val inactivityTimeout: Duration = 10.minutes,
) : UserActivityManager {
    private var timerJob: Job? = null
    private var backgroundedAt: TimeMark? = null

    override fun onAnyUserAction() {
        if (!pinSession.isUnlocked.value) {
            return
        }

        restartTimer()
    }

    override fun onAppUnlocked() {
        restartTimer()
    }

    override fun onAppBackgrounded() {
        backgroundedAt = timeSource.markNow()
    }

    override fun onAppForegrounded() {
        val backgroundedAt = backgroundedAt ?: return
        this.backgroundedAt = null

        if (backgroundedAt.elapsedNow() >= inactivityTimeout) {
            Logger.debug(TAG) { "Spent longer than the inactivity timeout in the background, clearing the PIN session" }

            timerJob?.cancel()
            timerJob = null
            pinSession.clear()
        } else if (pinSession.isUnlocked.value) {
            restartTimer()
        }
    }

    private fun restartTimer() {
        timerJob?.cancel()
        timerJob = applicationScope.launch {
            delay(inactivityTimeout)

            Logger.debug(TAG) { "Inactivity timeout reached, clearing the PIN session" }

            pinSession.clear()
        }
    }
}
