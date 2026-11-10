package me.sdi.ethwallet.core.security

import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SCORED_LENGTH = 100

/** [PasswordScorer] backed by zxcvbn, which is super-linear in length, so only the first 100 characters are scored. */
class PasswordScorerZxcvbn : PasswordScorer {
    private val zxcvbn by lazy { Zxcvbn() }

    override suspend fun score(password: String): Int = withContext(Dispatchers.Default) {
        zxcvbn.measure(password.take(MAX_SCORED_LENGTH)).score
    }
}
