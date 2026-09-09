package me.sdi.ethwallet.ui.wallet

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthCreationResult
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.fakes.FakeEthManager
import me.sdi.ethwallet.fakes.FakePrivateKeyEncryptor
import me.sdi.ethwallet.fakes.FakeWalletDao
import me.sdi.ethwallet.fakes.HistoryInitializationStoreInMemory
import me.sdi.ethwallet.kotlin.PrivateKey
import me.sdi.ethwallet.kotlin.deriveAddress
import java.math.BigInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    private lateinit var viewModelStore: ViewModelStore

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
                        privateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 1 }),
                        seedPhrase = testSeedPhrase,
                    )
            }
        privateKeyEncryptor = FakePrivateKeyEncryptor()
        walletRepository = WalletRepository(
            walletDao = walletDao,
            ethManager = ethManager,
            privateKeyEncryptor = privateKeyEncryptor,
            historyInitializationStore = HistoryInitializationStoreInMemory(),
        )
        createViewModel()
    }

    @AfterTest
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun initializationGeneratesSeedPhraseWithoutPersistingWallet() = runTest(testDispatcher) {
        assertEquals(testSeedPhrase, awaitSeedPhrase())
        assertTrue(walletDao.wallets.isEmpty())
    }

    @Test
    fun confirmingSeedPhrasePersistsWalletAndShowsCreatedNews() = runTest(testDispatcher) {
        awaitSeedPhrase()

        viewModel.onEvent(CreateWalletEvent.LabelChanged("  Daily   spending  "))
        viewModel.onEvent(CreateWalletEvent.SeedPhraseSaved)

        assertEquals(CreateWalletNews.WalletCreated, withTimeout(5.seconds) { viewModel.news.first() })
        assertEquals(
            listOf(
                WalletEntity(
                    address = ethManager.creationResult.address.toString(),
                    encryptedPrivateKeyBase64 = "encrypted_1",
                    label = "Daily spending",
                ),
            ),
            walletDao.wallets,
        )
    }

    @Test
    fun initializationPreservesGeneratedSeedPhrase() = runTest(testDispatcher) {
        val seedPhrase = listOf("test", "seed", "phrase", "words")
        ethManager.creationResult = ethManager.creationResult.copy(seedPhrase = seedPhrase)
        createViewModel()

        assertEquals(seedPhrase, awaitSeedPhrase())
    }

    @Test
    fun walletCreatedAfterViewModelClearedIsDiscardedAndCleared() = runTest(testDispatcher) {
        awaitSeedPhrase()
        viewModelStore.clear()
        val racedPrivateKey = PrivateKey.fromBytes(ByteArray(32).also { it[31] = 2 })
        ethManager.creationResult = EthCreationResult(
            address = EthAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F"),
            privateKey = racedPrivateKey,
            seedPhrase = testSeedPhrase,
        )
        val creationStarted = CompletableDeferred<Unit>()
        val continueCreation = CompletableDeferred<Unit>()
        ethManager.beforeCreateWallet = {
            creationStarted.complete(Unit)
            withContext(NonCancellable) { continueCreation.await() }
        }
        createViewModel()

        creationStarted.await()
        viewModelStore.clear()
        continueCreation.complete(Unit)
        awaitPrivateKeyCleared(racedPrivateKey)

        assertFailsWith<IllegalStateException> { racedPrivateKey.deriveAddress() }
        assertTrue(walletDao.wallets.isEmpty())
    }

    @Test
    fun walletNameMustBeNonBlankAndNoLongerThanFortyCharacters() = runTest(testDispatcher) {
        awaitSeedPhrase()

        assertTrue(!viewModel.viewState.value.isLabelValid)

        viewModel.onEvent(CreateWalletEvent.LabelChanged("x".repeat(MAX_WALLET_LABEL_LENGTH)))
        assertTrue(viewModel.viewState.value.isLabelValid)

        viewModel.onEvent(CreateWalletEvent.LabelChanged("x".repeat(MAX_WALLET_LABEL_LENGTH + 1)))
        assertTrue(!viewModel.viewState.value.isLabelValid)
    }

    private suspend fun awaitSeedPhrase(): List<String> =
        withTimeout(5.seconds) {
            viewModel.viewState
                .filter { it.seedPhrase.isNotEmpty() }
                .first()
                .seedPhrase
        }

    private suspend fun awaitPrivateKeyCleared(privateKey: PrivateKey) {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (true) {
                    val bytes = privateKey.copyBytes()
                    val cleared = bytes.all { it == 0.toByte() }
                    if (cleared) return@withTimeout
                    delay(10)
                }
            }
        }
    }

    private fun createViewModel() {
        if (::viewModelStore.isInitialized) {
            viewModelStore.clear()
        }
        viewModel = CreateWalletViewModel(ethManager, walletRepository)
        viewModelStore = ViewModelStore().apply { put("create", viewModel) }
    }
}
