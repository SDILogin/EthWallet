package me.sdi.ethwallet.core.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import java.math.BigInteger

class EthTransferInteractor(
    private val ethManager: EthManager,
    private val walletRepository: WalletRepository,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
    private val balanceProvider: BalanceProvider,
) : TransferInteractor {
    private val transferMutex = Mutex()

    override suspend fun transfer(transferData: TransferData): String {
        return transferMutex.withLock {
            val wallet = checkNotNull(walletRepository.getActiveWallet()) {
                "No active wallet found"
            }

            validateTransferData(transferData, wallet)

            val gasCost = transferData.maxFeePerGas.value.multiply(transferData.gasUnits)
            validateSufficientBalance(transferData, wallet, gasCost)

            val privateKey = decryptPrivateKey(wallet)

            try {
                ethManager.sendTransfer(
                    privateKey = privateKey,
                    contractAddress = transferData.contractAddress,
                    destinationAddress = transferData.destinationAddress,
                    amount = transferData.amount,
                    gasUnits = transferData.gasUnits,
                    maxPriorityFeePerGas = transferData.maxPriorityFeePerGas.value,
                    maxFeePerGas = transferData.maxFeePerGas.value,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TransferException.TransactionFailed("Transfer failed: ${e.message}")
            }
        }
    }

    override suspend fun fetchFeeSuggestion(): FeeSuggestion = ethManager.fetchFeeSuggestion()

    private fun validateTransferData(transferData: TransferData, wallet: EthWallet) {
        if (transferData.amount <= BigInteger.ZERO) {
            throw TransferException.InvalidAmount("Transfer amount must be positive")
        }

        if (wallet.address == transferData.destinationAddress) {
            throw TransferException.InvalidAddress("Source and destination addresses must be different")
        }

        if (transferData.maxFeePerGas.value.signum() <= 0) {
            throw TransferException.GasPriceError("Max fee per gas must be positive")
        }

        if (transferData.maxPriorityFeePerGas.value.signum() < 0) {
            throw TransferException.GasPriceError("Max priority fee per gas must not be negative")
        }

        if (transferData.maxPriorityFeePerGas.value > transferData.maxFeePerGas.value) {
            throw TransferException.GasPriceError("Max priority fee per gas must not exceed max fee per gas")
        }

        if (transferData.gasUnits < ETH_TRANSFER_GAS_UNITS) {
            throw TransferException.GasPriceError("Gas units must be at least $ETH_TRANSFER_GAS_UNITS")
        }
    }

    private suspend fun validateSufficientBalance(
        transferData: TransferData,
        wallet: EthWallet,
        gasCost: BigInteger,
    ) {
        balanceProvider.forceUpdate(wallet)
        val balances = balanceProvider.observe(wallet.address).first()
        val ethBalance = balances[EthContractAddress]
            ?: throw TransferException.NetworkError("ETH balance unavailable")

        if (transferData.contractAddress == EthContractAddress) {
            val totalRequired = transferData.amount.add(gasCost)
            if (ethBalance < totalRequired) {
                throw TransferException.InsufficientBalance(
                    "Insufficient ETH balance. Required: $totalRequired wei (${transferData.amount} + $gasCost gas), Available: $ethBalance wei",
                )
            }
        } else {
            if (ethBalance < gasCost) {
                throw TransferException.InsufficientBalance(
                    "Insufficient ETH balance for gas fees. Required: $gasCost wei, Available: $ethBalance wei",
                )
            }

            val tokenBalance = balances[transferData.contractAddress]
                ?: throw TransferException.NetworkError("Token balance unavailable")

            if (tokenBalance < transferData.amount) {
                throw TransferException.InsufficientBalance(
                    "Insufficient token balance. Required: ${transferData.amount}, Available: $tokenBalance",
                )
            }
        }
    }

    private suspend fun decryptPrivateKey(wallet: EthWallet): BigInteger {
        val encryptedPrivateKey = walletRepository.getEncryptedPrivateKey(wallet.address)
        return withContext(Dispatchers.Default) {
            privateKeyEncryptor.decryptPrivateKey(
                encryptedPrivateKey,
                wallet.address.toString(),
            )
        }
    }
}
