package me.sdi.ethwallet.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import me.sdi.ethwallet.data.UserActivityManager

/**
 * Reports foreground and background transitions to [UserActivityManager].
 *
 * Registered as an observer on [androidx.lifecycle.ProcessLifecycleOwner], which debounces
 * configuration changes internally, so a rotation never reads as a background trip. Wall-clock
 * measurement across those transitions is what locks a wallet the system froze in the background,
 * where in-process timers stop firing.
 */
class AppForegroundTracker(
    private val userActivityManager: UserActivityManager,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        userActivityManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        userActivityManager.onAppBackgrounded()
    }
}
