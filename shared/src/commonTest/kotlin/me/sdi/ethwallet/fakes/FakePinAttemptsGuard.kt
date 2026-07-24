package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinLockPolicy
import me.sdi.ethwallet.core.security.PinLockState
import kotlin.time.Duration

class FakePinAttemptsGuard : PinAttemptsGuard {
    var statusResult: PinLockState = PinLockState.Active(0, PinLockPolicy.ATTEMPTS_PER_WINDOW, Duration.ZERO)
    var onFailedAttemptResult: PinLockState =
        PinLockState.Active(1, PinLockPolicy.ATTEMPTS_PER_WINDOW - 1, Duration.ZERO)
    val onFailedAttemptResults = mutableListOf<PinLockState>()

    var statusCallCount = 0
    var onFailedAttemptCallCount = 0
    var onSuccessCallCount = 0

    override suspend fun bootstrap() = Unit

    override suspend fun status(): PinLockState {
        statusCallCount++
        return statusResult
    }

    override suspend fun onFailedAttempt(): PinLockState {
        onFailedAttemptCallCount++
        return if (onFailedAttemptResults.isNotEmpty()) onFailedAttemptResults.removeAt(0) else onFailedAttemptResult
    }

    override suspend fun onSuccess() {
        onSuccessCallCount++
    }

    override suspend fun clear() = Unit
}
