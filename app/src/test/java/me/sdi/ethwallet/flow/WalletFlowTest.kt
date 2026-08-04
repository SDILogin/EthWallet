package me.sdi.ethwallet.flow

import android.app.Application
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.flow.pageobjects.CreateWalletPage
import me.sdi.ethwallet.flow.pageobjects.OnboardingPage
import me.sdi.ethwallet.flow.pageobjects.SetPinCodePage
import me.sdi.ethwallet.flow.pageobjects.WalletPage
import me.sdi.ethwallet.flow.pageobjects.WalletsListPage
import me.sdi.ethwallet.ui.EthWalletApp
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.compose.KoinContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = WalletFlowTestApplication::class)
class WalletFlowTest : KoinTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var activityController: ActivityController<WalletFlowTestActivity>

    private val appDatabase: AppDatabase by inject()

    private val onboardingPage = OnboardingPage(composeTestRule)
    private val createWalletPage = CreateWalletPage(composeTestRule)
    private val setPinCodePage = SetPinCodePage(composeTestRule)
    private val walletPage = WalletPage(composeTestRule)
    private val walletsListPage = WalletsListPage(composeTestRule)

    private val pinSession: PinSession by inject()

    /**
     * Mirrors the real storage on the one point the lock depends on: storing or verifying a PIN
     * publishes the master key to the session, and clearing the PIN drops it.
     */
    private val pinCodeStorage = object : PinCodeStorage {
        var storedPin: String? = null

        override suspend fun storePinCode(pinCode: String) {
            storedPin = pinCode
            pinSession.set(ByteArray(32) { 7 })
        }

        override suspend fun verifyPinCode(pinCode: String): Boolean {
            if (storedPin != pinCode) {
                return false
            }

            pinSession.set(ByteArray(32) { 7 })
            return true
        }

        override suspend fun hasPinCode(): Boolean = storedPin != null

        override suspend fun clearPinCode() {
            storedPin = null
            pinSession.clear()
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
                override suspend fun getBalance(address: EthAddress): Map<EthAddress, Result<BigInteger>> = emptyMap()

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

                override suspend fun forceUpdate(address: EthAddress) {}
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
        val application = RuntimeEnvironment.getApplication()
        stopKoin()
        val chainConfig = ChainConfig(
            rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
            chainId = 11155111L,
            explorerBaseUrl = "https://sepolia.etherscan.io",
        )
        startKoin {
            androidContext(application)
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
        activityController = Robolectric.buildActivity(WalletFlowTestActivity::class.java).setup()
    }

    @After
    fun tearDown() {
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        stopKoin()
    }

    @Test
    fun walletCreationReturnsToWalletList() {
        runInitialWalletCreationFlow()
        walletsListPage.assertSingleWallet()
        walletsListPage.assertSelectedIndicatorIsHidden()
    }

    @Test
    fun walletLabelCanBeRenamedFromWalletOptions() {
        runInitialWalletCreationFlow()

        walletsListPage.renameWallet(currentLabel = "My Wallet", newLabel = "Daily spending")

        walletsListPage.assertWalletOptions("Daily spending")
    }

    @Test
    fun tappingWalletOpensDetailsAndUpReturnsToList() {
        runInitialWalletCreationFlow()

        walletsListPage.clickWallet("My Wallet")
        walletPage.awaitVisible()
        walletPage.clickUp()

        walletsListPage.assertSingleWallet()
    }

    @Test
    fun emptyWalletsListDoesNotShowAddWalletFab() {
        onboardingPage.clickCreatePinCode()
        setPinCodePage.enterPin(PIN)
        setPinCodePage.enterPin(PIN)

        walletsListPage.assertAddWalletIsHidden()
    }

    @Test
    fun addWalletFabOpensCreateWalletScreen() {
        runInitialWalletCreationFlow()
        showWalletsList()

        walletsListPage.openAddWalletMenu()
        walletsListPage.assertAddWalletActions()
        walletsListPage.clickAddWalletCreate()

        composeTestRule.assertTextExists("Create Wallet")
    }

    @Test
    fun addWalletFabOpensImportWalletScreen() {
        runInitialWalletCreationFlow()
        showWalletsList()

        walletsListPage.openAddWalletMenu()
        walletsListPage.assertAddWalletActions()
        walletsListPage.clickAddWalletImport()

        composeTestRule.assertTextExists("Enter Recovery Phrase")
    }

    @Test
    fun forgot_pin_logout_requires_confirmation_and_clears_local_data() {
        runInitialWalletCreationFlow()

        restartProcess()

        composeTestRule.assertTextExists("Forgot PIN Code?")
        composeTestRule.clickText("Forgot PIN Code?")
        composeTestRule.assertTextExists("Log out and remove local data?")
        composeTestRule.assertTextExists("Confirm logout")
        composeTestRule.assertTextExists("Dismiss")

        composeTestRule.clickText("Dismiss")
        runBlocking {
            assertTrue(appDatabase.walletDao().observeAllWallets().first().isNotEmpty())
            assertTrue(pinCodeStorage.hasPinCode())
        }

        composeTestRule.clickText("Forgot PIN Code?")
        composeTestRule.clickText("Confirm logout")

        composeTestRule.waitUntilTextExists("Create PIN Code")
        runBlocking {
            assertTrue(appDatabase.walletDao().observeAllWallets().first().isEmpty())
            assertFalse(pinCodeStorage.hasPinCode())
        }
    }

    @Test
    fun lock_screen_still_accepts_the_pin_after_a_logout_and_a_new_wallet() {
        runInitialWalletCreationFlow()

        lockApp()
        composeTestRule.clickText("Forgot PIN Code?")
        composeTestRule.clickText("Confirm logout")
        composeTestRule.assertTextExists("Create PIN Code")

        runInitialWalletCreationFlow()

        lockApp()
        setPinCodePage.enterPin(PIN)

        walletsListPage.assertSingleWallet()
        walletsListPage.assertSelectedIndicatorIsHidden()
    }

    /**
     * Drops the in-memory master key the way process death does, which is the only thing that
     * decides whether the app is locked.
     */
    private fun lockApp() {
        walletsListPage.clickWallet("My Wallet")
        walletPage.awaitVisible()
        composeTestRule.runOnUiThread { pinSession.clear() }
        composeTestRule.waitUntilTextExists("Forgot PIN Code?")
    }

    private fun restartProcess() {
        activityController.pause().stop().destroy()
        pinSession.clear()
        activityController = Robolectric.buildActivity(WalletFlowTestActivity::class.java).setup()
    }

    private fun showWalletsList() {
        walletsListPage.assertSingleWallet()
    }

    private fun runInitialWalletCreationFlow() {
        onboardingPage.clickCreatePinCode()
        setPinCodePage.enterPin(PIN)
        setPinCodePage.enterPin(PIN)

        runWalletCreationFlow()
    }

    private fun runWalletCreationFlow() {
        walletsListPage.clickCreateWallet()
        createWalletPage.enterWalletName("My Wallet")
        createWalletPage.clickNext()
        createWalletPage.clickConfirm()
    }
}

private const val PIN = "112233"

class WalletFlowTestApplication : Application()

class WalletFlowTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            @Suppress("DEPRECATION")
            KoinContext(koin = GlobalContext.get()) {
                EthWalletApp()
            }
        }
    }
}
