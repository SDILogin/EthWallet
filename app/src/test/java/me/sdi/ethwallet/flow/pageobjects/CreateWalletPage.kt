package me.sdi.ethwallet.flow.pageobjects

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import me.sdi.ethwallet.flow.assertTextExists
import me.sdi.ethwallet.flow.clickText

class CreateWalletPage(private val composeTestRule: ComposeTestRule) {
    fun clickNext() {
        try {
            composeTestRule.waitUntil(10000) {
                try {
                    composeTestRule.assertTextExists("I've safely stored my recovery phrase")
                    true
                } catch (e: Throwable) {
                    false
                }
            }
        } catch (e: Throwable) {
            System.err.println("--- TIMEOUT WAITING FOR NEXT BUTTON ---")
            try {
                System.err.println(composeTestRule.onRoot().printToString())
            } catch (printEx: Throwable) {
                System.err.println("Failed to print root: ${printEx.message}")
            }
            throw e
        }
        composeTestRule.clickText("I've safely stored my recovery phrase")
    }

    fun clickConfirm() {
        composeTestRule.clickText("Yes, I've stored it securely")
    }
}
