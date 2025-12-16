package me.sdi.ethwallet.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

actual fun formatUsdCurrency(amount: BigDecimal): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    formatter.currency = Currency.getInstance("USD")
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}
