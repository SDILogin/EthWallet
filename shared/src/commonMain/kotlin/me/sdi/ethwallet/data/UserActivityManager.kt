package me.sdi.ethwallet.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.data.repository.WalletRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Interface for managing Lock Screen visibility based on user activity.
 *
 * This interface provides a mechanism to track user interactions and determine
 * whether a lock screen should be displayed. It exposes a StateFlow to observe
 * the lock screen visibility state and provides methods to signal user actions
 * and app unlock events.
 */
interface UserActivityManager {
    /**
     * A [StateFlow] that emits `true` if the lock screen should be shown, `false` otherwise.
     * The lock screen should be shown if the user has been inactive for a certain period of time.
     */
    val showLockScreen: StateFlow<Boolean>

    /**
     * Should be called when a user performs any action in the app.
     *
     * This method updates the last interaction time and determines if the lock screen should be shown.
     * If the lock screen is already showing, this method does nothing.
     * Otherwise, it checks if the time since the last interaction exceeds a predefined threshold (FIVE_MINUTES).
     * If it does, the `showLockScreen` state is updated to true, indicating the lock screen should be displayed.
     *
     * It must be enough to call this method only from `Activity#onUserInteraction()`
     */
    fun onAnyUserAction()

    /**
     * Call this function when the app has been unlocked (e.g., after the user enters their PIN).
     * This will hide the lock screen.
     */
    fun onAppUnlocked()
}

class ProcessLifetimeUserActivityManager(
    private val walletRepository: WalletRepository,
    private val applicationScope: CoroutineScope,
    private val inactivityTimeout: Duration = 10.minutes,
) : UserActivityManager {
    private var timerJob: Job? = null

    private val _showLockScreen = MutableStateFlow(false)
    override val showLockScreen: StateFlow<Boolean> = _showLockScreen.asStateFlow()

    override fun onAnyUserAction() {
        if (_showLockScreen.value) {
            // If the lock screen is already showing, do nothing
            return
        }

        restartTimer()
    }

    override fun onAppUnlocked() {
        _showLockScreen.update { false }

        restartTimer()
    }

    private fun restartTimer() {
        timerJob?.cancel()
        timerJob = applicationScope.launch {
            delay(inactivityTimeout)

            Logger.debug("Navigation") {
                "Inactivity timeout reached, showing lock screen"
            }

            val hasWallets = try {
                walletRepository.getAllWallets().isNotEmpty()
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Logger.warn("Navigation") { "Failed to fetch wallets: ${e.stackTraceToString()}" }

                false
            }

            if (hasWallets) {
                _showLockScreen.update { true }
            } else {
                Logger.debug("") {
                    "No wallets found, not showing lock screen"
                }
            }
        }
    }
}
