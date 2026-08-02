package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import java.math.BigInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWalletViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var walletDao: FakeWalletDao
    private lateinit var ethManager: FakeEthManager
    private lateinit var privateKeyEncryptor: FakePrivateKeyEncryptor
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: CreateWalletViewModel

    private val testSeedPhrase =
        listOf(
            "abandon",
            "ability",
            "able",
            "about",
            "above",
            "absent",
            "absorb",
            "abstract",
            "absurd",
            "abuse",
            "access",
            "accident",
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletDao = FakeWalletDao()
        ethManager =
            FakeEthManager().also {
                it.creationResult =
                    EthCreationResult(
                        address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
                        privateKey = BigInteger.ONE,
                        seedPhrase = testSeedPhrase,
                    )
            }
        privateKeyEncryptor = FakePrivateKeyEncryptor()
        walletRepository = WalletRepository(walletDao, ethManager, privateKeyEncryptor)
        viewModel = CreateWalletViewModel(ethManager, walletRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationGeneratesSeedPhraseWithoutPersistingWallet() = runTest(testDispatcher) {
        assertEquals(testSeedPhrase, awaitSeedPhrase())
        assertTrue(walletDao.wallets.isEmpty())
        assertNull(walletDao.currentWalletAddress)
    }

    @Test
    fun confirmingSeedPhrasePersistsWalletAndShowsCreatedNews() = runTest(testDispatcher) {
        awaitSeedPhrase()

        viewModel.onEvent(CreateWalletEvent.SeedPhraseSaved)

        assertEquals(CreateWalletNews.WalletCreated, withTimeout(5.seconds) { viewModel.news.first() })
        assertEquals(
            listOf(
                WalletEntity(
                    address = ethManager.creationResult.address.toString(),
                    encryptedPrivateKeyBase64 = "encrypted_1",
                    label = "My Wallet",
                ),
            ),
            walletDao.wallets,
        )
        assertEquals(ethManager.creationResult.address.toString(), walletDao.currentWalletAddress)
    }

    @Test
    fun initializationPreservesGeneratedSeedPhrase() = runTest(testDispatcher) {
        val seedPhrase = listOf("test", "seed", "phrase", "words")
        ethManager.creationResult = ethManager.creationResult.copy(seedPhrase = seedPhrase)
        viewModel = CreateWalletViewModel(ethManager, walletRepository)

        assertEquals(seedPhrase, awaitSeedPhrase())
    }

    private suspend fun awaitSeedPhrase(): List<String> =
        withTimeout(5.seconds) {
            viewModel.viewState
                .filter { it.seedPhrase.isNotEmpty() }
                .first()
                .seedPhrase
        }
}
