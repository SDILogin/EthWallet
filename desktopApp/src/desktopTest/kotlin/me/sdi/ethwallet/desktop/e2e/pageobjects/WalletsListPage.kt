package me.sdi.ethwallet.desktop.e2e.pageobjects

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

class WalletsListPage(private val rule: ComposeTestRule) {
    fun assertWalletsListShown() {
        rule.awaitNode(hasText("My Wallets"))
    }

    fun clickImportWalletButton() {
        rule.clickNode(hasText("Import existing wallet") and hasClickAction())
    }

    fun clickAddWalletButton() {
        rule.clickNode(hasContentDescription("Add wallet") and hasClickAction())
    }

    fun clickImportWalletMenuItem() {
        rule.clickNode(hasText("Import existing wallet") and hasClickAction())
    }

    fun assertWalletShown(label: String) {
        rule.awaitNode(hasText(label))
    }

    fun openWallet(label: String) {
        rule.clickNode(hasText(label) and hasClickAction())
    }
}
