package me.sdi.ethwallet.util

import android.icu.text.NumberFormat
import android.icu.util.Currency
import java.math.BigDecimal

actual fun formatUsdCurrency(amount: BigDecimal): String {
    val formatter = NumberFormat.getCurrencyInstance()
    formatter.currency = Currency.getInstance("USD")
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}
