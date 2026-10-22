package me.sdi.ethwallet.desktop.e2e.pageobjects

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString

class SendPage(private val rule: ComposeTestRule) {
    fun assertShown() {
        rule.awaitNode(hasText("Send"))
        rule.awaitNode(hasText("Receiver Address"))
    }

    fun assertEthSelected() {
        rule.awaitNode(hasText("ETH") and isSelectable()).assertIsSelected()
    }

    fun clickSelectWalletButton() {
        rule.clickNode(hasContentDescription("Select wallet") and hasClickAction())
    }

    fun selectWallet(label: String) {
        rule.awaitNode(hasText("My wallets"))
        rule.clickNode(hasText(label) and hasClickAction())
    }

    fun inputEthAmount(amount: String) {
        rule.awaitNode(hasText("Enter amount") and hasSetTextAction()).performTextReplacement(amount)
    }

    fun assertDefaultFeesSelectedAndReady() {
        val defaultChip = rule.awaitNode(hasText("Default") and isSelectable())
        rule.waitUntil(FEE_TIMEOUT_MILLIS) {
            runCatching {
                defaultChip.assertIsSelected().assertIsEnabled()
            }.isSuccess
        }
    }

    fun clickSendButton() {
        val sendButton = rule.awaitNode(hasText("Send Transaction") and hasClickAction())
        scrollSendButtonIntoView(sendButton)
        rule.waitUntil(SEND_TIMEOUT_MILLIS) {
            runCatching { sendButton.assertIsEnabled() }.isSuccess
        }
        sendButton.assertIsDisplayed()
        sendButton.performTouchInput { click() }
        awaitSubmissionStartedOrFormCleared()
    }

    fun awaitTransferFormCleared(timeoutMillis: Long = TRANSFER_TIMEOUT_MILLIS) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes()
                .all { node ->
                    node.config.getOrElse(SemanticsProperties.EditableText) { AnnotatedString("") }
                        .text
                        .isEmpty()
                }
        }
    }

    fun clickBackButton() {
        rule.clickNode(hasContentDescription("Back"))
    }

    private fun scrollSendButtonIntoView(sendButton: SemanticsNodeInteraction) {
        repeat(MAX_SCROLL_GESTURES) {
            if (runCatching { sendButton.assertIsDisplayed() }.isSuccess) return
            rule.onNode(
                hasScrollAction() and hasAnyDescendant(hasText("Send Transaction")),
            ).performTouchInput { swipeUp() }
        }
        sendButton.assertIsDisplayed()
    }

    private fun awaitSubmissionStartedOrFormCleared() {
        rule.waitUntil(SUBMISSION_START_TIMEOUT_MILLIS) {
            rule.onAllNodes(hasText("Sending…")).fetchSemanticsNodes().isNotEmpty() || formIsCleared()
        }
    }

    private fun formIsCleared(): Boolean = rule.onAllNodes(hasSetTextAction())
        .fetchSemanticsNodes()
        .all { node ->
            node.config.getOrElse(SemanticsProperties.EditableText) { AnnotatedString("") }
                .text
                .isEmpty()
        }

    private companion object {
        const val FEE_TIMEOUT_MILLIS = 60_000L
        const val SEND_TIMEOUT_MILLIS = 30_000L
        const val SUBMISSION_START_TIMEOUT_MILLIS = 10_000L
        const val MAX_SCROLL_GESTURES = 6
        const val TRANSFER_TIMEOUT_MILLIS = 120_000L
    }
}
