package me.sdi.ethwallet.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.core.transfer.toWei
import me.sdi.ethwallet.ui.supportedKnownTokens
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SecureRandom

/**
 * web3j-backed [EthManager]. Wallets follow the standard BIP-39/BIP-44 scheme
 * (path m/44'/60'/0'/0/0, empty passphrase), so seed phrases are interchangeable
 * with Trust Wallet, MetaMask and other mainstream wallets.
 */
class Web3jEthManager(
    private val web3j: Web3j,
    private val chainConfig: ChainConfig,
) : EthManager {
    override suspend fun getBalance(address: EthAddress) = coroutineScope {
        val getEthBalanceTask = async {
            try {
                val ethBalanceWei: BigInteger = getEthBalanceFromBlockchain(address)
                Pair(EthContractAddress, Result.success(ethBalanceWei))
            } catch (e: Exception) {
                ensureActive()

                Pair(EthContractAddress, Result.failure(e))
            }
        }

        val getTrc20TokensBalancesTasks = supportedKnownTokens(chainConfig.chainId).map { erc20Token ->
            val tokenContractAddress: EthAddress = erc20Token.ethAddress
            async {
                try {
                    val balanceInMinimalUnits: BigInteger = getErc20TokenBalanceFromBlockchain(
                        walletAddress = address,
                        tokenContractAddress = tokenContractAddress,
                    )
                    Pair(tokenContractAddress, Result.success(balanceInMinimalUnits))
                } catch (e: Exception) {
                    ensureActive()

                    Pair(tokenContractAddress, Result.failure(e))
                }
            }
        }

        (getTrc20TokensBalancesTasks + getEthBalanceTask).awaitAll().toMap()
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

    override suspend fun sendTransfer(
        privateKey: BigInteger,
        contractAddress: EthAddress,
        destinationAddress: EthAddress,
        amount: BigInteger,
        gasUnits: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
    ): String {
        val credentials = withContext(Dispatchers.Default) {
            Credentials.create(ECKeyPair.create(privateKey))
        }

        val nonce = withContext(Dispatchers.IO) {
            web3j.ethGetTransactionCount(
                credentials.address,
                DefaultBlockParameterName.LATEST,
            ).send().transactionCount
        }

        val rawTransaction = if (contractAddress == EthContractAddress) {
            RawTransaction.createEtherTransaction(
                chainConfig.chainId,
                nonce,
                gasUnits,
                destinationAddress.toString(),
                amount,
                maxPriorityFeePerGas,
                maxFeePerGas,
            )
        } else {
            val transferFunction = Function(
                "transfer",
                listOf(
                    Address(destinationAddress.toString()),
                    Uint256(amount),
                ),
                emptyList(),
            )
            RawTransaction.createTransaction(
                chainConfig.chainId,
                nonce,
                gasUnits,
                contractAddress.toString(),
                BigInteger.ZERO,
                FunctionEncoder.encode(transferFunction),
                maxPriorityFeePerGas,
                maxFeePerGas,
            )
        }

        val hexValue = withContext(Dispatchers.Default) {
            Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, credentials))
        }

        val response: EthSendTransaction = withContext(Dispatchers.IO) {
            web3j.ethSendRawTransaction(hexValue).send()
        }

        if (response.hasError()) {
            throw RuntimeException(response.error.message)
        }

        return response.transactionHash
    }

    override fun createWallet(): EthCreationResult {
        val entropy = ByteArray(ENTROPY_BYTES).also { SecureRandom().nextBytes(it) }
        val mnemonic = MnemonicUtils.generateMnemonic(entropy)
        return deriveWallet(mnemonic)
    }

    override fun importWallet(mnemonic: String): EthCreationResult {
        val normalized = mnemonic.trim().lowercase().replace(WHITESPACE, " ")
        require(MnemonicUtils.validateMnemonic(normalized)) { "Invalid recovery phrase" }
        return deriveWallet(normalized)
    }

    private fun deriveWallet(mnemonic: String): EthCreationResult {
        val seed = MnemonicUtils.generateSeed(mnemonic, "")
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, DERIVATION_PATH)
        val credentials = Credentials.create(derivedKeyPair)
        return EthCreationResult(
            address = EthAddress(credentials.address),
            privateKey = derivedKeyPair.privateKey,
            seedPhrase = mnemonic.split(" "),
        )
    }

    private suspend fun getErc20TokenBalanceFromBlockchain(
        walletAddress: EthAddress,
        tokenContractAddress: EthAddress,
    ): BigInteger {
        val balanceOfFunction = Function(
            "balanceOf",
            listOf(Address(walletAddress.toString())),
            listOf(object : TypeReference<Uint256>() {}),
        )

        val erc20TokenBalanceRequest = Transaction.createEthCallTransaction(
            walletAddress.toString(),
            tokenContractAddress.toString(),
            FunctionEncoder.encode(balanceOfFunction),
        )
        val response: EthCall = withContext(Dispatchers.IO) {
            web3j
                .ethCall(erc20TokenBalanceRequest, DefaultBlockParameterName.LATEST)
                .send()
        }

        val decoded: List<Type<*>> = FunctionReturnDecoder
            .decode(response.value, balanceOfFunction.outputParameters)

        val balanceInMinimalUnits = decoded[0].value as BigInteger
        return balanceInMinimalUnits
    }

    private suspend fun getEthBalanceFromBlockchain(
        address: EthAddress,
    ): BigInteger = withContext(Dispatchers.IO) {
        web3j.ethGetBalance(address.toString(), DefaultBlockParameterName.LATEST).send().balance
    }

    companion object {
        private const val ENTROPY_BYTES = 16
        private val WHITESPACE = Regex("\\s+")

        private val DERIVATION_PATH = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0,
            0,
        )

        fun create(chainConfig: ChainConfig): Web3jEthManager =
            Web3jEthManager(Web3j.build(HttpService(chainConfig.rpcUrl)), chainConfig)
    }
}
