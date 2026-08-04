package me.sdi.ethwallet.ui.wallet

const val MAX_WALLET_LABEL_LENGTH = 40

fun normalizeWalletLabel(label: String): String =
    label.trim().replace(Regex("\\s+"), " ")

fun isWalletLabelValid(label: String): Boolean =
    label.isNotEmpty() && label.length <= MAX_WALLET_LABEL_LENGTH
