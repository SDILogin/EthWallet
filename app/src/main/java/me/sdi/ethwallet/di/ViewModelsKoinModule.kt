package me.sdi.ethwallet.di

import me.sdi.ethwallet.InitialNavigationViewModel
import me.sdi.ethwallet.ui.receive.ReceiveQrViewModel
import me.sdi.ethwallet.ui.send.SendViewModel
import me.sdi.ethwallet.ui.wallet.CreateWalletViewModel
import me.sdi.ethwallet.ui.wallet.SetPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.VerifyPinCodeViewModel
import me.sdi.ethwallet.ui.wallet.WalletViewModel
import me.sdi.ethwallet.ui.wallet.WalletsListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelsModule = module {
    viewModel<InitialNavigationViewModel> {
        InitialNavigationViewModel(
            walletDao = get(),
            pinCodeStorage = get(),
        )
    }

    viewModel<SetPinCodeViewModel> {
        SetPinCodeViewModel(
            pinCodeStorage = get(),
        )
    }

    viewModel<VerifyPinCodeViewModel> {
        VerifyPinCodeViewModel(
            pinCodeStorage = get(),
        )
    }

    viewModel<CreateWalletViewModel> {
        CreateWalletViewModel(
            walletRepository = get(),
        )
    }

    viewModel<WalletViewModel> {
        WalletViewModel(
            walletRepository = get(),
            balanceProvider = get(),
            fiatRateProvider = get(),
        )
    }

    viewModel<ReceiveQrViewModel> {
        ReceiveQrViewModel(
            walletRepository = get(),
        )
    }

    viewModel<SendViewModel> {
        SendViewModel(
            transferInteractor = get(),
        )
    }

    viewModel<WalletsListViewModel> {
        WalletsListViewModel(
            walletRepository = get(),
        )
    }
}
