package me.sdi.ethwallet.flow.e2e

import android.os.Build
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.flow.WalletFlowTestActivity
import me.sdi.ethwallet.flow.e2e.environment.SepoliaE2e
import me.sdi.ethwallet.flow.e2e.environment.SepoliaE2eSeeds
import me.sdi.ethwallet.flow.e2e.environment.SepoliaE2eTestApplication
import me.sdi.ethwallet.flow.e2e.environment.startSepoliaE2eKoin
import me.sdi.ethwallet.flow.e2e.environment.stopSepoliaE2eKoin
import me.sdi.ethwallet.flow.e2e.pageobjects.ImportWalletPage
import me.sdi.ethwallet.flow.e2e.pageobjects.OnboardingPage
import me.sdi.ethwallet.flow.e2e.pageobjects.SendPage
import me.sdi.ethwallet.flow.e2e.pageobjects.SetPinCodePage
import me.sdi.ethwallet.flow.e2e.pageobjects.WalletDashboardPage
import me.sdi.ethwallet.flow.e2e.pageobjects.WalletsListPage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [Build.VERSION_CODES.P],
    application = SepoliaE2eTestApplication::class,
)
class SepoliaTransferE2eTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val onboardingPage = OnboardingPage(composeTestRule)
    private val setPinCodePage = SetPinCodePage(composeTestRule)
    private val walletsListPage = WalletsListPage(composeTestRule)
    private val importWalletPage = ImportWalletPage(composeTestRule)
    private val walletDashboardPage = WalletDashboardPage(composeTestRule)
    private val sendPage = SendPage(composeTestRule)

    private lateinit var activityController: ActivityController<WalletFlowTestActivity>
    private lateinit var seeds: SepoliaE2eSeeds
    private var koinStarted = false

    @Before
    fun setUp() {
        seeds = SepoliaE2eSeeds.load()
        startSepoliaE2eKoin(RuntimeEnvironment.getApplication())
        koinStarted = true
        Logger.enabled = true
        Logger.printer = { level, tag, msg -> println("[${level.name.first()}] $tag: $msg") }
        activityController = Robolectric.buildActivity(WalletFlowTestActivity::class.java).setup()
    }

    @After
    fun tearDown() {
        Logger.enabled = false
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        if (koinStarted) {
            stopSepoliaE2eKoin()
            koinStarted = false
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
