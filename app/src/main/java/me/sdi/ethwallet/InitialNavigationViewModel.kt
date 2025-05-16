package me.sdi.ethwallet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InitialNavigationViewModel : ViewModel() {
    private val _numberOfWallets = MutableStateFlow(-1)
    val numberOfWallets: StateFlow<Int> = _numberOfWallets.asStateFlow()

    init {
        _numberOfWallets.value = 0
    }
}
