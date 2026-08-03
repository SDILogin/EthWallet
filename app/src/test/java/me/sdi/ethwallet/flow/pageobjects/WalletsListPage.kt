package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import me.sdi.ethwallet.flow.clickContentDescription
import me.sdi.ethwallet.flow.clickText

class WalletsListPage(private val composeTestRule: ComposeTestRule) {
    fun assertAddWalletIsHidden() {
        composeTestRule.onAllNodesWithContentDescription("Add wallet").assertCountEquals(0)
    }

    fun openAddWalletMenu() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Add wallet").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Add wallet", useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.clickContentDescription("Add wallet")
    }

    fun assertAddWalletActions() {
        composeTestRule.onAllNodesWithText("Create new wallet").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Import existing wallet").assertCountEquals(1)
    }

    fun clickAddWalletCreate() {
        composeTestRule.clickText("Create new wallet")
    }

    fun clickAddWalletImport() {
        composeTestRule.clickText("Import existing wallet")
    }

    fun clickCreateWallet() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Create new wallet").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.clickText("Create new wallet")
    }

    fun assertSingleWallet() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithContentDescription("Delete wallet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithContentDescription("Delete wallet", useUnmergedTree = true).assertCountEquals(1)
    }

    fun assertSelectedIndicatorIsHidden() {
        composeTestRule.onAllNodesWithContentDescription("Selected wallet").assertCountEquals(0)
    }

    fun clickWallet(label: String) {
        composeTestRule.clickText(label)
    }
}
