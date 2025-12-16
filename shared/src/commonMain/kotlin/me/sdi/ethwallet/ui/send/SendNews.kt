package me.sdi.ethwallet.ui.send

sealed interface SendNews {
    data class TransferSuccess(val transactionHash: String) : SendNews

    data class TransferError(val message: String) : SendNews

    data class ValidationError(val message: String) : SendNews

    data object NavigateBack : SendNews
}
