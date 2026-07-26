package me.sdi.ethwallet.core.security.biometry

sealed interface BiometricStatus {
    data object NotSupported : BiometricStatus

    data object NotEnrolled : BiometricStatus

    data object NotSetUp : BiometricStatus

    data object Invalidated : BiometricStatus

    data object Ready : BiometricStatus
}
