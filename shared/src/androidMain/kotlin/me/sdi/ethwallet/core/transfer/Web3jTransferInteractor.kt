package me.sdi.ethwallet.core.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.data.repository.WalletRepository
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger

class Web3jTransferInteractor(
    private val web3j: Web3j,
    private val walletRepository: WalletRepository,
    private val privateKeyEncryptor: PrivateKeyEncryptor,
    private val balanceProvider: BalanceProvider,
    private val chainConfig: ChainConfig,
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
            val credentials = getCredentials(wallet)

            if (transferData.contractAddress == EthContractAddress) {
                transferEth(transferData, credentials, wallet)
            } else {
                transferErc20(transferData, credentials, wallet)
            }
        }
    }

    override suspend fun fetchFeeSuggestion(): FeeSuggestion = withContext(Dispatchers.IO) {
        val block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().block
        val baseFee = block?.baseFeePerGas?.let { Numeric.decodeQuantity(it) }
            ?: throw RuntimeException("Base fee unavailable for the latest block")

        val gasPriceResponse = web3j.ethGasPrice().send()
        if (gasPriceResponse.hasError()) throw RuntimeException(gasPriceResponse.error.message)

        val priorityFee = gasPriceResponse.gasPrice.subtract(baseFee).max(BigInteger.ZERO)
        FeeSuggestion(baseFee = baseFee.toWei(), priorityFee = priorityFee.toWei())
    }

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
        val nonce = withContext(Dispatchers.IO) {
            web3j.ethGetTransactionCount(
                wallet.address.toString(),
                DefaultBlockParameterName.LATEST,
            ).send().transactionCount
        }

        val rawTransaction = withContext(Dispatchers.Default) {
            RawTransaction.createEtherTransaction(
                chainConfig.chainId,
                nonce,
                transferData.gasUnits,
                transferData.destinationAddress.toString(),
                transferData.amount,
                transferData.maxPriorityFeePerGas.value,
                transferData.maxFeePerGas.value,
            )
        }

        val signedMessage = withContext(Dispatchers.Default) {
            TransactionEncoder.signMessage(rawTransaction, credentials)
        }
        val hexValue = Numeric.toHexString(signedMessage)

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

        val nonce = withContext(Dispatchers.IO) {
            web3j.ethGetTransactionCount(
                wallet.address.toString(),
                DefaultBlockParameterName.LATEST,
            ).send().transactionCount
        }

        val rawTransaction = RawTransaction.createTransaction(
            chainConfig.chainId,
            nonce,
            transferData.gasUnits,
            transferData.contractAddress.toString(),
            BigInteger.ZERO,
            encodedFunction,
            transferData.maxPriorityFeePerGas.value,
            transferData.maxFeePerGas.value,
        )

        val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
        val hexValue = Numeric.toHexString(signedMessage)

        val response: EthSendTransaction = web3j.ethSendRawTransaction(hexValue).send()

        if (response.hasError()) {
            throw TransferException.TransactionFailed("ERC-20 transfer failed: ${response.error.message}")
        }

        return response.transactionHash
    }
}
