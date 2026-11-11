package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.security.PasswordStrength
import me.sdi.ethwallet.core.security.PasswordStrengthChecker

class FakePasswordStrengthChecker : PasswordStrengthChecker {
    var strength = PasswordStrength.Strong
    var failure: Exception? = null

    override suspend fun check(password: String): PasswordStrength {
        failure?.let { throw it }
        return strength
    }
}
