package me.sdi.ethwallet.desktop

import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.security.PasswordStrength
import me.sdi.ethwallet.core.security.PasswordStrengthCheckerZxcvbn
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthCheckerZxcvbnTest {
    private val checker = PasswordStrengthCheckerZxcvbn()

    @Test
    fun a_random_password_of_14_characters_is_weak() = runBlocking {
        assertEquals("OWASP: under 15 characters is weak", PasswordStrength.Weak, checker.check("qZ7#vL9!pX2@ab"))
    }

    @Test
    fun a_common_password_is_weak() = runBlocking {
        assertEquals("A common password is weak", PasswordStrength.Weak, checker.check("password12345678"))
    }

    @Test
    fun a_long_moderately_guessable_password_is_fair() = runBlocking {
        assertEquals("zxcvbn score 3 is fair", PasswordStrength.Fair, checker.check("monkeyelephant99"))
    }

    @Test
    fun a_long_random_password_is_strong() = runBlocking {
        assertEquals("A long random password is strong", PasswordStrength.Strong, checker.check("vR8#qL2!zN6@wT4\$kP9"))
    }
}
