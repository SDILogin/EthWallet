package me.sdi.ethwallet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.database.dao.WalletDao
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.ui.InitialNavigationViewModel
import me.sdi.ethwallet.ui.InitialRoute
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNavigationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var walletDao: WalletDao
    private lateinit var pinCodeStorage: PinCodeStorage
    private lateinit var viewModel: InitialNavigationViewModel

    @Before
    fun setup() {
        walletDao = mockk()
        pinCodeStorage = mockk()
    }

    @Test
    fun `should load wallet count and set onboarding initial route when no wallets`() =
        runTest {
            coEvery { walletDao.getAllWallets() } returns emptyList()
            coEvery { pinCodeStorage.hasPinCode() } returns false

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            // Backward compatibility check
            assertEquals(0, viewModel.numberOfWallets.first())
        }

    @Test
    fun `should set VerifyPinCodeScreen as initial route when wallets and pin exist`() =
        runTest {
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
            coEvery { pinCodeStorage.hasPinCode() } returns true

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(2, state.numberOfWallets)
            assertEquals(true, state.isPinCodeSet)
            assertEquals(InitialRoute.VerifyPinCode, state.initialRoute)

            // Backward compatibility check
            assertEquals(2, viewModel.numberOfWallets.first())
        }

    @Test
    fun `should set SetPinCodeScreen as initial route when wallets exist but no pin`() =
        runTest {
            val wallets =
                listOf(
                    WalletEntity(
                        address = "0x123abc",
                        encryptedPrivateKeyBase64 = "encrypted_key_1",
                        label = "Wallet 1",
                    ),
                )

            coEvery { walletDao.getAllWallets() } returns wallets
            coEvery { pinCodeStorage.hasPinCode() } returns false

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertEquals(1, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.SetPinCode, state.initialRoute)
        }

    @Test
    fun `should handle exceptions and set wallet count to 0 with onboarding route`() =
        runTest {
            val exception = RuntimeException("Database error")
            coEvery { walletDao.getAllWallets() } throws exception
            coEvery { pinCodeStorage.hasPinCode() } returns false // This shouldn't be called due to exception

            viewModel = InitialNavigationViewModel(walletDao, pinCodeStorage)
            advanceUntilIdle() // Let coroutines complete

            val state = viewModel.state.first()
            assertEquals(0, state.numberOfWallets)
            assertEquals(false, state.isPinCodeSet)
            assertEquals(InitialRoute.OnBoarding, state.initialRoute)

            // Backward compatibility check
            assertEquals(0, viewModel.numberOfWallets.first())
        }
}
