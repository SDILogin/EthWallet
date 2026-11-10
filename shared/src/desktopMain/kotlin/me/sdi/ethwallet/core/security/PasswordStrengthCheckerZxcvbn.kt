package me.sdi.ethwallet.core.security

import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SCORED_LENGTH = 100

/** [PasswordStrengthChecker] backed by zxcvbn, which is super-linear in length, so only the first 100 characters are scored. */
class PasswordStrengthCheckerZxcvbn : PasswordStrengthChecker {
    private val zxcvbn by lazy { Zxcvbn() }

    override suspend fun check(password: String): PasswordStrength = withContext(Dispatchers.Default) {
        passwordStrength(length = password.length, score = zxcvbn.measure(password.take(MAX_SCORED_LENGTH)).score)
    }
}
