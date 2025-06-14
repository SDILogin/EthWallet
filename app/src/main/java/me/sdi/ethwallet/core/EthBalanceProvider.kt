package me.sdi.ethwallet.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import java.math.BigInteger

private const val TAG = "EthBalanceProvider"

class EthBalanceProvider(
    private val ethManager: EthManager,
    private val tokenBalanceDao: TokenBalanceDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : BalanceProvider {
    override suspend fun forceUpdate(wallet: EthWallet) {
        Logger.debug(TAG) {
            "Forcing update of ETH and ERC-20 token balances"
        }

        val ethBalance: Map<EthAddress, Result<BigInteger>> = ethManager.getBalance(wallet)
        val tokenBalanceEntities: List<TokenBalanceEntity> = ethBalance.map { (tokenAddress, result) ->
            val balance: BigInteger = result
                .recoverCatching { throwable ->
                    val dbEntity = tokenBalanceDao.read(
                        walletAddress = wallet.address.toString(),
                        tokenAddress = tokenAddress.toString(),
                    )
                    if (dbEntity == null) {
                        Logger.warn(TAG) {
                            "No previous balance found for token $tokenAddress, defaulting to zero"
                        }
                        throw throwable
                    }

                    dbEntity.balance.toBigInteger()
                }
                .onFailure {
                    Logger.warn(TAG) {
                        val stacktrace = result.exceptionOrNull()?.stackTraceToString()
                        "Failed to fetch balance for token $tokenAddress: $stacktrace"
                    }
                }
                .getOrDefault(BigInteger.ZERO)

            Logger.debug(TAG) {
                "Token: $tokenAddress, Balance: $balance"
            }
            TokenBalanceEntity(
                walletAddress = wallet.address.toString(),
                tokenAddress = tokenAddress.toString(),
                balance = balance.toString(),
                timestamp = now(),
            )
        }

        tokenBalanceDao.insertAll(tokenBalanceEntities)
    }

    override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> {
        return tokenBalanceDao.observe(address.toString()).map { tokenBalanceEntities ->
            Logger.debug(TAG) {
                "Observing balances for address: $address, found ${tokenBalanceEntities.size} entries"
            }
            tokenBalanceEntities.associate { entity ->
                val tokenAddress = EthAddress(entity.tokenAddress)
                val balance = BigInteger(entity.balance)
                Logger.debug(TAG) {
                    "Token: $tokenAddress, Balance: $balance"
                }
                tokenAddress to balance
            }.also { result ->
                Logger.debug(TAG) {
                    "Observed balances: ${result.size} tokens"
                }
            }
        }
    }
}
