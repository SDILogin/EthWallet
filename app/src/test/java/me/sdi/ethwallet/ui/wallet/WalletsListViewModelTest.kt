package me.sdi.ethwallet.ui.wallet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.MainDispatcherRule
import me.sdi.ethwallet.data.repository.WalletRepository
import me.sdi.ethwallet.database.entity.WalletEntity
import me.sdi.ethwallet.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mock dependencies
    private lateinit var walletRepository: WalletRepository

    // Class under test
    private lateinit var viewModel: WalletsListViewModel

    // Test data
    private val testWallet1 = WalletEntity(
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        encryptedPrivateKeyBase64 = "encrypted_key_1",
        label = "My Primary Wallet",
        isActive = true
    )

    private val testWallet2 = WalletEntity(
        address = "0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF",
        encryptedPrivateKeyBase64 = "encrypted_key_2",
        label = "My Secondary Wallet",
        isActive = false
    )

    private val testWallets = listOf(testWallet1, testWallet2)

    @Before
    fun setUp() {
        walletRepository = mockk()
        
        // Default behavior for repository methods
        coEvery { walletRepository.getAllWallets() } returns testWallets
        
        viewModel = WalletsListViewModel(walletRepository = walletRepository)
    }
    
    @Test
    fun `initial state should show loading state`() = runTest {
        // Create a special instance with a different mock to test initial state
        // This avoids the automatic loading from the init block
        val initialStateRepo = mockk<WalletRepository>()
        val initialViewModel = WalletsListViewModel(walletRepository = initialStateRepo)
        
        val initialState = initialViewModel.uiState.first()
        assertTrue("Initial state should have isLoading = true", initialState.isLoading)
        assertEquals("Initial state should have empty wallet list", emptyList<WalletEntity>(), initialState.wallets)
        assertNull("Initial state should have no navigation target", initialState.navigateTo)
    }
    
    @Test
    fun `loadWallets should fetch wallets from repository and update state`() = runTest {
        // Wallet list is loaded in init block, just need to await the result
        advanceUntilIdle()
        
        val currentState = viewModel.uiState.first()
        assertFalse("Loading state should be false after initialization", currentState.isLoading)
        assertEquals("Wallet list should be updated with test wallets", testWallets, currentState.wallets)
        assertNull("Navigation state should remain null", currentState.navigateTo)
        
        // We can't verify exactly 1 call since the init block makes the call automatically
        // and we don't have control over when it happens
        coVerify(atLeast = 1) { walletRepository.getAllWallets() }
    }
    
    @Test
    fun `loadWallets should handle empty wallet list correctly`() = runTest {
        // Create a fresh mock for this test
        val emptyRepo = mockk<WalletRepository>()
        coEvery { emptyRepo.getAllWallets() } returns emptyList()
        
        // Create a new instance to trigger the init block with the updated mock
        val emptyViewModel = WalletsListViewModel(walletRepository = emptyRepo)
        advanceUntilIdle()
        
        val currentState = emptyViewModel.uiState.first()
        assertFalse("Loading state should be false after initialization", currentState.isLoading)
        assertEquals("Wallet list should be empty", emptyList<WalletEntity>(), currentState.wallets)
        assertNull("Navigation state should remain null", currentState.navigateTo)
        
        coVerify(exactly = 1) { emptyRepo.getAllWallets() }
    }
    
    @Test
    fun `loadWallets should handle repository exceptions`() = runTest {
        // Create a fresh mock for this test
        val errorRepo = mockk<WalletRepository>()
        coEvery { errorRepo.getAllWallets() } throws RuntimeException("Repository failure")
        
        // Create a new instance to trigger the init block with the updated mock
        val errorViewModel = WalletsListViewModel(walletRepository = errorRepo)
        advanceUntilIdle()
        
        val currentState = errorViewModel.uiState.first()
        assertFalse("Loading state should be false after error", currentState.isLoading)
        assertEquals("Wallet list should remain empty after error", emptyList<WalletEntity>(), currentState.wallets)
        assertNull("Navigation state should remain null", currentState.navigateTo)
        
        coVerify(exactly = 1) { errorRepo.getAllWallets() }
    }
    
    @Test
    fun `setActiveWallet should update active wallet through repository and reload wallet list`() = runTest {
        // Setup repository mocks
        val targetAddress = testWallet2.address
        coEvery { walletRepository.setActiveWallet(targetAddress) } returns Unit
        
        // Advance time to complete initial loading
        advanceUntilIdle()
        
        // Reset verification
        coVerify(exactly = 1) { walletRepository.getAllWallets() }
        
        // Perform setActiveWallet
        viewModel.setActiveWallet(targetAddress)
        advanceUntilIdle()
        
        // Verify repository interactions
        coVerify(exactly = 1) { walletRepository.setActiveWallet(targetAddress) }
        coVerify(exactly = 2) { walletRepository.getAllWallets() } // Initial + reload
    }
    
    @Test
    fun `setActiveWallet should handle repository exceptions`() = runTest {
        // Setup repository mocks for exception
        val targetAddress = testWallet2.address
        coEvery { walletRepository.setActiveWallet(targetAddress) } throws RuntimeException("Setting active wallet failed")
        
        // Advance time to complete initial loading
        advanceUntilIdle()
        
        // Perform setActiveWallet that throws
        viewModel.setActiveWallet(targetAddress)
        advanceUntilIdle()
        
        // Verify repository interactions
        coVerify(exactly = 1) { walletRepository.setActiveWallet(targetAddress) }
        coVerify(exactly = 1) { walletRepository.getAllWallets() } // Only the initial load
        
        // State should remain the same
        val currentState = viewModel.uiState.first()
        assertEquals("Wallet list should not change after error", testWallets, currentState.wallets)
        assertFalse("Loading state should be false after error", currentState.isLoading)
        assertNull("Navigation state should remain null", currentState.navigateTo)
    }
    
    @Test
    fun `deleteWallet should delete wallet and reload list when other wallets remain`() = runTest {
        // Setup repository mocks
        val addressToDelete = testWallet2.address
        coEvery { walletRepository.deleteWallet(addressToDelete) } returns Unit
        coEvery { walletRepository.hasWallets() } returns true
        
        // Advance time to complete initial loading
        advanceUntilIdle()
        
        // Reset verification counters
        coVerify(exactly = 1) { walletRepository.getAllWallets() }
        
        // Perform delete wallet
        viewModel.deleteWallet(addressToDelete)
        advanceUntilIdle()
        
        // Verify repository interactions
        coVerify(exactly = 1) { walletRepository.deleteWallet(addressToDelete) }
        coVerify(exactly = 1) { walletRepository.hasWallets() }
        coVerify(exactly = 2) { walletRepository.getAllWallets() } // Initial + reload
        
        // Verify UI state
        val currentState = viewModel.uiState.first()
        assertNull("Navigation state should remain null when wallets remain", currentState.navigateTo)
    }
    
    @Test
    fun `deleteWallet should navigate to onboarding when no wallets remain`() = runTest {
        // Setup repository mocks for the case where there are no more wallets
        val addressToDelete = testWallet1.address
        coEvery { walletRepository.deleteWallet(addressToDelete) } returns Unit
        coEvery { walletRepository.hasWallets() } returns false
        
        // Advance time to complete initial loading
        advanceUntilIdle()
        
        // Perform delete wallet
        viewModel.deleteWallet(addressToDelete)
        advanceUntilIdle()
        
        // Verify repository interactions
        coVerify(exactly = 1) { walletRepository.deleteWallet(addressToDelete) }
        coVerify(exactly = 1) { walletRepository.hasWallets() }
        coVerify(exactly = 1) { walletRepository.getAllWallets() } // Only initial load, no reload
        
        // Verify UI state has proper navigation target
        val currentState = viewModel.uiState.first()
        assertEquals("Should navigate to onboarding when no wallets remain", 
                     Destination.OnBoardingScreen, 
                     currentState.navigateTo)
    }
    
    @Test
    fun `deleteWallet should handle repository exceptions`() = runTest {
        // Setup repository mocks for exception
        val addressToDelete = testWallet1.address
        coEvery { walletRepository.deleteWallet(addressToDelete) } throws RuntimeException("Delete wallet failed")
        
        // Advance time to complete initial loading
        advanceUntilIdle()
        
        // Perform deleteWallet that throws
        viewModel.deleteWallet(addressToDelete)
        advanceUntilIdle()
        
        // Verify repository interactions
        coVerify(exactly = 1) { walletRepository.deleteWallet(addressToDelete) }
        coVerify(exactly = 0) { walletRepository.hasWallets() } // Should not be called when exception
        coVerify(exactly = 1) { walletRepository.getAllWallets() } // Only the initial load
        
        // State should remain the same
        val currentState = viewModel.uiState.first()
        assertEquals("Wallet list should not change after error", testWallets, currentState.wallets)
        assertFalse("Loading state should be false after error", currentState.isLoading)
        assertNull("Navigation state should remain null", currentState.navigateTo)
    }
    
    @Test
    fun `navigateToWalletDetails should update navigation state to WalletScreen`() = runTest {
        // Initial navigation should be null
        val initialState = viewModel.uiState.first()
        assertNull("Initial navigation state should be null", initialState.navigateTo)
        
        // Call navigation function
        viewModel.navigateToWalletDetails()
        
        // Check updated state
        val updatedState = viewModel.uiState.first()
        assertEquals("Navigation state should be set to WalletScreen", 
                     Destination.WalletScreen, 
                     updatedState.navigateTo)
    }
    
    @Test
    fun `consumeNavigation should clear navigation state`() = runTest {
        // Setup navigation state first
        viewModel.navigateToWalletDetails()
        val stateWithNavigation = viewModel.uiState.first()
        assertEquals("Navigation state should be set before consumption", 
                     Destination.WalletScreen, 
                     stateWithNavigation.navigateTo)
        
        // Call consume function
        viewModel.consumeNavigation()
        
        // Check cleared state
        val clearedState = viewModel.uiState.first()
        assertNull("Navigation state should be cleared after consumption", clearedState.navigateTo)
    }
}