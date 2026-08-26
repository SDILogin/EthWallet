package me.sdi.ethwallet.flow.e2e.pageobjects

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

class OnboardingPage(private val rule: ComposeTestRule) {
    fun assertShown() {
        rule.awaitNode(hasText("ETH Wallet"))
        rule.awaitNode(hasText("Create PIN Code") and hasClickAction())
    }

    fun clickCreatePinCodeButton() {
        rule.clickNode(hasText("Create PIN Code") and hasClickAction())
    }
}
