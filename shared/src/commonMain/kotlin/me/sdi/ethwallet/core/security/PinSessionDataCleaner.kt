package me.sdi.ethwallet.core.security

class PinSessionDataCleaner(private val pinSession: PinSession) : DataCleaner {
    override suspend fun clean() = pinSession.clear()
}
