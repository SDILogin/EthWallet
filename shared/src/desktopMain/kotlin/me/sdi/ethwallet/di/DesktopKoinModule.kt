package me.sdi.ethwallet.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.security.AppLockMethod
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerPinArtifacts
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.core.security.MasterKekProtection
import me.sdi.ethwallet.core.security.MasterKekProtectionSecureEnclave
import me.sdi.ethwallet.core.security.PasswordKekStorage
import me.sdi.ethwallet.core.security.PasswordKekStorageSoftware
import me.sdi.ethwallet.core.security.PasswordKeyDerivationArgon2
import me.sdi.ethwallet.core.security.PasswordScorer
import me.sdi.ethwallet.core.security.PasswordScorerZxcvbn
import me.sdi.ethwallet.core.security.PinSessionDataCleaner
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.SoftwarePrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.core.security.pinArtifactsDataStore
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.database.DesktopDatabaseBuilder
import me.sdi.ethwallet.database.dao.FiatRateDao
import me.sdi.ethwallet.database.dao.TokenBalanceDao
import me.sdi.ethwallet.database.dao.TransferHistoryDao
import me.sdi.ethwallet.database.dao.WalletDao
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

// Argon2id above the OWASP minimum (m=19 MiB, t=2, p=1).
private const val ARGON2_MEMORY_KIB = 65_536
private const val ARGON2_ITERATIONS = 3
private const val ARGON2_PARALLELISM = 1

actual fun platformModule(chainConfig: ChainConfig): Module = module {
    single<AppDatabase> { DesktopDatabaseBuilder.build(walletDirectory()) }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<TokenBalanceDao> { get<AppDatabase>().tokenBalanceDao() }
    single<FiatRateDao> { get<AppDatabase>().fiatRateDao() }
    single<TransferHistoryDao> { get<AppDatabase>().transferHistoryDao() }

    single<PrivateKeyEncryptor> { SoftwarePrivateKeyEncryptor(pinSession = get()) }

    single<DataStore<Preferences>> { pinArtifactsDataStore(walletDirectory()) }

    factory<DataCleaner> {
        DataCleanerComposite(
            listOf(
                DataCleanerRoomDb(get()),
                DataCleanerPinArtifacts(get()),
                PinSessionDataCleaner(get()),
            ),
        )
    }

    single { chainConfig }

    single<HttpClient> { createHttpClient(OkHttp.create()) }

    single { AppLockMethod.Password }
    single<MasterKekProtection> { MasterKekProtectionSecureEnclave() }
    single<PasswordKekStorage> {
        PasswordKekStorageSoftware(
            dataStore = get(),
            protection = get(),
            keyDerivation = PasswordKeyDerivationArgon2(
                memoryKib = ARGON2_MEMORY_KIB,
                iterations = ARGON2_ITERATIONS,
                parallelism = ARGON2_PARALLELISM,
            ),
        )
    }
    single<PasswordScorer> { PasswordScorerZxcvbn() }
    single<BiometricUnlock> { DesktopBiometricUnlock() }
}

private fun walletDirectory(): File = File(System.getProperty("user.home"), ".ethwallet")

private class DesktopBiometricUnlock : BiometricUnlock {
    override suspend fun status(): BiometricStatus = BiometricStatus.NotSupported

    override suspend fun enable(): BiometricResult = BiometricResult.Error("Biometrics are not supported")

    override suspend fun disable() = Unit

    override suspend fun unlock(): BiometricResult = BiometricResult.Error("Biometrics are not supported")
}
