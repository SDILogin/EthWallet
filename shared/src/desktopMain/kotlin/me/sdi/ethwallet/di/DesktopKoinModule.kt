package me.sdi.ethwallet.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.Web3jEthManager
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerPinFile
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinCodeStorageFile
import me.sdi.ethwallet.core.security.PinLockPolicy
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.PinSessionDataCleaner
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.SoftwarePrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.WalletDao
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import kotlin.time.Duration

actual fun platformModule(chainConfig: ChainConfig): Module = module {
    single<AppDatabase> { DesktopDatabaseBuilder.getInstance() }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }

    single<PrivateKeyEncryptor> { SoftwarePrivateKeyEncryptor(pinSession = get()) }

    factory<DataCleaner> {
        DataCleanerComposite(
            listOf(
                DataCleanerPinFile(pinArtifactsFile()),
                PinSessionDataCleaner(get()),
            ),
        )
    }

    single { chainConfig }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    single<EthManager> { Web3jEthManager.create(get()) }

    single<PinCodeStorage> {
        PinCodeStorageFile(
            artifactsFile = pinArtifactsFile(),
            userActivityManager = get(),
            pinAttemptsGuard = get(),
            pinSession = get(),
        )
    }
    single<PinAttemptsGuard> { DesktopPinAttemptsGuard() }
    single<BiometricUnlock> { DesktopBiometricUnlock() }
}

private fun pinArtifactsFile(): File =
    File(System.getProperty("user.home"), ".ethwallet/pin.properties")

private class DesktopPinAttemptsGuard : PinAttemptsGuard {
    private val unlocked =
        PinLockState.Active(
            totalFailures = 0,
            remainingInWindow = PinLockPolicy.ATTEMPTS_PER_WINDOW,
            lockRemaining = Duration.ZERO,
        )

    override suspend fun bootstrap() = Unit

    override suspend fun status(): PinLockState = unlocked

    override suspend fun onFailedAttempt(): PinLockState = unlocked

    override suspend fun onSuccess() = Unit

    override suspend fun clear() = Unit
}

private class DesktopBiometricUnlock : BiometricUnlock {
    override suspend fun status(): BiometricStatus = BiometricStatus.NotSupported

    override suspend fun enable(): BiometricResult = BiometricResult.Error("Biometrics are not supported")

    override suspend fun disable() = Unit

    override suspend fun unlock(): BiometricResult = BiometricResult.Error("Biometrics are not supported")
}
