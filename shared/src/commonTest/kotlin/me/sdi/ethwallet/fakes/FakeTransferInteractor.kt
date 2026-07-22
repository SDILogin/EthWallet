package me.sdi.ethwallet.fakes

import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.core.transfer.TransferData
import me.sdi.ethwallet.core.transfer.TransferInteractor

class FakeTransferInteractor : TransferInteractor {
    var transferResult: Result<String> = Result.success("0xTransactionHash")
    var lastTransferData: TransferData? = null
    var feeSuggestionToReturn: FeeSuggestion? = null
    var fetchFeeSuggestionCallCount = 0

    override suspend fun transfer(transferData: TransferData): String {
        lastTransferData = transferData
        val result = transferResult
        if (result.isSuccess) {
            return result.getOrThrow()
        } else {
            throw result.exceptionOrNull()!!
        }
    }

    override suspend fun fetchFeeSuggestion(): FeeSuggestion {
        fetchFeeSuggestionCallCount++
        return feeSuggestionToReturn ?: throw RuntimeException("fee suggestion unavailable")
    }
}
