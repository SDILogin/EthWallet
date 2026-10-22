package me.sdi.ethwallet.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.entity.TokenBalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopTokenBalanceDaoTest {
    @Test
    fun updatesBalancesByWalletAndToken() = runBlocking {
        val dao = DesktopDatabaseBuilder.getInstance().tokenBalanceDao()
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
    }
}
