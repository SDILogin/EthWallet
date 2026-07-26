package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.data.UserActivityManager

class FakeUserActivityManager : UserActivityManager {
    var onAppUnlockedCallCount = 0

    override fun onAnyUserAction() = Unit

    override fun onAppUnlocked() {
        onAppUnlockedCallCount++
    }

    override fun onAppBackgrounded() = Unit

    override fun onAppForegrounded() = Unit
}
