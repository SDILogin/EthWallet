package me.sdi.ethwallet.core

import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

private const val ETH_RPC_URL = "https://eth.llamarpc.com"

object Web3Provider {
    val web3j: Web3j by lazy {
        Web3j.build(HttpService(ETH_RPC_URL))
    }
}
