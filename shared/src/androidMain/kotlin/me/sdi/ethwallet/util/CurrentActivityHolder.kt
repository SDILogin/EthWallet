package me.sdi.ethwallet.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

/**
 * Tracks the started [FragmentActivity] so non-UI code can host a dialog-style system prompt.
 * Registered as [Application.ActivityLifecycleCallbacks]; the reference is weak and dropped on stop.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var activityRef: WeakReference<FragmentActivity>? = null

    val current: FragmentActivity?
        get() = activityRef?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (activity is FragmentActivity) {
            activityRef = WeakReference(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        if (current === activity) {
            activityRef = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
