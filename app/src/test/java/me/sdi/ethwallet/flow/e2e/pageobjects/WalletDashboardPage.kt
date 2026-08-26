package me.sdi.ethwallet.flow.e2e.pageobjects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import java.math.BigDecimal

class WalletDashboardPage(private val rule: ComposeTestRule) {
    fun assertShown(walletLabel: String) {
        rule.awaitNode(hasText(walletLabel))
    }

    fun assertTestnetShown() {
        rule.awaitNode(hasText("Network: Testnet"))
    }

    fun readEthBalance(): BigDecimal {
        val deadlineNanos = System.nanoTime() + INITIAL_BALANCE_TIMEOUT_MILLIS * NANOS_PER_MILLISECOND
        var retryClicks = 0
        while (System.nanoTime() < deadlineNanos) {
            findEthBalance()?.let { return it }
            val retry = rule.onAllNodes(hasText("Retry") and hasClickAction())
                .fetchSemanticsNodes()
                .firstOrNull()
            if (retry != null && retryClicks < MAX_INITIAL_RETRY_CLICKS) {
                rule.onNode(hasText("Retry") and hasClickAction()).performClick()
                retryClicks += 1
            } else {
                try {
                    rule.waitUntil(RETRY_POLL_TIMEOUT_MILLIS) {
                        findEthBalance() != null ||
                            rule.onAllNodes(hasText("Retry") and hasClickAction())
                                .fetchSemanticsNodes()
                                .isNotEmpty()
                    }
                } catch (_: ComposeTimeoutException) {
                    continue
                }
            }
        }
        throw AssertionError("ETH balance did not load within the allotted time")
    }

    fun clickSendButton() {
        rule.clickNode(hasContentDescription("Send"))
    }

    fun clickBackButton() {
        rule.clickNode(hasContentDescription("Back"))
    }

    fun awaitEthBalanceAtLeast(
        expected: BigDecimal,
        timeoutMillis: Long = BALANCE_TIMEOUT_MILLIS,
    ) {
        val deadlineNanos = System.nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
        var lastBalance: BigDecimal? = null
        var refreshes = 0

        while (System.nanoTime() < deadlineNanos) {
            val currentBalance = findEthBalance()
            if (currentBalance != null) {
                lastBalance = currentBalance
                if (currentBalance >= expected) return
            }

            if (refreshes >= MAX_BALANCE_REFRESHES) {
                try {
                    rule.waitUntil(REFRESH_SETTLE_TIMEOUT_MILLIS) {
                        findEthBalance()?.let { it >= expected } == true
                    }
                } catch (_: ComposeTimeoutException) {
                    continue
                }
                continue
            }

            rule.onNode(hasContentDescription(ETH_BALANCE_PREFIX, substring = true)).performTouchInput {
                swipe(
                    start = center,
                    end = Offset(center.x, center.y + REFRESH_SWIPE_DISTANCE_PX),
                    durationMillis = REFRESH_SWIPE_DURATION_MILLIS,
                )
            }
            refreshes += 1
            try {
                rule.waitUntil(REFRESH_SETTLE_TIMEOUT_MILLIS) {
                    val refreshedBalance = findEthBalance()
                    refreshedBalance != null && refreshedBalance != currentBalance
                }
            } catch (_: ComposeTimeoutException) {
            }
        }

        throw AssertionError("Expected receiver balance >= $expected ETH, last displayed balance was $lastBalance ETH")
    }

    private fun findEthBalance(): BigDecimal? =
        rule.onAllNodes(hasContentDescription(ETH_BALANCE_PREFIX, substring = true))
            .fetchSemanticsNodes()
            .asSequence()
            .flatMap { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.asSequence()
            }
            .mapNotNull { description ->
                ETH_BALANCE_PATTERN.matchEntire(description)?.groupValues?.get(1)?.toBigDecimalOrNull()
            }
            .singleOrNull()

    private companion object {
        const val ETH_BALANCE_PREFIX = "ETH balance:"
        val ETH_BALANCE_PATTERN = Regex("^ETH balance: ([0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?) ETH$")
        const val BALANCE_TIMEOUT_MILLIS = 300_000L
        const val INITIAL_BALANCE_TIMEOUT_MILLIS = 90_000L
        const val REFRESH_SETTLE_TIMEOUT_MILLIS = 5_000L
        const val REFRESH_SWIPE_DISTANCE_PX = 240f
        const val REFRESH_SWIPE_DURATION_MILLIS = 500L
        const val RETRY_POLL_TIMEOUT_MILLIS = 1_000L
        const val MAX_INITIAL_RETRY_CLICKS = 3
        const val MAX_BALANCE_REFRESHES = 20
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
