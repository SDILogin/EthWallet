package me.sdi.ethwallet.core.security

private const val OWASP_MIN_LENGTH = 15
private const val MIN_FAIR_SCORE = 3
private const val STRONG_SCORE = 4

enum class PasswordStrength { Weak, Fair, Strong }

/** Scores a password from 0 (guessable) to 4 (very hard to guess). */
interface PasswordScorer {
    suspend fun score(password: String): Int
}

/**
 * Weak below the OWASP minimum of 15 characters for a password without MFA, or when the scorer rates it below 3
 * (fewer than about 10^8 guesses, which covers common passwords).
 */
fun passwordStrength(length: Int, score: Int): PasswordStrength =
    when {
        length < OWASP_MIN_LENGTH || score < MIN_FAIR_SCORE -> PasswordStrength.Weak
        score >= STRONG_SCORE -> PasswordStrength.Strong
        else -> PasswordStrength.Fair
    }
