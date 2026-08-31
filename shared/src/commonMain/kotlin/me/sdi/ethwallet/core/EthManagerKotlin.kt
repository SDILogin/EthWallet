package me.sdi.ethwallet.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.kotlin.EthWalletKotlin
import me.sdi.ethwallet.kotlin.EthereumNetwork
import me.sdi.ethwallet.kotlin.Mnemonic
import me.sdi.ethwallet.kotlin.PrivateKey
import me.sdi.ethwallet.kotlin.TransferRequest
import me.sdi.ethwallet.kotlin.UInt256
import me.sdi.ethwallet.kotlin.deriveAddress
import me.sdi.ethwallet.kotlin.derivePrivateKey
import me.sdi.ethwallet.ui.supportedKnownTokens
import java.math.BigInteger
import me.sdi.ethwallet.kotlin.Asset as KotlinAsset
import me.sdi.ethwallet.kotlin.EthAddress as KotlinEthAddress

class EthManagerKotlin(
    private val network: EthereumNetwork,
) : EthManager {
    override suspend fun getBalance(address: EthAddress): Map<EthAddress, Result<BigInteger>> = coroutineScope {
        val tokenBalances = supportedKnownTokens(network.chainId).map { token ->
            async {
                token.ethAddress to balanceResult {
                    EthWalletKotlin.getBalance(
                        network = network,
                        owner = address.toKotlinAddress(),
                        asset = KotlinAsset.Erc20(token.ethAddress.toKotlinAddress()),
                    ).toBigInteger()
                }
            }
        }
        val ethBalance = async {
            EthContractAddress to balanceResult {
                EthWalletKotlin.getBalance(
                    network = network,
                    owner = address.toKotlinAddress(),
                    asset = KotlinAsset.Eth,
                ).toBigInteger()
            }
        }

        (tokenBalances + ethBalance).awaitAll().toMap()
    }

    override suspend fun fetchFeeSuggestion(): FeeSuggestion {
        val suggestion = EthWalletKotlin.getFeeSuggestion(network)
        return FeeSuggestion(
            baseFee = suggestion.baseFeePerGas.toBigInteger().let(::Wei),
            priorityFee = suggestion.priorityFeePerGas.toBigInteger().let(::Wei),
        )
    }

    override suspend fun sendTransfer(
        privateKey: PrivateKey,
        contractAddress: EthAddress,
        destinationAddress: EthAddress,
        amount: BigInteger,
        gasUnits: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
    ): String {
        val request = TransferRequest(
            destination = destinationAddress.toKotlinAddress(),
            asset = if (contractAddress.isEthContract()) {
                KotlinAsset.Eth
            } else {
                KotlinAsset.Erc20(contractAddress.toKotlinAddress())
            },
            amount = amount.toUInt256("amount"),
            gasUnits = gasUnits.toUInt256("gasUnits"),
            maxPriorityFeePerGas = maxPriorityFeePerGas.toUInt256("maxPriorityFeePerGas"),
            maxFeePerGas = maxFeePerGas.toUInt256("maxFeePerGas"),
        )
        return EthWalletKotlin.sendTransfer(
            network = network,
            privateKey = privateKey,
            request = request,
        ).value
    }

    override suspend fun createWallet(): EthCreationResult {
        val mnemonic = EthWalletKotlin.createWallet()
        return deriveWallet(mnemonic)
    }

    override suspend fun importWallet(mnemonic: String): EthCreationResult {
        val parsedMnemonic = Mnemonic.parse(mnemonic)
        return deriveWallet(parsedMnemonic)
    }

    private suspend fun deriveWallet(mnemonic: Mnemonic): EthCreationResult {
        var privateKey: PrivateKey? = null
        return try {
            val derivedPrivateKey = mnemonic.derivePrivateKey()
            privateKey = derivedPrivateKey
            val address = derivedPrivateKey.deriveAddress()
            val seedPhrase = mnemonic.words.map { word -> word.copyWord() }
            EthCreationResult(
                address = EthAddress(address.value),
                privateKey = derivedPrivateKey,
                seedPhrase = seedPhrase,
            ).also {
                privateKey = null
            }
        } catch (failure: Throwable) {
            privateKey?.clear()
            throw failure
        } finally {
            mnemonic.clear()
        }
    }

    private fun EthAddress.toKotlinAddress(): KotlinEthAddress = KotlinEthAddress(toString())

    private fun EthAddress.isEthContract(): Boolean =
        toString().equals(EthContractAddress.toString(), ignoreCase = true)

    private fun BigInteger.toUInt256(fieldName: String): UInt256 {
        require(signum() >= 0) { "$fieldName must not be negative" }
        require(bitLength() <= UINT256_BITS) { "$fieldName exceeds 256 bits" }
        return UInt256("0x${toString(16)}")
    }

    private fun UInt256.toBigInteger(): BigInteger = BigInteger(hex.removePrefix("0x"), HEX_RADIX)

    private suspend fun <T> balanceResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(failure)
    }

    private fun CharSequence.copyWord(): String = buildString(length) {
        for (index in this@copyWord.indices) {
            append(this@copyWord[index])
        }
    }

    private companion object {
        const val HEX_RADIX = 16
        const val UINT256_BITS = 256
    }
}
