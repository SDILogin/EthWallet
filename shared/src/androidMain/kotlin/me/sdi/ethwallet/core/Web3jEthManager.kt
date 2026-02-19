package me.sdi.ethwallet.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.ui.supportedKnownTokens
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import java.math.BigInteger

class Web3jEthManager(
    private val web3j: Web3j,
    private val chainConfig: ChainConfig,
) : EthManager {
    override suspend fun getBalance(wallet: EthWallet) = coroutineScope {
        val getEthBalanceTask = async {
            try {
                val ethBalanceWei: BigInteger = getEthBalanceFromBlockchain(wallet.address)
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
                        walletAddress = wallet.address,
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
}
