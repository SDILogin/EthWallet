package me.sdi.ethwallet.core.security

enum class PasswordStrength { Weak, Fair, Strong }

fun interface PasswordStrengthChecker {
    suspend fun check(password: String): PasswordStrength
}
