package me.sdi.ethwallet.core.history

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import me.sdi.ethwallet.core.EthAddress

class TransactionHistoryProviderBlockscout(
    private val httpClient: HttpClient,
    baseUrl: String,
) : TransactionHistoryProvider {
    private val apiBaseUrl = "${baseUrl.trimEnd('/')}/api/v2"

    override suspend fun getTransactions(
        address: EthAddress,
        cursor: TransactionHistoryCursor?,
    ): TransactionHistoryPage<TransactionHistoryItem> {
        val response = httpClient.get("$apiBaseUrl/addresses/$address/transactions") {
            cursor.parameters().forEach { (name, value) -> parameter(name, value) }
        }.body<BlockscoutPage<BlockscoutTransaction>>()

        return TransactionHistoryPage(
            items = response.items.map(BlockscoutTransaction::toHistoryItem),
            nextCursor = response.nextPageParams.toCursor(),
        )
    }

    override suspend fun getTokenTransfers(
        address: EthAddress,
        cursor: TransactionHistoryCursor?,
    ): TransactionHistoryPage<TokenTransferHistoryItem> {
        val response = httpClient.get("$apiBaseUrl/addresses/$address/token-transfers") {
            cursor.parameters().forEach { (name, value) -> parameter(name, value) }
        }.body<BlockscoutPage<BlockscoutTokenTransfer>>()

        return TransactionHistoryPage(
            items = response.items.mapNotNull(BlockscoutTokenTransfer::toHistoryItem),
            nextCursor = response.nextPageParams.toCursor(),
        )
    }
}

@Serializable
private data class BlockscoutPage<T>(
    val items: List<T>,
    @SerialName("next_page_params") val nextPageParams: Map<String, JsonElement?>? = null,
)

@Serializable
private data class BlockscoutTransaction(
    val hash: String,
    val timestamp: String? = null,
    @SerialName("block_number") val blockNumber: Long? = null,
    val from: BlockscoutAddress,
    val to: BlockscoutAddress? = null,
    val value: String,
    val status: String? = null,
) {
    fun toHistoryItem() = TransactionHistoryItem(
        hash = hash,
        timestamp = timestamp,
        blockNumber = blockNumber,
        from = EthAddress(from.hash),
        to = to?.hash?.let(::EthAddress),
        valueWei = value,
        status = when {
            blockNumber == null -> TransactionStatus.Pending
            status == "ok" -> TransactionStatus.Confirmed
            else -> TransactionStatus.Failed
        },
    )
}

@Serializable
private data class BlockscoutTokenTransfer(
    @SerialName("transaction_hash") val transactionHash: String,
    @SerialName("log_index") val logIndex: Int? = null,
    val timestamp: String,
    val from: BlockscoutAddress,
    val to: BlockscoutAddress,
    val token: BlockscoutToken,
    val total: BlockscoutTokenAmount,
) {
    fun toHistoryItem(): TokenTransferHistoryItem? {
        if (token.type != "ERC-20" || total.value == null) return null

        return TokenTransferHistoryItem(
            transactionHash = transactionHash,
            logIndex = logIndex,
            timestamp = timestamp,
            from = EthAddress(from.hash),
            to = EthAddress(to.hash),
            tokenAddress = EthAddress(token.addressHash),
            tokenSymbol = token.symbol,
            tokenDecimals = total.decimals?.toIntOrNull() ?: token.decimals?.toIntOrNull(),
            value = total.value,
        )
    }
}

@Serializable
private data class BlockscoutAddress(
    val hash: String,
)

@Serializable
private data class BlockscoutToken(
    @SerialName("address_hash") val addressHash: String,
    val symbol: String? = null,
    val decimals: String? = null,
    val type: String? = null,
)

@Serializable
private data class BlockscoutTokenAmount(
    val value: String? = null,
    val decimals: String? = null,
)

private fun Map<String, JsonElement?>?.toCursor(): TransactionHistoryCursor? {
    val parameters = this
        ?.mapNotNull { (key, value) -> value?.jsonPrimitive?.content?.let { key to it } }
        ?.toMap()
        .orEmpty()
    return parameters.takeIf(Map<String, String>::isNotEmpty)?.let(::BlockscoutCursor)
}

private data class BlockscoutCursor(
    val parameters: Map<String, String>,
) : TransactionHistoryCursor

private fun TransactionHistoryCursor?.parameters(): Map<String, String> = when (this) {
    null -> emptyMap()
    is BlockscoutCursor -> parameters
    else -> throw IllegalArgumentException("Cursor was not created by TransactionHistoryProviderBlockscout")
}
