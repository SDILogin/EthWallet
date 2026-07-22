package me.sdi.ethwallet.core.transfer

/**
 * Interface for handling cryptocurrency transfers.
 * Platform-specific implementations handle the actual blockchain interactions.
 */
interface TransferInteractor {
    /**
     * Executes a transfer of cryptocurrency.
     *
     * @param transferData The data required for the transfer
     * @return The transaction hash of the completed transfer
     * @throws TransferException if the transfer fails
     */
    suspend fun transfer(transferData: TransferData): String

    /**
     * Fetches the current EIP-1559 fee suggestion from the network.
     *
     * @throws Exception if the fee suggestion cannot be retrieved.
     */
    suspend fun fetchFeeSuggestion(): FeeSuggestion
}
