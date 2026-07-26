package me.sdi.ethwallet.flow

import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.MainActivity
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.data.repository.EthWallet
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.flow.pageobjects.CreateWalletPage
import me.sdi.ethwallet.flow.pageobjects.OnboardingPage
import me.sdi.ethwallet.flow.pageobjects.SetPinCodePage
import me.sdi.ethwallet.flow.pageobjects.WalletPage
import me.sdi.ethwallet.flow.pageobjects.WalletsListPage
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class WalletFlowTest : KoinTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val appDatabase: AppDatabase by inject()

    private val onboardingPage = OnboardingPage(composeTestRule)
    private val createWalletPage = CreateWalletPage(composeTestRule)
    private val setPinCodePage = SetPinCodePage(composeTestRule)
    private val walletPage = WalletPage(composeTestRule)
    private val walletsListPage = WalletsListPage(composeTestRule)

    private val pinCodeStorage = object : PinCodeStorage {
        var storedPin: String? = null

        override suspend fun storePinCode(pinCode: String) {
            storedPin = pinCode
        }

        override suspend fun verifyPinCode(pinCode: String): Boolean {
            return storedPin == pinCode
        }

        override suspend fun hasPinCode(): Boolean {
            return storedPin != null
        }

        override suspend fun clearPinCode() {
            storedPin = null
        }
    }

    private val mockModule = module {
        single<PinAttemptsGuard> {
            object : PinAttemptsGuard {
                private var pinLockState = PinLockState.Active(
                    totalFailures = 0,
                    remainingInWindow = 0,
                    lockRemaining = Duration.ZERO,
                )

                override suspend fun bootstrap() = Unit

                override suspend fun status() = pinLockState

                override suspend fun onFailedAttempt(): PinLockState {
                    pinLockState = PinLockState.Active(
                        totalFailures = 1,
                        remainingInWindow = 0,
                        lockRemaining = Duration.ZERO,
                    )
                    return pinLockState
                }

                override suspend fun onSuccess() {
                    pinLockState = PinLockState.Active(
                        totalFailures = 0,
                        remainingInWindow = 0,
                        lockRemaining = Duration.ZERO,
                    )
                }

                override suspend fun clear() {
                    pinLockState = PinLockState.Active(
                        totalFailures = 0,
                        remainingInWindow = 0,
                        lockRemaining = Duration.ZERO,
                    )
                }
            }
        }
        single<EthManager> {
            object : EthManager {
                override suspend fun getBalance(wallet: EthWallet): Map<EthAddress, Result<BigInteger>> = emptyMap()

                override suspend fun fetchFeeSuggestion(): FeeSuggestion = throw NotImplementedError()

                override suspend fun sendTransfer(
                    privateKey: BigInteger,
                    contractAddress: EthAddress,
                    destinationAddress: EthAddress,
                    amount: BigInteger,
                    gasUnits: BigInteger,
                    maxPriorityFeePerGas: BigInteger,
                    maxFeePerGas: BigInteger,
                ): String = throw NotImplementedError()

                override fun createWallet(): EthCreationResult {
                    return EthCreationResult(
                        address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
                        privateKey = BigInteger.ONE,
                        seedPhrase = listOf(
                            "word1",
                            "word2",
                            "word3",
                            "word4",
                            "word5",
                            "word6",
                            "word7",
                            "word8",
                            "word9",
                            "word10",
                            "word11",
                            "word12",
                        ),
                    )
                }

                override fun importWallet(mnemonic: String): EthCreationResult = createWallet()
            }
        }
        single<PinCodeStorage> { pinCodeStorage }
        single<PrivateKeyEncryptor> {
            object : PrivateKeyEncryptor {
                override suspend fun encryptPrivateKey(privateKey: BigInteger, address: String): String {
                    return privateKey.toString(16)
                }

                override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): BigInteger {
                    return BigInteger(encryptedBase64, 16)
                }
            }
        }
        single<DataCleaner> {
            DataCleanerComposite(
                listOf(
                    DataCleanerRoomDb(get()),
                    object : DataCleaner {
                        override suspend fun clean() {
                            pinCodeStorage.clearPinCode()
                        }
                    },
                ),
            )
        }
        single<BiometricUnlock> {
            object : BiometricUnlock {
                override suspend fun status(): BiometricStatus = BiometricStatus.NotSupported

                override suspend fun enable(): BiometricResult = BiometricResult.Error("unsupported")

                override suspend fun disable() = Unit

                override suspend fun unlock(): BiometricResult = BiometricResult.Error("unsupported")
            }
        }
        factory<BalanceProvider> {
            object : BalanceProvider {
                override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> =
                    flowOf(
                        mapOf(
                            EthContractAddress to BigInteger.ZERO,
                            usdtOnSepolia.ethAddress to BigInteger.ZERO,
                            usdcOnSepolia.ethAddress to BigInteger.ZERO,
                            daiOnSepolia.ethAddress to BigInteger.ZERO,
                        ),
                    )

                override suspend fun forceUpdate(wallet: EthWallet) {}
            }
        }
        factory<FiatRateProvider> {
            object : FiatRateProvider {
                override fun observe(): Flow<Map<EthAddress, BigDecimal>> =
                    flowOf(
                        mapOf(
                            EthContractAddress to BigDecimal.ZERO,
                            usdtOnSepolia.ethAddress to BigDecimal.ONE,
                            usdcOnSepolia.ethAddress to BigDecimal.ONE,
                            daiOnSepolia.ethAddress to BigDecimal.ONE,
                        ),
                    )

                override suspend fun forceUpdate() {}
            }
        }
    }

    @Before
    fun setUp() {
        stopKoin()
        val chainConfig = ChainConfig(
            rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
            chainId = 11155111L,
            explorerBaseUrl = "https://sepolia.etherscan.io",
        )
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            allowOverride(true)
            modules(appModules(chainConfig) + mockModule)
        }

        // Clear all data to simulate a clean/fresh install setup
        runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                appDatabase.clearAllTables()
            }
            pinCodeStorage.clearPinCode()
        }

        // Launch activity manually after Koin is properly initialized with mocks
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        stopKoin()
    }

    @Test
    fun testWalletFlow() {
        // Step 1: Verification of Clean Setup is handled by setUp() clearing everything.

        // Step 2 & 3: Create first account & set PIN
        runAccountCreationAndPinSetupFlow()

        // Step 4: Logout (switch to account screen, verify single wallet, delete it)
        runLogoutAndDeletionFlow()

        // Step 5: Repeat flow (verifies that screen viewmodel state is clean/reset and wallets are not duplicated)
        runAccountCreationAndPinSetupFlow()
        runLogoutAndDeletionFlow()
    }

    @Test
    fun forgot_pin_logout_requires_confirmation_and_clears_local_data() {
        runAccountCreationAndPinSetupFlow()

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeTestRule.assertTextExists("Forgot PIN Code?")
        composeTestRule.clickText("Forgot PIN Code?")
        composeTestRule.assertTextExists("Log out and remove local data?")
        composeTestRule.assertTextExists("Confirm logout")
        composeTestRule.assertTextExists("Dismiss")

        composeTestRule.clickText("Dismiss")
        runBlocking {
            assertTrue(appDatabase.walletDao().getAllWallets().isNotEmpty())
            assertTrue(pinCodeStorage.hasPinCode())
        }

        composeTestRule.clickText("Forgot PIN Code?")
        composeTestRule.clickText("Confirm logout")

        composeTestRule.assertTextExists("Create new wallet")
        runBlocking {
            assertTrue(appDatabase.walletDao().getAllWallets().isEmpty())
            assertFalse(pinCodeStorage.hasPinCode())
        }
    }

    private fun runAccountCreationAndPinSetupFlow() {
        // 1. Click "Registration" (Create new wallet) button
        onboardingPage.clickCreateNewWallet()

        // 2. Click next button
        createWalletPage.clickNext()

        // 3. Confirm seed phrase has been saved
        createWalletPage.clickConfirm()

        // 4. Setup Pin Code (Set 112233)
        setPinCodePage.enterPin("112233")

        // 5. Confirm Pin Code (Confirm 112233)
        setPinCodePage.enterPin("112233")
    }

    private fun runLogoutAndDeletionFlow() {
        // 1. Go to the list of accounts
        walletPage.clickManageWallets()

        // 2. Ensure there is a single account
        walletsListPage.assertSingleWallet()

        // 3. Optional: Verify active checkmark is visible
        walletsListPage.assertActiveCheckmarkExists()

        // 4. Delete it
        walletsListPage.deleteActiveWallet()
    }
}
