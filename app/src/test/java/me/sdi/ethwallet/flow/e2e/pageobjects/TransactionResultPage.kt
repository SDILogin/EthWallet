package me.sdi.ethwallet.flow.e2e.pageobjects

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

class TransactionResultPage(private val rule: ComposeTestRule) {
    fun assertShown() {
        rule.awaitNode(hasText("Transaction details"))
        rule.awaitNode(hasText("Sent"))
    }

    fun awaitConfirmed(timeoutMillis: Long) {
        rule.awaitNode(hasText("Confirmed"), timeoutMillis)
    }

    fun clickBackButton() {
        rule.clickNode(hasContentDescription("Back"))
    }
}
