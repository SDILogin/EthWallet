@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.sdi.ethwallet.kotlin

import ethwallet_crypto.ewc_abi_version
import ethwallet_crypto.ewc_create_mnemonic
import ethwallet_crypto.ewc_derive_address
import ethwallet_crypto.ewc_derive_private_key
import ethwallet_crypto.ewc_sign_eip1559_transaction
import ethwallet_crypto.ewc_validate_mnemonic
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

internal actual object NativeCrypto {
    private val nativeLibrary = lazy {
        check(ewc_abi_version() == NATIVE_ABI_VERSION.toUInt()) {
            "Unsupported ethwallet_crypto ABI"
        }
    }

    actual fun createMnemonic(): CharArray {
        ensureNativeLibrary()
        val output = ByteArray(NATIVE_MNEMONIC_MAXIMUM_LENGTH)
        try {
            val length = memScoped {
                val nativeLength = alloc<ULongVar>()
                checkNativeStatus(
                    output.usePinned { pinned ->
                        ewc_create_mnemonic(
                            pinned.addressOf(0).reinterpret<UByteVar>(),
                            nativeLength.ptr,
                        )
                    },
                )
                nativeLength.value.toInt()
            }
            require(length in 1..output.size) { "Native mnemonic length is invalid" }
            return CharArray(length) { output[it].toInt().toChar() }
        } finally {
            output.fill(0)
        }
    }

    actual fun validateMnemonic(mnemonic: ByteArray) {
        ensureNativeLibrary()
        checkNativeStatus(
            withPinnedInput(mnemonic) { input ->
                ewc_validate_mnemonic(input, mnemonic.size.toULong())
            },
        )
    }

    actual fun derivePrivateKey(mnemonic: ByteArray): ByteArray = nativeOutput(NATIVE_PRIVATE_KEY_LENGTH) { output ->
        ensureNativeLibrary()
        withPinnedInput(mnemonic) { input ->
            output.usePinned { pinned ->
                ewc_derive_private_key(
                    input,
                    mnemonic.size.toULong(),
                    pinned.addressOf(0).reinterpret<UByteVar>(),
                )
            }
        }
    }

    actual fun deriveAddress(privateKey: ByteArray): ByteArray = nativeOutput(NATIVE_ADDRESS_LENGTH) { output ->
        ensureNativeLibrary()
        requireNativePrivateKeyLength(privateKey)
        withPinnedInput(privateKey) { input ->
            output.usePinned { pinned ->
                ewc_derive_address(
                    input,
                    pinned.addressOf(0).reinterpret<UByteVar>(),
                )
            }
        }
    }

    actual fun signEip1559Transaction(
        privateKey: ByteArray,
        chainId: Long,
        nonce: ByteArray,
        maxPriorityFeePerGas: ByteArray,
        maxFeePerGas: ByteArray,
        gasLimit: ByteArray,
        destination: ByteArray,
        value: ByteArray,
        data: ByteArray,
    ): ByteArray {
        ensureNativeLibrary()
        require(chainId > 0) { "EIP-1559 chain ID must be positive" }
        requireNativePrivateKeyLength(privateKey)
        requireNativeTransactionFieldLengths(
            nonce,
            maxPriorityFeePerGas,
            maxFeePerGas,
            gasLimit,
            destination,
            value,
        )
        val output = ByteArray(nativeSignedTransactionCapacity(data.size))
        try {
            val length = memScoped {
                val nativeLength = alloc<ULongVar>()
                val status = privateKey.usePinned { privateKeyPinned ->
                    nonce.usePinned { noncePinned ->
                        maxPriorityFeePerGas.usePinned { priorityFeePinned ->
                            maxFeePerGas.usePinned { maxFeePinned ->
                                gasLimit.usePinned { gasLimitPinned ->
                                    destination.usePinned { destinationPinned ->
                                        value.usePinned { valuePinned ->
                                            withOptionalPinnedInput(data) { dataPointer ->
                                                output.usePinned { outputPinned ->
                                                    ewc_sign_eip1559_transaction(
                                                        privateKeyPinned.addressOf(0).reinterpret(),
                                                        chainId.toULong(),
                                                        noncePinned.addressOf(0).reinterpret(),
                                                        priorityFeePinned.addressOf(0).reinterpret(),
                                                        maxFeePinned.addressOf(0).reinterpret(),
                                                        gasLimitPinned.addressOf(0).reinterpret(),
                                                        destinationPinned.addressOf(0).reinterpret(),
                                                        valuePinned.addressOf(0).reinterpret(),
                                                        dataPointer,
                                                        data.size.toULong(),
                                                        outputPinned.addressOf(0).reinterpret(),
                                                        nativeLength.ptr,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                checkNativeStatus(status)
                nativeLength.value.toLong()
            }
            check(length in 1..output.size.toLong()) { "Native signed transaction length is invalid" }
            return output.copyOf(length.toInt())
        } finally {
            output.fill(0)
        }
    }

    private fun ensureNativeLibrary() {
        nativeLibrary.value
    }

    private fun withPinnedInput(
        input: ByteArray,
        operation: (CPointer<UByteVar>) -> Int,
    ): Int {
        if (input.isEmpty()) {
            return STATUS_INVALID_ARGUMENT
        }
        return input.usePinned { pinned ->
            operation(pinned.addressOf(0).reinterpret())
        }
    }

    private fun <T> withOptionalPinnedInput(
        input: ByteArray,
        operation: (CPointer<UByteVar>?) -> T,
    ): T = if (input.isEmpty()) {
        operation(null)
    } else {
        input.usePinned { pinned -> operation(pinned.addressOf(0).reinterpret()) }
    }
}
