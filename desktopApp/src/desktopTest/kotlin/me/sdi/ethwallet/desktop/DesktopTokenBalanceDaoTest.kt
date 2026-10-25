package me.sdi.ethwallet.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopTokenBalanceDaoTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun updatesBalancesByWalletAndToken() = runBlocking {
        val database = DesktopDatabaseBuilder.build(temporaryFolder.root)
        try {
            val dao = database.tokenBalanceDao()
            val senderEth = TokenBalanceEntity("sender", "ETH", "10", 1L)
            val senderToken = TokenBalanceEntity("sender", "token", "20", 1L)
            val receiverEth = TokenBalanceEntity("receiver", "ETH", "30", 1L)
            assertEquals(emptyList<TokenBalanceEntity>(), dao.observe("sender").first())
            assertNull(dao.read("sender", "ETH"))

            dao.insertAll(listOf(senderEth, senderToken, receiverEth))
            val updatedEth = senderEth.copy(balance = "9", timestamp = 2L)
            dao.insertAll(listOf(updatedEth))

            assertEquals(updatedEth, dao.read("sender", "ETH"))
            assertEquals(setOf(updatedEth, senderToken), dao.observe("sender").first().toSet())
            assertEquals(listOf(receiverEth), dao.observe("receiver").first())
            assertEquals(receiverEth, dao.read("receiver", "ETH"))
        } finally {
            database.close()
        }
        val reopened = DesktopDatabaseBuilder.build(temporaryFolder.root)
        try {
            assertEquals("9", reopened.tokenBalanceDao().read("sender", "ETH")?.balance)
            DataCleanerRoomDb(reopened).clean()
            assertNull(reopened.tokenBalanceDao().read("sender", "ETH"))
        } finally {
            reopened.close()
        }
    }
}
