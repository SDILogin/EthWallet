package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import me.sdi.ethwallet.flow.assertContentDescriptionExists
import me.sdi.ethwallet.flow.clickContentDescription

class WalletsListPage(private val composeTestRule: ComposeTestRule) {
    fun assertSingleWallet() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Delete wallet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithContentDescription("Delete wallet", useUnmergedTree = true).assertCountEquals(1)
    }

    fun assertActiveCheckmarkExists() {
        composeTestRule.assertContentDescriptionExists("Active wallet")
    }

    fun deleteActiveWallet() {
        composeTestRule.clickContentDescription("Delete wallet")
    }
}
