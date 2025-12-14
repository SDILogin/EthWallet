package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.Flow
import me.sdi.ethwallet.data.repository.EthWallet
import java.math.BigInteger

interface BalanceProvider {
    fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>>

    suspend fun forceUpdate(wallet: EthWallet)
}
