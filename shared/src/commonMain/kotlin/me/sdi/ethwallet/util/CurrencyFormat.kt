package me.sdi.ethwallet.util

import me.sdi.ethwallet.core.transfer.Usd
import java.math.BigDecimal

/**
 * Formats a [BigDecimal] amount as a USD currency string.
 * Uses platform-specific formatting to handle locale-aware currency display.
 *
 * @param amount The amount to format
 * @return Formatted currency string (e.g., "$1,234.56")
 */
expect fun formatUsdCurrency(amount: BigDecimal): String

expect fun formatUsdCurrency(usd: Usd): String
