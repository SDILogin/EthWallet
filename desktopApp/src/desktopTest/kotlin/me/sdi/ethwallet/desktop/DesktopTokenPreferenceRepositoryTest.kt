package me.sdi.ethwallet.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.data.repository.TokenPreferenceRepositoryRoom
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopTokenPreferenceRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val walletAddress = EthAddress("0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")

    @Test
    fun preferencesSurviveReopeningAndAreWipedByDataCleaner() = runBlocking {
        val preferences = listOf(
            TokenPreference(daiOnSepolia.ethAddress, visible = true),
            TokenPreference(usdtOnSepolia.ethAddress, visible = false),
            TokenPreference(usdcOnSepolia.ethAddress, visible = true),
        )
        val database = DesktopDatabaseBuilder.build(temporaryFolder.root)
        try {
            TokenPreferenceRepositoryRoom(database.tokenPreferenceDao()).update(walletAddress, preferences)
        } finally {
            database.close()
        }

        val reopened = DesktopDatabaseBuilder.build(temporaryFolder.root)
        try {
            val repository = TokenPreferenceRepositoryRoom(reopened.tokenPreferenceDao())
            assertEquals(preferences, repository.observe(walletAddress).first())

            DataCleanerRoomDb(reopened).clean()

            assertEquals(emptyList<TokenPreference>(), repository.observe(walletAddress).first())
        } finally {
            reopened.close()
        }
    }
}
