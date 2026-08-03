package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

interface BalanceProvider {
    fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>>

    suspend fun forceUpdate(address: EthAddress)
}
