package me.sdi.ethwallet.flow.e2e.environment

import android.app.Application
import me.sdi.ethwallet.core.ChainConfig
import me.sdi.ethwallet.core.EthManager
import me.sdi.ethwallet.core.EthManagerKotlin
import me.sdi.ethwallet.core.security.DataCleaner
import me.sdi.ethwallet.core.security.DataCleanerComposite
import me.sdi.ethwallet.core.security.DataCleanerRoomDb
import me.sdi.ethwallet.core.security.PinAttemptsGuard
import me.sdi.ethwallet.core.security.PinCodeStorage
import me.sdi.ethwallet.core.security.PinLockState
import me.sdi.ethwallet.core.security.PinSession
import me.sdi.ethwallet.core.security.PrivateKeyEncryptor
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.database.AppDatabase
import me.sdi.ethwallet.di.appModules
import me.sdi.ethwallet.kotlin.PrivateKey
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.time.Duration

internal interface SepoliaE2e

internal class SepoliaE2eTestApplication : Application()

internal fun startSepoliaE2eKoin(application: Application) {
    stopKoin()
    val koinApplication = startKoin {
        androidContext(application)
        allowOverride(true)
        modules(appModules(SEPOLIA_CHAIN_CONFIG) + sepoliaE2eHardwareModule)
    }
    check(koinApplication.koin.get<EthManager>() is EthManagerKotlin) {
        "Sepolia E2E must resolve EthManagerKotlin"
    }
}

internal fun stopSepoliaE2eKoin() {
    stopKoin()
}

private val SEPOLIA_CHAIN_CONFIG = ChainConfig(
    rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
    chainId = 11155111L,
    explorerBaseUrl = "https://sepolia.etherscan.io",
    transactionHistoryApiBaseUrl = "https://eth-sepolia.blockscout.com",
)

private val sepoliaE2eHardwareModule = module {
    single<PinAttemptsGuard> { InMemoryPinAttemptsGuard() }
    single<PinCodeStorage> {
        InMemoryPinCodeStorage(
            pinSession = get(),
            userActivityManager = get(),
        )
    }
    single { InMemoryPrivateKeyEncryptor() }
    single<PrivateKeyEncryptor> { get<InMemoryPrivateKeyEncryptor>() }
    single<BiometricUnlock> { UnsupportedBiometricUnlock() }
    single<DataCleaner> {
        DataCleanerComposite(
            listOf(
                DataCleanerRoomDb(get<AppDatabase>()),
                E2eHardwareDataCleaner(
                    pinCodeStorage = get(),
                    pinAttemptsGuard = get(),
                    privateKeyEncryptor = get(),
                ),
            ),
        )
    }
}

private class InMemoryPinCodeStorage(
    private val pinSession: PinSession,
    private val userActivityManager: UserActivityManager,
) : PinCodeStorage {
    private var storedPin: String? = null

    override suspend fun storePinCode(pinCode: String) {
        storedPin = pinCode
        unlock()
    }

    override suspend fun verifyPinCode(pinCode: String): Boolean {
        if (storedPin != pinCode) return false
        unlock()
        return true
    }

    override suspend fun hasPinCode(): Boolean = storedPin != null

    override suspend fun changePinCode(newPin: String) {
        storedPin = newPin
    }

    override suspend fun clearPinCode() {
        storedPin = null
        pinSession.clear()
    }

    private fun unlock() {
        pinSession.set(ByteArray(PIN_SESSION_KEY_SIZE) { PIN_SESSION_KEY_BYTE })
        userActivityManager.onAppUnlocked()
    }
}

private class InMemoryPrivateKeyEncryptor : PrivateKeyEncryptor {
    private val keysByAddress = mutableMapOf<String, ByteArray>()

    override suspend fun encryptPrivateKey(privateKey: PrivateKey, address: String): String {
        keysByAddress[address] = privateKey.copyBytes()
        return address
    }

    override suspend fun decryptPrivateKey(encryptedBase64: String, address: String): PrivateKey {
        check(encryptedBase64 == address) { "Private key address binding is invalid" }
        val bytes = checkNotNull(keysByAddress[address]).copyOf()
        return PrivateKey.fromBytes(bytes)
    }

    fun clear() {
        keysByAddress.clear()
    }
}

private class InMemoryPinAttemptsGuard : PinAttemptsGuard {
    private var failures = 0

    override suspend fun bootstrap() {
        failures = 0
    }

    override suspend fun status(): PinLockState = activeState()

    override suspend fun onFailedAttempt(): PinLockState {
        failures += 1
        return activeState()
    }

    override suspend fun onSuccess() {
        failures = 0
    }

    override suspend fun clear() {
        failures = 0
    }

    private fun activeState() = PinLockState.Active(
        totalFailures = failures,
        remainingInWindow = 0,
        lockRemaining = Duration.ZERO,
    )
}

private class UnsupportedBiometricUnlock : BiometricUnlock {
    override suspend fun status(): BiometricStatus = BiometricStatus.NotSupported

    override suspend fun enable(): BiometricResult = BiometricResult.Error("unsupported")

    override suspend fun disable() = Unit

    override suspend fun unlock(): BiometricResult = BiometricResult.Error("unsupported")
}

private class E2eHardwareDataCleaner(
    private val pinCodeStorage: PinCodeStorage,
    private val pinAttemptsGuard: PinAttemptsGuard,
    private val privateKeyEncryptor: InMemoryPrivateKeyEncryptor,
) : DataCleaner {
    override suspend fun clean() {
        pinCodeStorage.clearPinCode()
        pinAttemptsGuard.clear()
        privateKeyEncryptor.clear()
    }
}

private const val PIN_SESSION_KEY_SIZE = 32
private const val PIN_SESSION_KEY_BYTE: Byte = 7
