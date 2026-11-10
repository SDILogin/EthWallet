package me.sdi.ethwallet.desktop.e2e.pageobjects

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

class OnboardingPage(private val rule: ComposeTestRule) {
    fun assertShown() {
        rule.awaitNode(hasText("ETH Wallet"))
        rule.awaitNode(hasText("Create password") and hasClickAction())
    }

    fun clickCreatePasswordButton() {
        rule.clickNode(hasText("Create password") and hasClickAction())
    }
}
