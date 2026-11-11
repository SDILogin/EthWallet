package me.sdi.ethwallet.flow

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.flow.pageobjects.TokensListPage
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import me.sdi.ethwallet.ui.wallet.TokensListScreen
import me.sdi.ethwallet.ui.wallet.TokensListViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = WalletFlowTestApplication::class)
class TokensListScreenTest {
    @get:Rule
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    private val tokensListPage = TokensListPage(composeTestRule)
    private lateinit var activityController: ActivityController<TokensListScreenTestActivity>
    private var upClickCount = 0
    private val walletAddress = EthAddress("0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")
    private val tokenPreferenceRepository = TokenPreferenceRepositoryInMemory()

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    viewModel { params ->
                        TokensListViewModel(
                            walletAddress = params.get(),
                            tokenPreferenceRepository = tokenPreferenceRepository,
                            chainConfig = ChainConfig(
                                rpcUrl = "https://rpc.example",
                                chainId = ETH_SEPOLIA_CHAIN_ID,
                                explorerBaseUrl = "https://explorer.example",
                                transactionHistoryApiBaseUrl = "https://history.example",
                            ),
                        )
                    }
                },
            )
        }
        launchScreen()
    }

    private fun launchScreen() {
        activityController = Robolectric.buildActivity(TokensListScreenTestActivity::class.java).setup()
        activityController.get().setContent {
            @Suppress("DEPRECATION")
            KoinContext(koin = GlobalContext.get()) {
                MaterialTheme {
                    TokensListScreen(walletAddress = walletAddress, onBackPressed = { upClickCount += 1 })
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        stopKoin()
    }

    @Test
    fun toggling_token_updates_checked_state_without_changing_other_tokens() {
        tokensListPage.assertTokenChecked("USDT", checked = true)
        tokensListPage.assertTokenChecked("USDC", checked = true)
        tokensListPage.assertTokenChecked("DAI", checked = true)

        tokensListPage.toggleToken("USDC")

        tokensListPage.assertTokenChecked("USDC", checked = false)
        tokensListPage.assertTokenChecked("USDT", checked = true)
        tokensListPage.assertTokenChecked("DAI", checked = true)

        tokensListPage.toggleToken("USDC")

        tokensListPage.assertTokenChecked("USDC", checked = true)
        tokensListPage.assertTokenChecked("USDT", checked = true)
        tokensListPage.assertTokenChecked("DAI", checked = true)
        assertEquals(
            listOf(usdtOnSepolia, usdcOnSepolia, daiOnSepolia).map { TokenPreference(it.ethAddress, visible = true) },
            storedPreferences(),
        )
    }

    @Test
    fun dragging_token_down_and_back_up_updates_order_and_preserves_checked_state() {
        tokensListPage.toggleToken("USDT")
        tokensListPage.assertTokenOrder("USDT", "USDC", "DAI")

        tokensListPage.dragTokenTo("USDT", "DAI")

        tokensListPage.assertTokenOrder("USDC", "DAI", "USDT")
        tokensListPage.assertTokenChecked("USDT", checked = false)
        tokensListPage.assertTokenChecked("USDC", checked = true)
        tokensListPage.assertTokenChecked("DAI", checked = true)

        tokensListPage.dragTokenTo("USDT", "USDC")

        tokensListPage.assertTokenOrder("USDT", "USDC", "DAI")
        tokensListPage.assertTokenChecked("USDT", checked = false)
        assertEquals(
            listOf(
                TokenPreference(usdtOnSepolia.ethAddress, visible = false),
                TokenPreference(usdcOnSepolia.ethAddress, visible = true),
                TokenPreference(daiOnSepolia.ethAddress, visible = true),
            ),
            storedPreferences(),
        )
    }

    @Test
    fun stored_preferences_are_shown_on_open() {
        activityController.pause().stop().destroy()
        runBlocking {
            tokenPreferenceRepository.update(
                walletAddress,
                listOf(
                    TokenPreference(daiOnSepolia.ethAddress, visible = false),
                    TokenPreference(usdtOnSepolia.ethAddress, visible = true),
                    TokenPreference(usdcOnSepolia.ethAddress, visible = true),
                ),
            )
        }
        launchScreen()

        tokensListPage.assertTokenOrder("DAI", "USDT", "USDC")
        tokensListPage.assertTokenChecked("DAI", checked = false)
    }

    private fun storedPreferences() = composeTestRule.runOnIdle {
        runBlocking { tokenPreferenceRepository.observe(walletAddress).first() }
    }

    @Test
    fun clicking_up_calls_back_callback_once() {
        composeTestRule.runOnIdle { assertEquals(0, upClickCount) }

        tokensListPage.clickUp()

        composeTestRule.runOnIdle { assertEquals(1, upClickCount) }
    }
}

private class TokensListScreenTestActivity : FragmentActivity()
