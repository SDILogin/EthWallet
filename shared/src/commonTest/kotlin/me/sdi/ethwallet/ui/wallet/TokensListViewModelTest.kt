package me.sdi.ethwallet.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.data.repository.TokenPreference
import me.sdi.ethwallet.data.repository.TokenPreferenceRepository
import me.sdi.ethwallet.data.repository.TokenPreferenceRepositoryInMemory
import me.sdi.ethwallet.ui.ETH_SEPOLIA_CHAIN_ID
import me.sdi.ethwallet.ui.daiOnSepolia
import me.sdi.ethwallet.ui.usdcOnSepolia
import me.sdi.ethwallet.ui.usdtOnSepolia
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TokensListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val walletAddress = EthAddress("0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")
    private val otherWalletAddress = EthAddress("0x0000000000000000000000000000000000000002")
    private val chainConfig = ChainConfig(
        rpcUrl = "https://rpc.example",
        chainId = ETH_SEPOLIA_CHAIN_ID,
        explorerBaseUrl = null,
        transactionHistoryApiBaseUrl = "https://history.example",
    )
    private val repository = TokenPreferenceRepositoryInMemory()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(tokenPreferenceRepository: TokenPreferenceRepository) =
        TokensListViewModel(
            walletAddress = walletAddress,
            chainConfig = chainConfig,
            tokenPreferenceRepository = tokenPreferenceRepository,
        )

    private fun TestScope.start(viewModel: TokensListViewModel): List<TokenListItem> {
        backgroundScope.launch { viewModel.viewState.collect {} }
        return viewModel.viewState.value.tokenListItems
    }

    private suspend fun storedPreferences() = repository.observe(walletAddress).first()

    @Test
    fun without_stored_preferences_all_supported_tokens_are_visible_in_default_order() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)

        assertEquals(
            listOf(
                TokenListItem(usdtOnSepolia, showOnMain = true),
                TokenListItem(usdcOnSepolia, showOnMain = true),
                TokenListItem(daiOnSepolia, showOnMain = true),
            ),
            start(viewModel),
        )
    }

    @Test
    fun stored_preferences_define_order_and_visibility_and_missing_tokens_are_appended() = runTest(testDispatcher) {
        repository.update(
            walletAddress,
            listOf(
                TokenPreference(daiOnSepolia.ethAddress, visible = false),
                TokenPreference(usdtOnSepolia.ethAddress, visible = true),
            ),
        )
        repository.update(otherWalletAddress, listOf(TokenPreference(usdcOnSepolia.ethAddress, visible = false)))
        val viewModel = createViewModel(tokenPreferenceRepository = repository)

        assertEquals(
            listOf(
                TokenListItem(daiOnSepolia, showOnMain = false),
                TokenListItem(usdtOnSepolia, showOnMain = true),
                TokenListItem(usdcOnSepolia, showOnMain = true),
            ),
            start(viewModel),
        )
    }

    @Test
    fun hiding_and_showing_a_token_is_persisted_for_the_wallet() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)

        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(initialItems[1].copy(showOnMain = false)))

        assertEquals(
            listOf(
                TokenPreference(usdtOnSepolia.ethAddress, visible = true),
                TokenPreference(usdcOnSepolia.ethAddress, visible = false),
                TokenPreference(daiOnSepolia.ethAddress, visible = true),
            ),
            storedPreferences(),
        )
        assertEquals(emptyList(), repository.observe(otherWalletAddress).first())

        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(initialItems[1]))

        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)
        assertEquals(
            initialItems.map { TokenPreference(it.knownToken.ethAddress, visible = true) },
            storedPreferences(),
        )
    }

    @Test
    fun two_quick_visibility_changes_store_the_final_list() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)

        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(initialItems[0].copy(showOnMain = false)))
        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(initialItems[2].copy(showOnMain = false)))

        assertEquals(
            listOf(
                TokenPreference(usdtOnSepolia.ethAddress, visible = false),
                TokenPreference(usdcOnSepolia.ethAddress, visible = true),
                TokenPreference(daiOnSepolia.ethAddress, visible = false),
            ),
            storedPreferences(),
        )
    }

    @Test
    fun moves_are_persisted_when_the_move_finishes() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)
        val usdt = initialItems[0].knownToken.ethAddress

        viewModel.onEvent(TokensListEvent.OnTokenMoved(usdt, 1))
        viewModel.onEvent(TokensListEvent.OnTokenMoved(usdt, 2))

        assertEquals(emptyList(), storedPreferences())

        viewModel.onEvent(TokensListEvent.OnTokenMoveFinished)

        assertEquals(listOf(initialItems[1], initialItems[2], initialItems[0]), viewModel.viewState.value.tokenListItems)
        assertEquals(
            listOf(usdcOnSepolia, daiOnSepolia, usdtOnSepolia).map { TokenPreference(it.ethAddress, visible = true) },
            storedPreferences(),
        )
    }

    @Test
    fun moving_last_token_to_first_and_repeating_the_move_keeps_requested_order() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)
        val event = TokensListEvent.OnTokenMoved(initialItems[2].knownToken.ethAddress, 0)
        val expected = listOf(initialItems[2], initialItems[0], initialItems[1])

        viewModel.onEvent(event)
        assertEquals(expected, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(event)
        assertEquals(expected, viewModel.viewState.value.tokenListItems)
    }

    @Test
    fun moving_tokens_preserves_visibility_and_visibility_changes_preserve_order() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)
        val hiddenToken = initialItems[0].copy(showOnMain = false)
        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(hiddenToken))

        viewModel.onEvent(TokensListEvent.OnTokenMoved(hiddenToken.knownToken.ethAddress, 2))
        viewModel.onEvent(TokensListEvent.OnTokenMoveFinished)

        assertEquals(listOf(initialItems[1], initialItems[2], hiddenToken), viewModel.viewState.value.tokenListItems)

        val secondHiddenToken = initialItems[1].copy(showOnMain = false)
        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(secondHiddenToken))

        assertEquals(listOf(secondHiddenToken, initialItems[2], hiddenToken), viewModel.viewState.value.tokenListItems)
        assertEquals(
            listOf(
                TokenPreference(usdcOnSepolia.ethAddress, visible = false),
                TokenPreference(daiOnSepolia.ethAddress, visible = true),
                TokenPreference(usdtOnSepolia.ethAddress, visible = false),
            ),
            storedPreferences(),
        )
    }

    @Test
    fun unknown_token_and_out_of_range_positions_leave_list_unchanged() = runTest(testDispatcher) {
        val viewModel = createViewModel(tokenPreferenceRepository = repository)
        val initialItems = start(viewModel)
        val tokenAddress = initialItems[0].knownToken.ethAddress

        viewModel.onEvent(TokensListEvent.OnTokenMoved(tokenAddress, -1))
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(TokensListEvent.OnTokenMoved(tokenAddress, initialItems.size))
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)

        viewModel.onEvent(
            TokensListEvent.OnTokenMoved(EthAddress("0x0000000000000000000000000000000000000001"), 1),
        )
        assertEquals(initialItems, viewModel.viewState.value.tokenListItems)
    }

    @Test
    fun save_failure_keeps_the_choice_on_screen_and_emits_error_news() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            tokenPreferenceRepository = object : TokenPreferenceRepository {
                override fun observe(walletAddress: EthAddress) = repository.observe(walletAddress)

                override suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>) {
                    error("Disk is full")
                }
            },
        )
        val news = mutableListOf<TokensListNews>()
        backgroundScope.launch { viewModel.news.collect { news += it } }
        val hiddenToken = start(viewModel)[0].copy(showOnMain = false)

        viewModel.onEvent(TokensListEvent.OnTokenVisibilityChanged(hiddenToken))

        assertEquals(hiddenToken, viewModel.viewState.value.tokenListItems[0])
        assertEquals(listOf<TokensListNews>(TokensListNews.ShowError), news)
    }

    @Test
    fun load_failure_emits_error_news() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            tokenPreferenceRepository = object : TokenPreferenceRepository {
                override fun observe(walletAddress: EthAddress): Flow<List<TokenPreference>> =
                    flow { error("Database is corrupted") }

                override suspend fun update(walletAddress: EthAddress, preferences: List<TokenPreference>) = Unit
            },
        )
        val news = mutableListOf<TokensListNews>()
        backgroundScope.launch { viewModel.news.collect { news += it } }

        assertEquals(emptyList(), start(viewModel))
        assertEquals(listOf<TokensListNews>(TokensListNews.ShowError), news)
    }
}
