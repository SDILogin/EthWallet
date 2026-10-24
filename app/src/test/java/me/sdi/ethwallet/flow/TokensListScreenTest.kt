package me.sdi.ethwallet.flow

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.fragment.app.FragmentActivity
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.flow.pageobjects.TokensListPage
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
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

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    viewModel {
                        TokensListViewModel(
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
        activityController = Robolectric.buildActivity(TokensListScreenTestActivity::class.java).setup()
        activityController.get().setContent {
            @Suppress("DEPRECATION")
            KoinContext(koin = GlobalContext.get()) {
                MaterialTheme {
                    TokensListScreen(onBackPressed = { upClickCount += 1 })
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
    }

    @Test
    fun clicking_up_calls_back_callback_once() {
        composeTestRule.runOnIdle { assertEquals(0, upClickCount) }

        tokensListPage.clickUp()

        composeTestRule.runOnIdle { assertEquals(1, upClickCount) }
    }
}

private class TokensListScreenTestActivity : FragmentActivity()
