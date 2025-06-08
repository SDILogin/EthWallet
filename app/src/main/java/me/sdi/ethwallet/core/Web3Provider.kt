package me.sdi.ethwallet.core

import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

object Web3Provider {
    val web3j: Web3j by lazy {
        Web3j.build(HttpService(NetworkConfig.ETH_RPC_URL))
    }
}
