package me.sdi.ethwallet.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.security.MasterKekProtection
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
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
    fun newUserSetsAWalletPasswordAndUnlocksWithIt() {
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.root.resolve("pin.preferences_pb")
        }
        val databaseDirectory = temporaryFolder.newFolder("database")
        val testStorage = module {
            single<DataStore<Preferences>> { dataStore }
            single<AppDatabase> { DesktopDatabaseBuilder.build(databaseDirectory) }
            single<MasterKekProtection> { FakeMasterKekProtection() }
        }
        val chainConfig = ChainConfig(
            rpcUrl = "http://127.0.0.1:1",
            chainId = 11155111L,
            explorerBaseUrl = null,
            transactionHistoryApiBaseUrl = "http://127.0.0.1:1",
        )
        lateinit var pinSession: PinSession

        compose.setContent {
            KoinApplication(configuration = koinConfiguration { modules(appModules(chainConfig) + testStorage) }) {
                pinSession = koinInject()
                EthWalletApp()
            }
        }

        awaitText("ETH Wallet")
        compose.onNodeWithText("Create password").assertIsDisplayed().performClick()
        awaitText("Create a wallet password")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput(PASSWORD)
        compose.onNode(hasSetTextAction() and hasText("Confirm password")).performTextInput(PASSWORD)
        compose.onNodeWithText("Continue").performClick()
        awaitText("My Wallets")

        compose.runOnIdle { pinSession.clear() }
        awaitText("Enter your wallet password")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("wrong password")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasSetTextAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertFalse("A wrong password must keep the app locked", pinSession.isUnlocked.value)
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(PASSWORD)
        compose.onNodeWithText("Unlock").performClick()
        awaitText("My Wallets")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        private const val PASSWORD = "correct horse battery staple"
    }
}
