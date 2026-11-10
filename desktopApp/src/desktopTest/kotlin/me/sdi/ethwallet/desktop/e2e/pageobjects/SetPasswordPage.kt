package me.sdi.ethwallet.desktop.e2e.pageobjects

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTextInput

class SetPasswordPage(private val rule: ComposeTestRule) {
    fun assertShown() {
        rule.awaitNode(hasText("Create a wallet password"))
    }

    fun inputPassword(password: String) {
        rule.awaitNode(hasSetTextAction() and hasText("Password")).performTextInput(password)
    }

    fun confirmPassword(password: String) {
        rule.awaitNode(hasSetTextAction() and hasText("Confirm password")).performTextInput(password)
    }

    fun clickContinue() {
        rule.clickNode(hasText("Continue") and hasClickAction() and isEnabled())
    }
}
