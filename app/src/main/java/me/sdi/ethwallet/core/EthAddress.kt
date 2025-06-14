package me.sdi.ethwallet.core

@JvmInline
value class EthAddress(private val validEthAddress: String) {
    init {
        require(validEthAddress.isNotEmpty()) { "Address string cannot be empty" }
        require(validEthAddress.startsWith("0x")) { "Ethereum address must start with 0x" }
        require(validEthAddress.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
            "Invalid Ethereum address format"
        }
    }

    override fun toString(): String {
        return validEthAddress
    }
}

// fake address to work with ETH the way we work with ERC-20 tokens
val EthContractAddress = EthAddress("0x0000000000000000000000000000000000000000")
