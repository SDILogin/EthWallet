package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PasswordScorer

class FakePasswordScorer : PasswordScorer {
    var score = 4
    var failure: Exception? = null

    override suspend fun score(password: String): Int {
        failure?.let { throw it }
        return score
    }
}
