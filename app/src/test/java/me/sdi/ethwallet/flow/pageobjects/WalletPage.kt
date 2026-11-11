package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import me.sdi.ethwallet.flow.clickContentDescription
import me.sdi.ethwallet.flow.clickText
import me.sdi.ethwallet.flow.waitUntilTextExists

class WalletPage(private val composeTestRule: ComposeTestRule) {
    fun awaitVisible() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Receive").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun awaitHidden() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Receive").fetchSemanticsNodes().isEmpty()
        }
    }

    fun clickUp() {
        composeTestRule.clickContentDescription("Back")
    }

    fun clickToken(name: String) {
        composeTestRule.waitUntilTextExists(name)
        composeTestRule.clickText(name)
    }

    fun clickSend() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Send").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.clickContentDescription("Send")
    }

    fun clickReceive() {
        composeTestRule.clickContentDescription("Receive")
    }

    fun clickHistory() {
        composeTestRule.clickContentDescription("History")
    }

    fun clickEditTokens() {
        composeTestRule.clickContentDescription("Manage tokens")
    }
}
