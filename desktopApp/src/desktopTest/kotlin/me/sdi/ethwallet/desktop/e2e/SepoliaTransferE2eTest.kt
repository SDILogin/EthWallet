package me.sdi.ethwallet.desktop.e2e

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.desktop.e2e.environment.SepoliaE2eSeeds
import me.sdi.ethwallet.desktop.e2e.pageobjects.ImportWalletPage
import me.sdi.ethwallet.desktop.e2e.pageobjects.OnboardingPage
import me.sdi.ethwallet.desktop.e2e.pageobjects.SendPage
import me.sdi.ethwallet.desktop.e2e.pageobjects.SetPinCodePage
import me.sdi.ethwallet.desktop.e2e.pageobjects.WalletDashboardPage
import me.sdi.ethwallet.desktop.e2e.pageobjects.WalletsListPage
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import java.math.BigDecimal

internal interface SepoliaE2e

class SepoliaTransferE2eTest {
    private val temporaryFolder = TemporaryFolder()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(temporaryFolder)
        .around(object : ExternalResource() {
            override fun after() {
                dataStoreScope.cancel()
            }
        })
        .around(composeTestRule)

    private val onboardingPage = OnboardingPage(composeTestRule)
    private val setPinCodePage = SetPinCodePage(composeTestRule)
    private val walletsListPage = WalletsListPage(composeTestRule)
    private val importWalletPage = ImportWalletPage(composeTestRule)
    private val walletDashboardPage = WalletDashboardPage(composeTestRule)
    private val sendPage = SendPage(composeTestRule)
    private lateinit var seeds: SepoliaE2eSeeds

    @Before
    fun setUp() {
        seeds = SepoliaE2eSeeds.load()
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.root.resolve("pin.preferences_pb")
        }
        val testStorage = module {
            single<DataStore<Preferences>> { dataStore }
            single<CoroutineScope> { dataStoreScope }
        }
        val chainConfig = ChainConfig(
            rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
            chainId = 11155111L,
            explorerBaseUrl = "https://sepolia.etherscan.io",
            transactionHistoryApiBaseUrl = "https://eth-sepolia.blockscout.com",
        )
        composeTestRule.setContent {
            KoinApplication(configuration = koinConfiguration { modules(appModules(chainConfig) + testStorage) }) {
                EthWalletApp()
            }
        }
    }

    @Test
    @Category(SepoliaE2e::class)
    fun restoredAccountsCanTransferEthBetweenEachOther() {
        onboardingPage.assertShown()
        onboardingPage.clickCreatePinCodeButton()

        setPinCodePage.assertCreatePinShown()
        setPinCodePage.inputPin(TEST_PIN)
        setPinCodePage.assertConfirmPinShown()
        setPinCodePage.confirmPin(TEST_PIN)
        setPinCodePage.dismissBiometricOfferIfShown()

        walletsListPage.assertWalletsListShown()
        walletsListPage.clickImportWalletButton()
        importWalletPage.assertShown()
        importWalletPage.inputWalletLabel(SENDER_LABEL)
        importWalletPage.inputSeedPhrase(seeds.seed1)
        importWalletPage.clickRestoreButton()

        walletsListPage.assertWalletsListShown()
        walletsListPage.assertWalletShown(SENDER_LABEL)
        walletsListPage.clickAddWalletButton()
        walletsListPage.clickImportWalletMenuItem()
        importWalletPage.assertShown()
        importWalletPage.inputWalletLabel(RECEIVER_LABEL)
        importWalletPage.inputSeedPhrase(seeds.seed2)
        importWalletPage.clickRestoreButton()

        walletsListPage.assertWalletsListShown()
        walletsListPage.assertWalletShown(SENDER_LABEL)
        walletsListPage.assertWalletShown(RECEIVER_LABEL)

        walletsListPage.openWallet(RECEIVER_LABEL)
        walletDashboardPage.assertShown(RECEIVER_LABEL)
        walletDashboardPage.assertTestnetShown()
        val receiverBalanceBefore = walletDashboardPage.readEthBalance()
        walletDashboardPage.clickBackButton()

        walletsListPage.assertWalletsListShown()
        walletsListPage.openWallet(SENDER_LABEL)
        walletDashboardPage.assertShown(SENDER_LABEL)
        walletDashboardPage.assertTestnetShown()
        walletDashboardPage.clickSendButton()

        sendPage.assertShown()
        sendPage.assertEthSelected()
        sendPage.clickSelectWalletButton()
        sendPage.selectWallet(RECEIVER_LABEL)
        sendPage.inputEthAmount(TRANSFER_AMOUNT.toPlainString())
        sendPage.assertDefaultFeesSelectedAndReady()
        sendPage.clickSendButton()
        sendPage.awaitTransferFormCleared(timeoutMillis = TRANSFER_TIMEOUT_MILLIS)
        sendPage.clickBackButton()

        walletDashboardPage.assertShown(SENDER_LABEL)
        walletDashboardPage.clickBackButton()
        walletsListPage.assertWalletsListShown()
        walletsListPage.openWallet(RECEIVER_LABEL)
        walletDashboardPage.assertShown(RECEIVER_LABEL)
        walletDashboardPage.assertTestnetShown()
        walletDashboardPage.awaitEthBalanceAtLeast(receiverBalanceBefore + TRANSFER_AMOUNT)
    }

    private companion object {
        const val TEST_PIN = "112233"
        const val SENDER_LABEL = "E2E Sender"
        const val RECEIVER_LABEL = "E2E Receiver"
        const val TRANSFER_TIMEOUT_MILLIS = 120_000L
        val TRANSFER_AMOUNT: BigDecimal = BigDecimal("0.000001")
    }
}
