package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import me.sdi.ethwallet.flow.clickText

class OnboardingPage(private val composeTestRule: ComposeTestRule) {
    fun clickCreatePinCode() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Create PIN Code").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.clickText("Create PIN Code")
    }
}
