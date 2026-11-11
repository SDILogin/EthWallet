package me.sdi.ethwallet.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.sdi.ethwallet.core.BalanceProvider
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.FiatRateProvider
import me.sdi.ethwallet.core.security.MasterKekProtection
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.ui.EthWalletApp
import me.sdi.ethwallet.ui.usdtOnSepolia
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import java.math.BigDecimal
import java.math.BigInteger

class DesktopWalletSelectionTest {
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
    fun indicatorStaysOnActiveWalletInTokenDetailsAndMovesToAnotherSelectedWallet() {
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.root.resolve("pin.preferences_pb")
        }
        val database = DesktopDatabaseBuilder.build(temporaryFolder.newFolder("database"))
        runBlocking {
            database.walletDao().insertWallet(
                WalletEntity(address = MAIN_ADDRESS, encryptedPrivateKeyBase64 = "private-key", label = "Main"),
            )
            database.walletDao().insertWallet(
                WalletEntity(address = SAVINGS_ADDRESS, encryptedPrivateKeyBase64 = "private-key", label = "Savings"),
            )
        }
        val testModule = module {
            single<DataStore<Preferences>> { dataStore }
            single<AppDatabase> { database }
            single<MasterKekProtection> { FakeMasterKekProtection() }
            factory<BalanceProvider> {
                object : BalanceProvider {
                    override fun observe(address: EthAddress): Flow<Map<EthAddress, BigInteger>> =
                        flowOf(mapOf(EthContractAddress to BigInteger.ZERO, usdtOnSepolia.ethAddress to BigInteger.ZERO))

                    override suspend fun forceUpdate(address: EthAddress) = Unit
                }
            }
            factory<FiatRateProvider> {
                object : FiatRateProvider {
                    override fun observe(): Flow<Map<EthAddress, BigDecimal>> =
                        flowOf(mapOf(EthContractAddress to BigDecimal.ZERO, usdtOnSepolia.ethAddress to BigDecimal.ONE))

                    override suspend fun forceUpdate() = Unit
                }
            }
        }
        val chainConfig = ChainConfig(
            rpcUrl = "http://127.0.0.1:1",
            chainId = 11155111L,
            explorerBaseUrl = null,
            transactionHistoryApiBaseUrl = "http://127.0.0.1:1",
        )

        compose.setContent {
            KoinApplication(configuration = koinConfiguration { modules(appModules(chainConfig) + testModule) }) {
                EthWalletApp()
            }
        }

        awaitText("ETH Wallet")
        compose.onNodeWithText("Create password").performClick()
        awaitText("Create a wallet password")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput(PASSWORD)
        compose.onNode(hasSetTextAction() and hasText("Confirm password")).performTextInput(PASSWORD)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("Continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Continue").performClick()
        awaitText("No wallet has been selected yet")
        awaitText("Savings")

        compose.onNodeWithText("Main").performClick()
        awaitText(usdtOnSepolia.tokenName)
        assertSelectedWallet("Main")

        compose.onNodeWithText(usdtOnSepolia.tokenName).performClick()
        awaitText("Token information")
        assertSelectedWallet("Main")

        compose.onNodeWithText("Savings").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Token information").fetchSemanticsNodes().isEmpty()
        }
        assertSelectedWallet("Savings")
    }

    private fun assertSelectedWallet(label: String) {
        compose.onAllNodesWithContentDescription(SELECTED_WALLET).assertCountEquals(1)
        compose.onNode(hasText(label) and hasContentDescription(SELECTED_WALLET)).assertExists()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private const val PASSWORD = "correct horse battery staple"
private const val SELECTED_WALLET = "Selected wallet"
private const val MAIN_ADDRESS = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
private const val SAVINGS_ADDRESS = "0x9876543210987654321098765432109876543210"
