package me.sdi.ethwallet.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import me.sdi.ethwallet.data.UserActivityManager

/**
 * Reports foreground and background transitions to [UserActivityManager] by counting started
 * activities: the app is in the background while no activity is started.
 *
 * Registered as [Application.ActivityLifecycleCallbacks]. Wall-clock measurement across those
 * transitions is what locks a wallet the system froze in the background, where in-process timers
 * stop firing.
 *
 * A rotation tears the activity down and builds it again, which reads as a background trip by the
 * count alone; [Activity.isChangingConfigurations] tells those two apart so rotating does not pass
 * for activity and postpone the lock.
 */
class AppForegroundTracker(
    private val userActivityManager: UserActivityManager,
) : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        if (startedActivities == 1) {
            userActivityManager.onAppForegrounded()
        }
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            userActivityManager.onAppBackgrounded()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
