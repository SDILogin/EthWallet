package me.sdi.ethwallet.core.history

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.transfer.toWei
import java.math.BigInteger
import kotlin.time.Instant

class TransfersHistoryProviderBlockscout(
    private val httpClient: HttpClient,
    baseUrl: String,
) : TransfersHistoryProvider {
    private val apiBaseUrl = "${baseUrl.trimEnd('/')}/api/v2"

    override suspend fun getTransfers(
        address: EthAddress,
        eth: TransfersPageRequest,
        erc20: TransfersPageRequest,
    ): CombinedTransfersPage = coroutineScope {
        val transactions = async {
            if (eth == TransfersPageRequest.Skip) null else load<BlockscoutTransaction>(address, "transactions", eth)
        }
        val tokens = async {
            if (erc20 == TransfersPageRequest.Skip) {
                null
            } else {
                load<BlockscoutTokenTransfer>(
                    address,
                    "token-transfers",
                    erc20,
                )
            }
        }
        val transactionPage = transactions.await()
        val tokenPage = tokens.await()
        val transactionByHash = transactionPage?.items.orEmpty().associateBy { it.hash.lowercase() }.toMutableMap()
        val ethItems = transactionPage?.items.orEmpty().mapNotNull { it.toTransfer(address) }
        val tokenItems = mutableListOf<Transfer>()
        for (item in tokenPage?.items.orEmpty()) {
            if ((item.tokenType ?: item.token.type) != "ERC-20") continue
            val amount = requireNotNull(item.total?.value).nonNegativeInteger()
            if (amount.signum() == 0) continue
            val from = EthAddress(item.from.hash)
            val to = EthAddress(item.to.hash)
            if (!address.matches(from) && !address.matches(to)) continue
            val outgoing = address.matches(from)
            val transaction = if (outgoing) {
                transactionByHash.getOrPut(item.transactionHash.lowercase()) {
                    httpClient.get("$apiBaseUrl/transactions/${item.transactionHash}").body<BlockscoutTransaction>()
                }
            } else {
                null
            }
            val status = transaction?.transactionStatus() ?: TransactionStatus.Confirmed
            val decimals = (item.total.decimals ?: item.token.decimals)?.toInt() ?: 6
            require(decimals in 0..255) { "Invalid token decimals" }
            require(item.logIndex >= 0) { "Invalid transfer log index" }
            tokenItems += Transfer.Erc20(
                hash = item.transactionHash,
                timestamp = item.timestamp.toTimestamp(),
                from = from,
                to = to,
                logIndex = item.logIndex,
                tokenAddress = EthAddress(item.token.addressHash),
                tokenSymbol = item.token.symbol,
                tokenDecimals = decimals,
                value = amount.toString(),
                status = status,
                fee = transaction?.transactionFee(required = status != TransactionStatus.Failed),
            )
        }
        CombinedTransfersPage(
            eth = transactionPage?.let {
                TransfersPage(
                    ethItems,
                    it.nextPageParams.toRequest(),
                    it.items.minOfOrNull { item -> item.timestamp.toTimestamp() },
                )
            },
            erc20 = tokenPage?.let {
                TransfersPage(
                    tokenItems,
                    it.nextPageParams.toRequest(),
                    it.items.minOfOrNull { item -> item.timestamp.toTimestamp() },
                )
            },
        )
    }

    private suspend inline fun <reified T> load(
        address: EthAddress,
        endpoint: String,
        request: TransfersPageRequest,
    ): BlockscoutPage<T> = httpClient.get("$apiBaseUrl/addresses/$address/$endpoint") {
        if (request is TransfersPageRequest.Next) {
            request.parameters.forEach { (key, value) -> parameter(key, value) }
        }
    }.body()
}

@Serializable
private data class BlockscoutPage<T>(
    val items: List<T>,
    @SerialName("next_page_params") val nextPageParams: Map<String, JsonElement?>? = null,
)

@Serializable
private data class BlockscoutTransaction(
    val hash: String,
    val timestamp: String,
    @SerialName("block_number") val blockNumber: Long? = null,
    val from: BlockscoutAddress,
    val to: BlockscoutAddress? = null,
    val value: String,
    val status: String? = null,
    val fee: BlockscoutFee? = null,
    @SerialName("transaction_types") val transactionTypes: List<String> = emptyList(),
) {
    fun toTransfer(address: EthAddress): Transfer.Eth? {
        val amount = value.nonNegativeInteger()
        if (amount.signum() == 0 || to == null) return null
        if ("contract_call" in transactionTypes) return null
        val sender = EthAddress(from.hash)
        val recipient = EthAddress(to.hash)
        if (!address.matches(sender) && !address.matches(recipient)) return null
        val status = transactionStatus()
        return Transfer.Eth(
            hash = hash,
            timestamp = timestamp.toTimestamp(),
            from = sender,
            to = recipient,
            valueWei = amount.toWei(),
            status = status,
            fee = transactionFee(required = address.matches(sender) && status != TransactionStatus.Failed),
        )
    }

    fun transactionStatus(): TransactionStatus = when {
        blockNumber == null -> TransactionStatus.Pending
        status == "ok" -> TransactionStatus.Confirmed
        status == "error" -> TransactionStatus.Failed
        else -> error("Unknown transaction status")
    }

    fun transactionFee(required: Boolean): TransactionFee? {
        val data = fee
        if (data == null) {
            require(!required) { "Missing outgoing transaction fee" }
            return null
        }
        require(data.type == "actual" || data.type == "maximum") { "Unknown transaction fee type" }
        return TransactionFee(data.value.nonNegativeInteger().toWei(), estimated = data.type == "maximum")
    }
}

@Serializable
private data class BlockscoutFee(val type: String, val value: String)

@Serializable
private data class BlockscoutTokenTransfer(
    @SerialName("transaction_hash") val transactionHash: String,
    @SerialName("log_index") val logIndex: Int,
    val timestamp: String,
    val from: BlockscoutAddress,
    val to: BlockscoutAddress,
    val token: BlockscoutToken,
    val total: BlockscoutTokenAmount? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
private data class BlockscoutAddress(val hash: String)

@Serializable
private data class BlockscoutToken(
    @SerialName("address_hash") val addressHash: String,
    val symbol: String? = null,
    val decimals: String? = null,
    val type: String? = null,
)

@Serializable
private data class BlockscoutTokenAmount(val value: String? = null, val decimals: String? = null)

private fun Map<String, JsonElement?>?.toRequest(): TransfersPageRequest {
    val parameters = this?.filterValues { it != null && it != JsonNull }
        ?.mapValues { (_, value) -> requireNotNull(value).jsonPrimitive.content }
        ?.takeIf { it.isNotEmpty() } ?: return TransfersPageRequest.Skip
    return TransfersPageRequest.Next(parameters)
}

private fun String.toTimestamp(): Long = Instant.parse(this).toEpochMilliseconds()

private fun String.nonNegativeInteger(): BigInteger = toBigInteger().also { require(it.signum() >= 0) }

private fun EthAddress.matches(other: EthAddress): Boolean = toString().equals(other.toString(), ignoreCase = true)
