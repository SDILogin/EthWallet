package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock

class FakeBiometricUnlock : BiometricUnlock {
    var currentStatus: BiometricStatus = BiometricStatus.NotSupported
    var enableResult: BiometricResult = BiometricResult.Success
    var unlockResult: BiometricResult = BiometricResult.Success
    var unlockThrowable: Throwable? = null
    var enableCallCount = 0
    var disableCallCount = 0
    var unlockCallCount = 0

    override suspend fun status(): BiometricStatus = currentStatus

    override suspend fun enable(): BiometricResult {
        enableCallCount++
        if (enableResult == BiometricResult.Success) {
            currentStatus = BiometricStatus.Ready
        }
        return enableResult
    }

    override suspend fun disable() {
        disableCallCount++
        currentStatus = BiometricStatus.NotSetUp
    }

    override suspend fun unlock(): BiometricResult {
        unlockCallCount++
        unlockThrowable?.let { throw it }
        return unlockResult
    }
}
