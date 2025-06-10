package me.sdi.ethwallet.core

import java.math.BigInteger

interface BalanceProvider {
    suspend fun getEthBalance(address: EthAddress): BigInteger

    suspend fun getErc20TokensBalance(
        walletAddress: EthAddress,
        tokenContractAddresses: List<EthAddress>,
    ): Map<EthAddress, BigInteger> // Returns balances in wei (smallest token units)
}
