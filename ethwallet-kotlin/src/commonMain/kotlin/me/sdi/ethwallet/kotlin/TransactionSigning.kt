package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HEX_DATA = Regex("^0x(?:[0-9a-fA-F]{2})*$")
private const val UINT256_BYTES = 32

data class Eip1559TransactionPayload(
    val chainId: Long,
    val nonce: UInt256,
    val maxPriorityFeePerGas: UInt256,
    val maxFeePerGas: UInt256,
    val gasLimit: UInt256,
    val destination: EthAddress,
    val value: UInt256,
    val data: String = "0x",
) {
    init {
        require(chainId > 0) { "EIP-1559 chain ID must be positive" }
        require(data.matches(HEX_DATA)) { "Transaction data must be 0x-prefixed hexadecimal bytes" }
    }
}

suspend fun PrivateKey.signTransaction(payload: Eip1559TransactionPayload): ByteArray =
    withContext(Dispatchers.Default) {
        requireUsable()
        val privateKey = copyBytes()
        try {
            NativeCrypto.signEip1559Transaction(
                privateKey = privateKey,
                chainId = payload.chainId,
                nonce = payload.nonce.toNativeQuantity(),
                maxPriorityFeePerGas = payload.maxPriorityFeePerGas.toNativeQuantity(),
                maxFeePerGas = payload.maxFeePerGas.toNativeQuantity(),
                gasLimit = payload.gasLimit.toNativeQuantity(),
                destination = payload.destination.value.hexToByteArray(),
                value = payload.value.toNativeQuantity(),
                data = payload.data.hexToByteArray(),
            )
        } finally {
            privateKey.fill(0)
        }
    }

private fun UInt256.toNativeQuantity(): ByteArray {
    val digits = hex.substring(2).padStart(UINT256_BYTES * 2, '0')
    return "0x$digits".hexToByteArray()
}

private fun String.hexToByteArray(): ByteArray {
    val digits = substring(2)
    return ByteArray(digits.length / 2) { index ->
        digits.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
