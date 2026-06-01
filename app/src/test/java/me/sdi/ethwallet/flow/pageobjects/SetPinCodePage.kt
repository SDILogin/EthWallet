package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import me.sdi.ethwallet.flow.clickText

class SetPinCodePage(private val composeTestRule: ComposeTestRule) {
    fun enterPin(pin: String) {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        }
        for (char in pin) {
            composeTestRule.clickText(char.toString())
        }
        composeTestRule.waitForIdle()
    }
}
