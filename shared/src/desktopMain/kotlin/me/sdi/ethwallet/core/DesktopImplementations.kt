package me.sdi.ethwallet.core

import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.transfer.GasParams
import me.sdi.ethwallet.core.transfer.TransferData
import me.sdi.ethwallet.data.TempFileProvider
import me.sdi.ethwallet.data.repository.EthWallet
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.Web3j
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

class FileBasedEthWalletCreator(private val tempFileProvider: TempFileProvider) : EthWalletCreator {
    companion object {
        private const val PASSWORD_LENGTH = 16
        private const val WALLET_TEMP_DIR_PREFIX = "eth_wallet"
    }

    override fun createWallet(): EthCreationResult {
        return tempFileProvider.withTempDirectory(WALLET_TEMP_DIR_PREFIX) { tempDir ->
            val password = generateRandomPassword()
            val bip39Wallet = WalletUtils.generateBip39Wallet(password, tempDir)
            val seedPhrase = bip39Wallet.mnemonic.split(" ").map { it.trim() }
            val credentials = WalletUtils.loadBip39Credentials(password, bip39Wallet.mnemonic)
            EthCreationResult(
                address = EthAddress(credentials.address),
                privateKey = credentials.ecKeyPair.privateKey,
                seedPhrase = seedPhrase,
            )
        }
    }

    private fun generateRandomPassword(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#$%^&*()_-+=<>?"
        val secureRandom = SecureRandom()
        return (1..PASSWORD_LENGTH)
            .map { allowedChars[secureRandom.nextInt(allowedChars.size)] }
            .joinToString("")
    }
}

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

class TransferInteractor(
    private val web3j: Web3j,
    private val walletRepository: me.sdi.ethwallet.data.repository.WalletRepository,
    private val ethManager: EthManager,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
) {
    suspend fun transfer(data: TransferData) {
        // Desktop implementation placeholder
    }

    suspend fun estimateGas(toAddress: EthAddress, amount: BigDecimal): GasParams {
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasLimit = BigInteger.valueOf(21000)
        return GasParams.Custom(
            gasPrice = gasPrice,
            gasLimit = gasLimit,
        )
    }
}
