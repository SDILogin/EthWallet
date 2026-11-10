package me.sdi.ethwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordStrengthTest {
    @Test
    fun a_password_shorter_than_15_characters_is_weak_whatever_its_score() {
        assertEquals(PasswordStrength.Weak, passwordStrength(length = 14, score = 4), "OWASP: under 15 characters is weak")
    }

    @Test
    fun a_low_score_is_weak_whatever_the_length() {
        assertEquals(PasswordStrength.Weak, passwordStrength(length = 40, score = 2), "A guessable password is weak")
    }

    @Test
    fun a_long_password_with_score_3_is_fair() {
        assertEquals(PasswordStrength.Fair, passwordStrength(length = 15, score = 3), "Score 3 is fair")
    }

    @Test
    fun a_long_password_with_score_4_is_strong() {
        assertEquals(PasswordStrength.Strong, passwordStrength(length = 20, score = 4), "Score 4 is strong")
    }
}
