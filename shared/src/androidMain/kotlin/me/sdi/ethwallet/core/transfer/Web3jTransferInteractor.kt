package me.sdi.ethwallet.core.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

class Web3jTransferInteractor(
    private val web3j: Web3j,
    private val walletRepository: WalletRepository,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
    private val balanceProvider: BalanceProvider,
) : TransferInteractor {
    // Mutex to prevent race conditions in nonce management
    private val transferMutex = Mutex()

    override suspend fun transfer(transferData: TransferData): String {
        return transferMutex.withLock {
            val wallet = checkNotNull(walletRepository.getActiveWallet()) {
                "No active wallet found"
            }

            validateTransferData(transferData, wallet)
            validateSufficientBalance(transferData, wallet)
            val credentials = getCredentials(wallet)

            if (transferData.contractAddress == EthContractAddress) {
                transferEth(transferData, credentials, wallet)
            } else {
                transferErc20(transferData, credentials, wallet)
            }
        }
    }

    private fun validateTransferData(transferData: TransferData, wallet: EthWallet) {
        if (transferData.amount <= BigInteger.ZERO) {
            throw TransferException.InvalidAmount("Transfer amount must be positive")
        }

        if (wallet.address == transferData.destinationAddress) {
            throw TransferException.InvalidAddress("Source and destination addresses must be different")
        }

        when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> {
                if (gasParams.gasPrice <= BigInteger.ZERO) {
                    throw TransferException.GasPriceError("Gas price must be positive")
                }
                if (gasParams.gasLimit <= BigInteger.ZERO) {
                    throw TransferException.GasPriceError("Gas limit must be positive")
                }
            }
            is GasParams.Default -> {
                // No additional validation needed for default gas params
            }
        }
    }

    private suspend fun validateSufficientBalance(transferData: TransferData, wallet: EthWallet) {
        // Force update to get latest balances
        balanceProvider.forceUpdate(wallet)

        // Get current balances - this returns a flow, so we need to get the latest value
        // For now, we'll use a simplified approach and call the blockchain directly
        val ethBalance = withContext(Dispatchers.IO) {
            web3j.ethGetBalance(
                wallet.address.toString(),
                DefaultBlockParameterName.LATEST,
            ).send().balance
        }

        // Calculate gas cost
        val gasPrice = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasPrice
            is GasParams.Default -> DefaultGasProvider.GAS_PRICE
        }
        val gasLimit = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasLimit
            is GasParams.Default -> DefaultGasProvider.GAS_LIMIT
        }
        val gasCost = gasPrice.multiply(gasLimit)

        if (transferData.contractAddress == EthContractAddress) {
            // ETH transfer: need enough ETH for amount + gas
            val totalRequired = transferData.amount.add(gasCost)
            if (ethBalance < totalRequired) {
                throw TransferException.InsufficientBalance(
                    "Insufficient ETH balance. Required: $totalRequired wei (${transferData.amount} + $gasCost gas), Available: $ethBalance wei",
                )
            }
        } else {
            // ERC-20 transfer: need enough ETH for gas, and enough tokens for transfer
            if (ethBalance < gasCost) {
                throw TransferException.InsufficientBalance(
                    "Insufficient ETH balance for gas fees. Required: $gasCost wei, Available: $ethBalance wei",
                )
            }

            // Check token balance - simplified approach using direct contract call
            val tokenBalance = try {
                getErc20Balance(transferData.contractAddress, wallet.address)
            } catch (e: Exception) {
                throw TransferException.NetworkError("Failed to get token balance: ${e.message}", e)
            }

            if (tokenBalance < transferData.amount) {
                throw TransferException.InsufficientBalance(
                    "Insufficient token balance. Required: ${transferData.amount}, Available: $tokenBalance",
                )
            }
        }
    }

    private suspend fun getErc20Balance(contractAddress: EthAddress, walletAddress: EthAddress): BigInteger {
        val balanceOfFunction = Function(
            "balanceOf",
            listOf(Address(walletAddress.toString())),
            listOf(object : org.web3j.abi.TypeReference<Uint256>() {}),
        )

        val encodedFunction = FunctionEncoder.encode(balanceOfFunction)
        val response = withContext(Dispatchers.IO) {
            web3j.ethCall(
                Transaction.createEthCallTransaction(
                    walletAddress.toString(),
                    contractAddress.toString(),
                    encodedFunction,
                ),
                DefaultBlockParameterName.LATEST,
            ).send()
        }

        if (response.hasError()) {
            throw TransferException.NetworkError(
                "Failed to get token balance: ${response.error.message}",
                RuntimeException(response.error.message),
            )
        }

        val result = withContext(Dispatchers.IO) {
            FunctionReturnDecoder.decode(
                response.value,
                balanceOfFunction.outputParameters,
            )
        }

        return if (result.isNotEmpty()) {
            result[0].value as BigInteger
        } else {
            BigInteger.ZERO
        }
    }

    private suspend fun getCredentials(wallet: EthWallet): Credentials {
        val encryptedPrivateKey = walletRepository.getEncryptedPrivateKey(wallet.address)
        return withContext(Dispatchers.Default) {
            val privateKeyBigInt = privateKeyEncryptor.decryptPrivateKey(
                encryptedPrivateKey,
                wallet.address.toString(),
            )
            Credentials.create(String.format("%064x", privateKeyBigInt))
        }
    }

    private suspend fun transferEth(
        transferData: TransferData,
        credentials: Credentials,
        wallet: EthWallet,
    ): String {
        val gasPrice = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasPrice
            is GasParams.Default -> DefaultGasProvider.GAS_PRICE
        }

        val gasLimit = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasLimit
            is GasParams.Default -> DefaultGasProvider.GAS_LIMIT
        }

        // Get current nonce for the wallet that will sign the transaction
        val nonce = withContext(Dispatchers.IO) {
            web3j.ethGetTransactionCount(
                wallet.address.toString(),
                DefaultBlockParameterName.LATEST,
            ).send().transactionCount
        }

        // Create raw transaction
        val rawTransaction = withContext(Dispatchers.Default) {
            RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                transferData.destinationAddress.toString(),
                transferData.amount,
            )
        }

        // Sign the transaction
        val signedMessage = withContext(Dispatchers.Default) {
            TransactionEncoder.signMessage(rawTransaction, credentials)
        }
        val hexValue = Numeric.toHexString(signedMessage)

        // Send the signed transaction
        val response: EthSendTransaction = withContext(Dispatchers.IO) {
            web3j.ethSendRawTransaction(hexValue).send()
        }

        if (response.hasError()) {
            throw TransferException.TransactionFailed("ETH transfer failed: ${response.error.message}")
        }

        return response.transactionHash
    }

    private suspend fun transferErc20(
        transferData: TransferData,
        credentials: Credentials,
        wallet: EthWallet,
    ): String {
        val transferFunction = Function(
            "transfer",
            listOf(
                Address(transferData.destinationAddress.toString()),
                Uint256(transferData.amount),
            ),
            emptyList(),
        )

        val encodedFunction = FunctionEncoder.encode(transferFunction)

        val gasPrice = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasPrice
            is GasParams.Default -> DefaultGasProvider.GAS_PRICE
        }

        val gasLimit = when (val gasParams = transferData.gasParams) {
            is GasParams.Custom -> gasParams.gasLimit
            is GasParams.Default -> DefaultGasProvider.GAS_LIMIT
        }

        // Get current nonce for the wallet that will sign the transaction
        val nonce = withContext(Dispatchers.IO) {
            web3j.ethGetTransactionCount(
                wallet.address.toString(),
                DefaultBlockParameterName.LATEST,
            ).send().transactionCount
        }

        // Create raw transaction
        val rawTransaction = RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            transferData.contractAddress.toString(),
            BigInteger.ZERO, // value is 0 for ERC-20 transfers
            encodedFunction,
        )

        // Sign the transaction
        val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
        val hexValue = Numeric.toHexString(signedMessage)

        // Send the signed transaction
        val response: EthSendTransaction = web3j.ethSendRawTransaction(hexValue).send()

        if (response.hasError()) {
            throw TransferException.TransactionFailed("ERC-20 transfer failed: ${response.error.message}")
        }

        return response.transactionHash
    }
}
