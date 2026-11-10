package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PasswordScorer

class FakePasswordScorer : PasswordScorer {
    var score = 4

    override suspend fun score(password: String): Int = score
}
