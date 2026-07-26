package me.sdi.ethwallet.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.sdi.ethwallet.data.UserActivityManager

class FakeUserActivityManager : UserActivityManager {
    var onAppUnlockedCallCount = 0

    override val showLockScreen: StateFlow<Boolean> = MutableStateFlow(false)

    override fun onAnyUserAction() = Unit

    override fun onAppUnlocked() {
        onAppUnlockedCallCount++
    }
}
