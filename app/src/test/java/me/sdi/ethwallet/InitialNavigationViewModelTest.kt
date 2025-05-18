package me.sdi.ethwallet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNavigationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletDao: WalletDao
    private lateinit var viewModel: InitialNavigationViewModel

    @Before
    fun setup() {
        walletDao = mockk()
    }

    @Test
    fun `should load wallet count on initialization`() =
        runTest {
            // Arrange
            val wallets =
                listOf(
                    WalletEntity(
                        address = "0x123abc",
                        encryptedPrivateKeyBase64 = "encrypted_key_1",
                        label = "Wallet 1",
                    ),
                    WalletEntity(
                        address = "0x456def",
                        encryptedPrivateKeyBase64 = "encrypted_key_2",
                        label = "Wallet 2",
                    ),
                )

            coEvery { walletDao.getAllWallets() } returns wallets

            // Act
            viewModel = InitialNavigationViewModel(walletDao)
            advanceUntilIdle() // Let coroutines complete

            // Assert
            assertEquals(2, viewModel.numberOfWallets.first())
        }

    @Test
    fun `should handle exceptions and set wallet count to 0`() =
        runTest {
            // Arrange
            val exception = RuntimeException("Database error")
            coEvery { walletDao.getAllWallets() } throws exception

            // Act
            viewModel = InitialNavigationViewModel(walletDao)
            advanceUntilIdle() // Let coroutines complete

            // Assert
            assertEquals(0, viewModel.numberOfWallets.first())
        }
}
