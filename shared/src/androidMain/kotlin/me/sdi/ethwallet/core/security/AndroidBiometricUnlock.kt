package me.sdi.ethwallet.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.sdi.ethwallet.Logger
import me.sdi.ethwallet.core.security.biometry.BiometricResult
import me.sdi.ethwallet.core.security.biometry.BiometricStatus
import me.sdi.ethwallet.core.security.biometry.BiometricUnlock
import me.sdi.ethwallet.data.UserActivityManager
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.biometric_prompt_cancel
import me.sdi.ethwallet.resources.biometric_prompt_setup_subtitle
import me.sdi.ethwallet.resources.biometric_prompt_title
import me.sdi.ethwallet.resources.biometric_prompt_unlock_subtitle
import me.sdi.ethwallet.resources.biometric_prompt_use_pin
import me.sdi.ethwallet.util.CurrentActivityHolder
import me.sdi.ethwallet.util.preferencesDataStore
import org.jetbrains.compose.resources.getString
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * [me.sdi.ethwallet.core.security.biometry.BiometricUnlock] backed by androidx.biometric and an AndroidKeyStore key that requires a strong
 * biometric for every use.
 *
 * The key wraps the same master KEK that [PinCodeStorageDataStore] keeps under its AndroidKeyStore key,
 * and the wrapped blob is stored as `bio_wrapped_kek` in the same DataStore. Every `doFinal` runs on the
 * cipher handed back inside the prompt's `AuthenticationResult`, so an unauthorized cipher can never
 * produce the KEK. The key is created with `setInvalidatedByBiometricEnrollment(true)`, so enrolling
 * a new fingerprint or face destroys it; that surfaces as [BiometricStatus.Invalidated] and wipes the
 * stored artifacts.
 */
class AndroidBiometricUnlock(
    private val context: Context,
    private val userActivityManager: UserActivityManager,
    private val pinSession: PinSession,
    private val activityHolder: CurrentActivityHolder,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BiometricUnlock {
    private companion object {
        private const val TAG = "AndroidBiometricUnlock"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "biometric_unlock_key"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private val BIO_WRAPPED_KEK = stringPreferencesKey("bio_wrapped_kek")
        private val BIO_INVALIDATED = booleanPreferencesKey("bio_invalidated")
    }

    private val dataStore = context.preferencesDataStore

    private sealed interface PromptOutcome {
        data class Authorized(val cipher: Cipher) : PromptOutcome

        data class Rejected(val result: BiometricResult) : PromptOutcome
    }

    /**
     * The Keystore entry is gone, so the stored blob can never be unwrapped again. Separates that
     * unrecoverable state from transient Keystore failures, which must keep the stored artifacts.
     */
    private class MissingBiometricKeyException : IllegalStateException("Biometric key is missing")

    override suspend fun status(): BiometricStatus =
        try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> storedKeyStatus()
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NotEnrolled
                else -> BiometricStatus.NotSupported
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Biometric status is unavailable" }
            BiometricStatus.NotSupported
        }

    override suspend fun enable(): BiometricResult {
        val masterKek = try {
            pinSession.getOrThrow().copyOf()
        } catch (e: IllegalStateException) {
            Logger.warn(TAG, e) { "Biometric setup requires an unlocked session" }
            return BiometricResult.Error(e.message)
        }

        try {
            val cipher = try {
                withContext(dispatcher) {
                    deleteKey()
                    val secretKey = generateKey()
                    ensureActive()
                    Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn(TAG, e) { "Failed to create the biometric key" }
                deleteKey()
                return BiometricResult.Error(e.message)
            }

            val outcome = authenticate(
                cipher = cipher,
                subtitle = getString(Res.string.biometric_prompt_setup_subtitle),
                negativeButtonText = getString(Res.string.biometric_prompt_cancel),
            )
            return when (outcome) {
                is PromptOutcome.Authorized -> {
                    wrapMasterKek(outcome.cipher, masterKek)
                }
                is PromptOutcome.Rejected -> {
                    deleteKey()
                    outcome.result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Biometric setup failed unexpectedly" }
            deleteKey()
            return BiometricResult.Error(e.message)
        } finally {
            masterKek.fill(0)
        }
    }

    override suspend fun disable() = reset(invalidated = false)

    override suspend fun unlock(): BiometricResult {
        try {
            val blob = dataStore.data.first()[BIO_WRAPPED_KEK]
                ?: return BiometricResult.Error("Biometric unlock is not set up")

            val cipher = try {
                decryptCipher(blob)
            } catch (e: KeyPermanentlyInvalidatedException) {
                Logger.warn(TAG, e) { "Biometric enrollment changed, resetting biometric unlock" }
                reset(invalidated = true)
                return BiometricResult.Invalidated
            } catch (e: MissingBiometricKeyException) {
                Logger.warn(TAG, e) { "Biometric key is gone, resetting biometric unlock" }
                reset(invalidated = false)
                return BiometricResult.Error(e.message)
            } catch (e: IllegalArgumentException) {
                Logger.warn(TAG, e) { "Wrapped KEK blob is corrupt, resetting biometric unlock" }
                reset(invalidated = false)
                return BiometricResult.Error(e.message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn(TAG, e) { "Biometric key is temporarily unusable, keeping biometric unlock" }
                return BiometricResult.Error(e.message)
            }

            val outcome = authenticate(
                cipher = cipher,
                subtitle = getString(Res.string.biometric_prompt_unlock_subtitle),
                negativeButtonText = getString(Res.string.biometric_prompt_use_pin),
            )
            return when (outcome) {
                is PromptOutcome.Authorized -> unwrapMasterKek(outcome.cipher, blob)
                is PromptOutcome.Rejected -> outcome.result
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Biometric unlock failed unexpectedly, keeping biometric unlock" }
            return BiometricResult.Error(e.message)
        }
    }

    private suspend fun storedKeyStatus(): BiometricStatus {
        val preferences = dataStore.data.first()
        val blob = preferences[BIO_WRAPPED_KEK]
            ?: return if (preferences[BIO_INVALIDATED] == true) BiometricStatus.Invalidated else BiometricStatus.NotSetUp

        return try {
            decryptCipher(blob)
            BiometricStatus.Ready
        } catch (e: KeyPermanentlyInvalidatedException) {
            Logger.warn(TAG, e) { "Biometric enrollment changed, resetting biometric unlock" }
            reset(invalidated = true)
            BiometricStatus.Invalidated
        } catch (e: MissingBiometricKeyException) {
            Logger.warn(TAG, e) { "Biometric key is gone, resetting biometric unlock" }
            reset(invalidated = false)
            BiometricStatus.NotSetUp
        } catch (e: IllegalArgumentException) {
            Logger.warn(TAG, e) { "Wrapped KEK blob is corrupt, resetting biometric unlock" }
            reset(invalidated = false)
            BiometricStatus.NotSetUp
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Biometric key is temporarily unusable, keeping biometric unlock" }
            BiometricStatus.Ready
        }
    }

    private suspend fun wrapMasterKek(authorizedCipher: Cipher, masterKek: ByteArray): BiometricResult =
        try {
            val blob = withContext(dispatcher) {
                val encrypted = authorizedCipher.doFinal(masterKek)
                Base64.encodeToString(authorizedCipher.iv + encrypted, Base64.DEFAULT)
            }
            dataStore.edit { preferences ->
                preferences[BIO_WRAPPED_KEK] = blob
                preferences.remove(BIO_INVALIDATED)
            }
            Logger.debug(TAG) { "Biometric unlock enabled" }
            BiometricResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Failed to wrap the master KEK" }
            reset(invalidated = false)
            BiometricResult.Error(e.message)
        }

    private suspend fun unwrapMasterKek(authorizedCipher: Cipher, blob: String): BiometricResult =
        try {
            val masterKek = withContext(dispatcher) {
                val wrapped = wrappedBytes(blob)
                authorizedCipher.doFinal(wrapped.copyOfRange(GCM_IV_LENGTH, wrapped.size))
            }
            pinSession.set(masterKek)
            userActivityManager.onAppUnlocked()
            Logger.debug(TAG) { "Biometric unlock succeeded" }
            BiometricResult.Success
        } catch (e: AEADBadTagException) {
            Logger.warn(TAG, e) { "Wrapped KEK does not match the biometric key, resetting biometric unlock" }
            reset(invalidated = false)
            BiometricResult.Error(e.message)
        } catch (e: IllegalArgumentException) {
            Logger.warn(TAG, e) { "Wrapped KEK blob is corrupt, resetting biometric unlock" }
            reset(invalidated = false)
            BiometricResult.Error(e.message)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Failed to unwrap the master KEK, keeping biometric unlock" }
            BiometricResult.Error(e.message)
        }

    private suspend fun authenticate(cipher: Cipher, subtitle: String, negativeButtonText: String): PromptOutcome {
        val activity = activityHolder.current
            ?: return PromptOutcome.Rejected(BiometricResult.Error("No visible screen to show the biometric prompt"))

        val promptInfo = BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(getString(Res.string.biometric_prompt_title))
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                if (activity.supportFragmentManager.isStateSaved) {
                    Logger.warn(TAG) { "The host screen already saved its state, skipping the biometric prompt" }
                    continuation.resume(PromptOutcome.Rejected(BiometricResult.Cancelled))
                    return@suspendCancellableCoroutine
                }

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authorizedCipher = result.cryptoObject?.cipher
                        continuation.resume(
                            if (authorizedCipher == null) {
                                PromptOutcome.Rejected(BiometricResult.Error("Biometric authentication returned no cipher"))
                            } else {
                                PromptOutcome.Authorized(authorizedCipher)
                            },
                        )
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resume(PromptOutcome.Rejected(promptError(errorCode, errString)))
                    }
                }

                val prompt = BiometricPrompt(activity, callback)
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            }
        }
    }

    private fun promptError(errorCode: Int, errString: CharSequence): BiometricResult =
        when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            -> BiometricResult.Cancelled
            else -> BiometricResult.Error(errString.toString())
        }

    private suspend fun decryptCipher(blob: String): Cipher =
        withContext(dispatcher) {
            val iv = wrappedBytes(blob).copyOfRange(0, GCM_IV_LENGTH)
            val secretKey = loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw MissingBiometricKeyException()
            val cipher = Cipher.getInstance(TRANSFORMATION).also { ensureActive() }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher
        }

    private fun wrappedBytes(blob: String): ByteArray {
        val wrapped = Base64.decode(blob, Base64.DEFAULT)
        require(wrapped.size >= GCM_IV_LENGTH) { "Wrapped KEK blob is shorter than the IV" }
        return wrapped
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec
            .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setUnlockedDeviceRequired(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    private suspend fun reset(invalidated: Boolean) {
        try {
            dataStore.edit { preferences ->
                preferences.remove(BIO_WRAPPED_KEK)
                if (invalidated) {
                    preferences[BIO_INVALIDATED] = true
                } else {
                    preferences.remove(BIO_INVALIDATED)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Failed to reset the biometric artifacts" }
        }
        deleteKey()
    }

    private suspend fun deleteKey() {
        try {
            withContext(dispatcher) {
                val keyStore = loadKeyStore()
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.warn(TAG, e) { "Failed to delete the biometric key" }
        }
    }

    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }
}
