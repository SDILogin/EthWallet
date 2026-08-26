package me.sdi.ethwallet.flow.e2e.pageobjects

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText

class SetPinCodePage(private val rule: ComposeTestRule) {
    fun assertCreatePinShown() {
        rule.awaitNode(hasText("Create PIN Code"))
    }

    fun inputPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit)) { "PIN must contain exactly $PIN_LENGTH digits" }
        pin.forEach { digit ->
            rule.clickNode(hasText(digit.toString()) and hasClickAction())
        }
    }

    fun assertConfirmPinShown() {
        rule.awaitNode(hasText("Confirm PIN Code"))
    }

    fun confirmPin(pin: String) {
        inputPin(pin)
    }

    fun dismissBiometricOfferIfShown() {
        rule.waitUntil(DEFAULT_UI_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("My Wallets").fetchSemanticsNodes().isNotEmpty()
        }

        if (rule.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) {
            rule.clickNode(hasText("Not now") and hasClickAction())
        }
        rule.awaitNode(hasText("My Wallets"))
    }

    private companion object {
        const val PIN_LENGTH = 6
    }
}
