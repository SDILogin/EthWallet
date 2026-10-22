package me.sdi.ethwallet.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

class DesktopAppTest {
    private val temporaryFolder = TemporaryFolder()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(temporaryFolder)
        .around(object : ExternalResource() {
            override fun after() {
                dataStoreScope.cancel()
            }
        })
        .around(compose)

    @Test
    fun newUserCanOpenPinSetup() {
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.root.resolve("pin.preferences_pb")
        }
        val testStorage = module {
            single<DataStore<Preferences>> { dataStore }
        }
        val chainConfig = ChainConfig(
            rpcUrl = "http://127.0.0.1:1",
            chainId = 11155111L,
            explorerBaseUrl = null,
            transactionHistoryApiBaseUrl = "http://127.0.0.1:1",
        )

        compose.setContent {
            KoinApplication(configuration = koinConfiguration { modules(appModules(chainConfig) + testStorage) }) {
                EthWalletApp()
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("ETH Wallet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ETH Wallet").assertIsDisplayed()
        compose.onNodeWithText("Create PIN Code").assertIsDisplayed().performClick()
        compose.onNodeWithText("Create PIN Code").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }
}
