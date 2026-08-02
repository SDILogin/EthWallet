package me.sdi.ethwallet.util

import me.sdi.ethwallet.core.transfer.Usd
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val usdFormatter = ThreadLocal.withInitial {
    NumberFormat.getCurrencyInstance(Locale.US).apply {
        currency = Currency.getInstance("USD")
        maximumFractionDigits = 2
    }
}

actual fun formatUsdCurrency(amount: BigDecimal): String = usdFormatter.get()?.format(amount) ?: amount.toPlainString()

actual fun formatUsdCurrency(usd: Usd): String = formatUsdCurrency(usd.value)
