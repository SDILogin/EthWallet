package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import me.sdi.ethwallet.flow.clickContentDescription

class WalletPage(private val composeTestRule: ComposeTestRule) {
    fun awaitVisible() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Manage Wallets").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun clickManageWallets() {
        awaitVisible()
        composeTestRule.clickContentDescription("Manage Wallets")
    }
}
