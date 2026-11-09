package me.sdi.ethwallet.flow

import android.app.Application
import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import me.sdi.ethwallet.core.security.MasterKekStorage
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.core.security.PinSessionDataCleaner
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.core.transfer.FeeSuggestion
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.flow.pageobjects.CreateWalletPage
import me.sdi.ethwallet.flow.pageobjects.OnboardingPage
import me.sdi.ethwallet.flow.pageobjects.SetPinCodePage
import me.sdi.ethwallet.flow.pageobjects.WalletPage
import me.sdi.ethwallet.flow.pageobjects.WalletsListPage
import me.sdi.ethwallet.kotlin.PrivateKey
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
import kotlin.test.assertEquals
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
    private val sensitiveData = SemanticsMatcher.expectValue(SemanticsProperties.IsSensitiveData, true)

    private val pinSession: PinSession by inject()

    private val pinCodeStorage = object : PinCodeStorage {
        var storedPin: String? = null

        override suspend fun storePinCode(pinCode: String) {
            storedPin = pinCode
        }

        override suspend fun verifyPinCode(pinCode: String): Boolean = storedPin == pinCode

        override suspend fun hasPinCode(): Boolean = storedPin != null

        override suspend fun changePinCode(newPin: String) {
            storedPin = newPin
        }

        override suspend fun clearPinCode() {
            storedPin = null
        }
    }

    private val masterKekStorage = object : MasterKekStorage {
        var storedKek: ByteArray? = null

        override suspend fun create(): ByteArray = ByteArray(32) { 7 }.also { storedKek = it.copyOf() }

        override suspend fun read(): ByteArray = checkNotNull(storedKek).copyOf()

        override suspend fun clear() {
            storedKek = null
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
                    privateKey: PrivateKey,
                    contractAddress: EthAddress,
                    destinationAddress: EthAddress,
                    amount: BigInteger,
                    gasUnits: BigInteger,
                    maxPriorityFeePerGas: BigInteger,
                    maxFeePerGas: BigInteger,
                ): String = throw NotImplementedError()

                override suspend fun createWallet(): EthCreationResult {
                    return EthCreationResult(
                        address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
                        privateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 1 }),
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

                override suspend fun importWallet(mnemonic: String): EthCreationResult = createWallet()
            }
        }
        single<PinCodeStorage> { pinCodeStorage }
        single<MasterKekStorage> { masterKekStorage }
        single<PrivateKeyEncryptor> {
            object : PrivateKeyEncryptor {
                override suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String {
                    return privateKey.copyBytes().joinToString("") { "%02x".format(it) }
                }

                override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): PrivateKey {
                    val bytes = ByteArray(32)
                    val decoded = encryptedBase64.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    decoded.copyInto(bytes, bytes.size - decoded.size)
                    return PrivateKey.fromBytes(bytes)
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
                            masterKekStorage.clear()
                        }
                    },
                    PinSessionDataCleaner(get()),
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
            transactionHistoryApiBaseUrl = "https://eth-sepolia.blockscout.com",
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
            masterKekStorage.clear()
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
    fun sendScreenSelectsAnOwnedDestinationWallet() {
        runInitialWalletCreationFlow()
        val savingsAddress = "0x9876543210987654321098765432109876543210"
        runBlocking {
            appDatabase.walletDao().insertWallet(
                WalletEntity(
                    address = savingsAddress,
                    encryptedPrivateKeyBase64 = "private-key",
                    label = "Savings",
                ),
            )
        }

        walletsListPage.clickWallet("My Wallet")
        walletPage.awaitVisible()
        walletPage.clickSend()

        composeTestRule.waitUntilTextExists("Receiver Address")
        composeTestRule.clickContentDescription("Select wallet")
        composeTestRule.waitUntilTextExists("My wallets")
        composeTestRule.assertTextExists("Savings")
        composeTestRule.assertTextExists(savingsAddress)
        composeTestRule.clickText("Savings")

        composeTestRule.assertTextExists(savingsAddress)
    }

    @Test
    fun sendScreenDisablesOwnedWalletPickerWhenNoOtherWalletExists() {
        runInitialWalletCreationFlow()

        walletsListPage.clickWallet("My Wallet")
        walletPage.awaitVisible()
        walletPage.clickSend()

        composeTestRule.waitUntilTextExists("Receiver Address")
        composeTestRule.onNodeWithContentDescription("No other wallets").assertIsNotEnabled()
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
    fun recoveryPhraseFieldDisablesKeyboardLearningAndKeepsTextVisible() {
        runInitialWalletCreationFlow()
        showWalletsList()
        walletsListPage.openAddWalletMenu()
        walletsListPage.clickAddWalletImport()

        val recoveryPhraseField = composeTestRule.onNode(hasText("Recovery Phrase") and hasSetTextAction())
        recoveryPhraseField.performClick()
        composeTestRule.waitForIdle()
        val rootView = (recoveryPhraseField.fetchSemanticsNode().root as ViewRootForTest).view
        val editorInfo = EditorInfo()
        composeTestRule.runOnIdle { rootView.onCreateInputConnection(editorInfo) }

        assertEquals(
            InputType.TYPE_CLASS_TEXT,
            editorInfo.inputType and InputType.TYPE_MASK_CLASS,
            "Recovery phrase field should report a text input class",
        )
        assertEquals(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            editorInfo.inputType and InputType.TYPE_MASK_VARIATION,
            "Recovery phrase field should report a password variation",
        )
        assertEquals(
            0,
            editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
            "Recovery phrase field should disable autocorrect",
        )
        assertEquals(
            EditorInfo.IME_ACTION_DONE,
            editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION,
            "Recovery phrase field should keep the Done action",
        )
        assertEquals(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            "Recovery phrase field should ask the keyboard not to learn typed words",
        )

        recoveryPhraseField.performTextInput(VISIBLE_RECOVERY_PHRASE)

        val semantics = recoveryPhraseField.fetchSemanticsNode().config
        assertEquals(
            VISIBLE_RECOVERY_PHRASE,
            semantics[SemanticsProperties.EditableText].text,
            "Recovery phrase should stay readable after typing",
        )
        assertFalse(SemanticsProperties.Password in semantics, "Recovery phrase should not be masked")
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

    @Test
    fun setPinScreenMarksOnlyPinInputAsSensitive() {
        onboardingPage.clickCreatePinCode()
        composeTestRule.waitUntilTextExists("1")

        assertOnlyPinInputIsSensitive()
    }

    @Test
    fun verifyPinScreenMarksOnlyPinInputAsSensitive() {
        runInitialWalletCreationFlow()

        lockApp()

        assertOnlyPinInputIsSensitive()
    }

    @Test
    fun changePinScreenMarksOnlyPinInputAsSensitive() {
        runInitialWalletCreationFlow()
        showWalletsList()

        composeTestRule.clickContentDescription("Settings")
        composeTestRule.waitUntilTextExists("Change PIN")
        composeTestRule.clickText("Change PIN")
        composeTestRule.waitUntilTextExists("1")

        assertOnlyPinInputIsSensitive()
    }

    @Test
    fun createWalletScreenMarksRecoveryPhraseAsSensitive() {
        onboardingPage.clickCreatePinCode()
        setPinCodePage.enterPin(PIN)
        setPinCodePage.enterPin(PIN)
        walletsListPage.clickCreateWallet()

        val recoveryPhrase = hasContentDescription("12 words mnemonic", substring = true)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(recoveryPhrase).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(recoveryPhrase).assert(sensitiveData)
    }

    @Test
    fun importWalletScreenMarksOnlyRecoveryPhraseInputAsSensitive() {
        runInitialWalletCreationFlow()
        showWalletsList()

        walletsListPage.openAddWalletMenu()
        walletsListPage.clickAddWalletImport()
        composeTestRule.waitUntilTextExists("Enter Recovery Phrase")

        composeTestRule.onAllNodes(hasSetTextAction() and sensitiveData, useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onNodeWithTag("wallet-label-input")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.IsSensitiveData))
    }

    private fun assertOnlyPinInputIsSensitive() {
        composeTestRule.onNodeWithContentDescription("0 of 6 digits entered").assert(sensitiveData)
        composeTestRule.onAllNodes(hasClickAction() and sensitiveData, useUnmergedTree = true).assertCountEquals(0)
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
private const val VISIBLE_RECOVERY_PHRASE = "abandon ability able"

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
