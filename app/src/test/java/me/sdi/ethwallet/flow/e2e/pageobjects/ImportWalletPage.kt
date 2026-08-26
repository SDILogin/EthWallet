package me.sdi.ethwallet.flow.e2e.pageobjects

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

class ImportWalletPage(private val rule: ComposeTestRule) {
    private var containsSecret = false

    fun assertShown() {
        safely("Import wallet screen was not shown") {
            rule.awaitNode(hasText("Enter Recovery Phrase"))
        }
    }

    fun inputWalletLabel(label: String) {
        safely("Could not enter the wallet label") {
            rule.awaitNode(hasText("Wallet name") and hasSetTextAction()).performTextReplacement(label)
        }
    }

    fun inputSeedPhrase(seedPhrase: String) {
        containsSecret = true
        secretSafeAction("Could not enter the recovery phrase") {
            rule.awaitNode(hasText("Recovery Phrase") and hasSetTextAction())
                .performTextReplacement(seedPhrase)
        }
    }

    fun clickRestoreButton() {
        safely("The restore wallet button was not ready") {
            val button = rule.awaitNode(hasText("Import Wallet") and hasClickAction())
            rule.waitUntil(DEFAULT_UI_TIMEOUT_MILLIS) {
                runCatching { button.assertIsEnabled() }.isSuccess
            }
            button.performClick()
        }
    }

    private inline fun safely(message: String, action: () -> Unit) {
        if (containsSecret) {
            secretSafeAction(message, action)
        } else {
            action()
        }
    }
}
