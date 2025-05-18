package me.sdi.ethwallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.dao.WalletDao

class InitialNavigationViewModel(
    private val walletDao: WalletDao,
) : ViewModel() {
    private val _numberOfWallets = MutableStateFlow(-1)
    val numberOfWallets: StateFlow<Int> = _numberOfWallets.asStateFlow()

    init {
        viewModelScope.launch {
            _numberOfWallets.value =
                try {
                    walletDao.getAllWallets().size
                } catch (e: Exception) {
                    ensureActive()

                    Logger.warn("Db") { e.stackTraceToString() }

                    0
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appContext = checkNotNull(this[APPLICATION_KEY])
                    val appDatabase = AppDatabase.getInstance(appContext)
                    val walletDao = appDatabase.walletDao()
                    InitialNavigationViewModel(walletDao)
                }
            }
    }
}
