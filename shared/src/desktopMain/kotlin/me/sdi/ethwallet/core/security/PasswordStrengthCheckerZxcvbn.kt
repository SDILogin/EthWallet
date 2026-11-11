package me.sdi.ethwallet.core.security

import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SCORED_LENGTH = 100
private const val OWASP_MIN_LENGTH = 15
private const val MIN_FAIR_SCORE = 3
private const val STRONG_SCORE = 4

/**
 * [PasswordStrengthChecker] backed by zxcvbn scores from 0 (guessable) to 4 (very hard to guess). Weak below the OWASP
 * minimum of 15 characters for a password without MFA, or below score 3 (fewer than about 10^8 guesses, which covers
 * common passwords). zxcvbn is super-linear in length, so only the first 100 characters are scored.
 */
class PasswordStrengthCheckerZxcvbn : PasswordStrengthChecker {
    private val zxcvbn by lazy { Zxcvbn() }

    override suspend fun check(password: String): PasswordStrength = withContext(Dispatchers.Default) {
        val score = zxcvbn.measure(password.take(MAX_SCORED_LENGTH)).score
        when {
            password.length < OWASP_MIN_LENGTH || score < MIN_FAIR_SCORE -> PasswordStrength.Weak
            score >= STRONG_SCORE -> PasswordStrength.Strong
            else -> PasswordStrength.Fair
        }
    }
}
