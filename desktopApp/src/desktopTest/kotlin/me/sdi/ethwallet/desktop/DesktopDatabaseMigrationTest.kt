package me.sdi.ethwallet.desktop

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.data.repository.TokenPreferenceRepositoryRoom
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.ui.daiOnSepolia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopDatabaseMigrationTest {
    private val databaseDirectory = Files.createTempDirectory("ethwallet-migration").toFile()

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        schemaDirectoryPath = File("../shared/schemas").toPath(),
        databasePath = File(databaseDirectory, "ethwallet_db").toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    private val walletAddress = EthAddress("0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")

    @After
    fun tearDown() {
        databaseDirectory.deleteRecursively()
    }

    @Test
    fun migrationFromVersion1KeepsWalletsAndAddsTokenPreferences() = runBlocking {
        migrationTestHelper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO wallets (address, encryptedPrivateKeyBase64, label) " +
                    "VALUES ('$walletAddress', 'encrypted-key', 'Main')",
            )
            close()
        }

        migrationTestHelper.runMigrationsAndValidate(2).close()

        val database = DesktopDatabaseBuilder.build(databaseDirectory)
        try {
            assertEquals(listOf("Main"), database.walletDao().observeAllWallets().first().map { it.label })
            val repository = TokenPreferenceRepositoryRoom(database.tokenPreferenceDao())
            val preferences = listOf(TokenPreference(daiOnSepolia.ethAddress, visible = false))
            repository.update(walletAddress, preferences)
            assertEquals(preferences, repository.observe(walletAddress).first())
        } finally {
            database.close()
        }
    }
}
