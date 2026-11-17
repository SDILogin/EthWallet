package me.sdi.ethwallet.ui.send

import me.sdi.ethwallet.core.history.Transfer

sealed interface SendNews {
    data class TransferSuccess(val transfer: Transfer) : SendNews

    data class TransferError(val message: String) : SendNews

    data object NavigateBack : SendNews
}
