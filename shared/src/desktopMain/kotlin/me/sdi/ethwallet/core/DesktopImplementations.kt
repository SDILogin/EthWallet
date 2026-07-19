package me.sdi.ethwallet.core

import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.EthWallet
import org.web3j.protocol.Web3j
import java.math.BigInteger

class Web3jEthManager(private val web3j: Web3j) : EthManager {
    override suspend fun getBalance(wallet: EthWallet): Map<EthAddress, Result<BigInteger>> {
        return try {
            val balance = web3j.ethGetBalance(
                wallet.address.toString(),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST,
            ).send()
            mapOf(wallet.address to Result.success(balance.balance))
        } catch (e: Exception) {
            mapOf(wallet.address to Result.failure(e))
        }
    }
}

class KeyStoreEncryptor : PrivateKeyEncryptor {
    override fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
        return privateKey.toString(16)
    }

    override fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
        return BigInteger(encryptedBase64, 16)
    }
}
