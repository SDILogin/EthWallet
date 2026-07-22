package me.sdi.ethwallet.core.transfer

sealed class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InsufficientBalance(message: String) : TransferException(message)

    class InvalidAddress(message: String) : TransferException(message)

    class InvalidAmount(message: String) : TransferException(message)

    class NetworkError(message: String, cause: Throwable? = null) : TransferException(message, cause)

    class TransactionFailed(message: String) : TransferException(message)

    class GasPriceError(message: String) : TransferException(message)
}
