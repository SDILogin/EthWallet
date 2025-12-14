package me.sdi.ethwallet.core

import me.sdi.ethwallet.data.repository.EthWallet
import java.math.BigInteger

interface EthManager {
    suspend fun getBalance(wallet: EthWallet): Map<EthAddress, Result<BigInteger>>
}
