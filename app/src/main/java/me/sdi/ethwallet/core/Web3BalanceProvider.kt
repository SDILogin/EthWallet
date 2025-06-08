package me.sdi.ethwallet.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigInteger

class Web3BalanceProvider(private val web3j: Web3j) : BalanceProvider {
    override suspend fun getEthBalance(address: String): BigInteger = withContext(Dispatchers.IO) {
        val ethGetBalance = web3j
            .ethGetBalance(address, DefaultBlockParameterName.LATEST)
            .sendAsync()
            .get()
        ethGetBalance.balance
    }
}
