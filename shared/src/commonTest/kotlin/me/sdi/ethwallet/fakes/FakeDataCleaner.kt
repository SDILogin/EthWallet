package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.DataCleaner

class FakeDataCleaner : DataCleaner {
    var shouldThrowException = false
    var cleanCalledCount = 0

    override suspend fun clean() {
        if (shouldThrowException) throw IllegalStateException("Simulated data cleanup failure")
        cleanCalledCount++
    }
}
