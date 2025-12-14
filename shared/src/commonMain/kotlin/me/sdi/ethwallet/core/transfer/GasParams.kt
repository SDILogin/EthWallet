package me.sdi.ethwallet.core.transfer

import java.math.BigInteger

sealed interface GasParams {
    data object Default : GasParams

    data class Custom(
        val gasPrice: BigInteger,
        val gasLimit: BigInteger,
    ) : GasParams
}
