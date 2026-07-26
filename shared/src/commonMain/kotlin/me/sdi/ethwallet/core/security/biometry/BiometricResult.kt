package me.sdi.ethwallet.core.security.biometry

sealed interface BiometricResult {
    data object Success : BiometricResult

    data object Cancelled : BiometricResult

    data object Invalidated : BiometricResult

    data class Error(val message: String?) : BiometricResult
}
