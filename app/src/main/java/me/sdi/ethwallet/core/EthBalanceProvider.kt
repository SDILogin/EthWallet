package me.sdi.ethwallet.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

class EthBalanceProvider(private val web3j: Web3j) : BalanceProvider {
    override suspend fun getEthBalance(address: EthAddress): BigInteger = withContext(Dispatchers.IO) {
        Logger.debug("EthBalanceProvider") {
            "Fetching ETH balance for address: $address"
        }

        val ethGetBalance = web3j
            .ethGetBalance(address.toString(), DefaultBlockParameterName.LATEST)
            .sendAsync()
            .get()
        ethGetBalance.balance.also {
            Logger.debug("EthBalanceProvider") {
                "ETH balance for address $address: $it wei"
            }
        }
    }

    override suspend fun getErc20TokensBalance(
        walletAddress: EthAddress,
        tokenContractAddresses: List<EthAddress>,
    ): Map<EthAddress, BigInteger> = withContext(Dispatchers.IO) {
        Logger.debug("EthBalanceProvider") {
            "Fetching ERC-20 balances for wallet $walletAddress, tokens: ${tokenContractAddresses.size}"
        }

        tokenContractAddresses.map { tokenAddress ->
            async {
                try {
                    Logger.debug("EthBalanceProvider") {
                        "Fetching balance for token $tokenAddress"
                    }

                    val balanceOfFunction = Function(
                        "balanceOf",
                        listOf(Address(walletAddress.toString())),
                        listOf(object : TypeReference<Uint256>() {}),
                    )

                    val encodedFunction = FunctionEncoder.encode(balanceOfFunction)

                    val response = web3j.ethCall(
                        Transaction.createEthCallTransaction(
                            walletAddress.toString(),
                            tokenAddress.toString(),
                            encodedFunction,
                        ),
                        DefaultBlockParameterName.LATEST,
                    ).sendAsync().get()

                    val output = response.value
                    val decoded = FunctionReturnDecoder.decode(output, balanceOfFunction.outputParameters)

                    val balance = if (decoded.isNotEmpty()) {
                        decoded[0].value as BigInteger
                    } else {
                        BigInteger.ZERO
                    }

                    Logger.debug("EthBalanceProvider") {
                        "Token $tokenAddress balance: $balance wei"
                    }

                    tokenAddress to balance
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    Logger.warn("EthBalanceProvider") {
                        "Failed to fetch balance for token $tokenAddress: ${e.message}"
                    }

                    tokenAddress to BigInteger.ZERO
                }
            }
        }.awaitAll().toMap().also { result ->
            Logger.debug("EthBalanceProvider") {
                "Completed ERC-20 balance fetching. Retrieved ${result.size} token balances"
            }
        }
    }
}
