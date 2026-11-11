package me.sdi.ethwallet.data.repository

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TokenPreferenceRepositoryRoomTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TokenPreferenceRepositoryRoom

    private val walletAddress = EthAddress("0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")
    private val otherWalletAddress = EthAddress("0x0000000000000000000000000000000000000002")

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = TokenPreferenceRepositoryRoom(database.tokenPreferenceDao())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun wallet_without_preferences_has_empty_list() = runTest {
        assertEquals(emptyList(), repository.observe(walletAddress).first())
    }

    @Test
    fun update_round_trips_order_visibility_and_addresses() = runTest {
        val preferences = listOf(
            TokenPreference(daiOnSepolia.ethAddress, visible = true),
            TokenPreference(usdtOnSepolia.ethAddress, visible = false),
            TokenPreference(usdcOnSepolia.ethAddress, visible = true),
        )

        repository.update(walletAddress, preferences)

        assertEquals(preferences, repository.observe(walletAddress).first())
    }

    @Test
    fun update_replaces_previous_order_and_visibility() = runTest {
        repository.update(
            walletAddress,
            listOf(
                TokenPreference(usdtOnSepolia.ethAddress, visible = true),
                TokenPreference(usdcOnSepolia.ethAddress, visible = true),
                TokenPreference(daiOnSepolia.ethAddress, visible = true),
            ),
        )
        val updated = listOf(
            TokenPreference(daiOnSepolia.ethAddress, visible = false),
            TokenPreference(usdcOnSepolia.ethAddress, visible = true),
            TokenPreference(usdtOnSepolia.ethAddress, visible = true),
        )

        repository.update(walletAddress, updated)

        assertEquals(updated, repository.observe(walletAddress).first())
    }

    @Test
    fun update_with_shorter_list_removes_tokens_that_are_not_in_it() = runTest {
        repository.update(
            walletAddress,
            listOf(
                TokenPreference(usdtOnSepolia.ethAddress, visible = true),
                TokenPreference(usdcOnSepolia.ethAddress, visible = false),
            ),
        )
        val updated = listOf(TokenPreference(daiOnSepolia.ethAddress, visible = true))

        repository.update(walletAddress, updated)

        assertEquals(updated, repository.observe(walletAddress).first())
    }

    @Test
    fun preferences_are_kept_per_wallet() = runTest {
        val walletPreferences = listOf(TokenPreference(usdtOnSepolia.ethAddress, visible = false))
        val otherWalletPreferences = listOf(TokenPreference(daiOnSepolia.ethAddress, visible = true))

        repository.update(walletAddress, walletPreferences)
        repository.update(otherWalletAddress, otherWalletPreferences)

        assertEquals(walletPreferences, repository.observe(walletAddress).first())
        assertEquals(otherWalletPreferences, repository.observe(otherWalletAddress).first())
    }
}
