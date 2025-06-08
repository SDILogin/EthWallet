package me.sdi.ethwallet.core

import java.math.BigInteger

interface BalanceProvider {
    suspend fun getEthBalance(address: String): BigInteger
}
