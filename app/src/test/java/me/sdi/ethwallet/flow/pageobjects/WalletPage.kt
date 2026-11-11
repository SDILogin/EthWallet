package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import me.sdi.ethwallet.flow.clickContentDescription
import me.sdi.ethwallet.flow.clickText

class WalletPage(private val composeTestRule: ComposeTestRule) {
    fun awaitVisible() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Receive").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun clickUp() {
        composeTestRule.clickContentDescription("Back")
    }

    fun clickToken(name: String) {
        composeTestRule.clickText(name)
    }

    fun clickSend() {
        composeTestRule.clickContentDescription("Send")
    }
}
