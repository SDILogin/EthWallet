package me.sdi.ethwallet.kotlin

private const val REDACTED_PRIVATE_KEY = "PrivateKey(**redacted**)"
private const val PRIVATE_KEY_BYTES = 32

/**
 * Mutable, single-owner secp256k1 private key held as 32 bytes.
 *
 * Do not clear this instance concurrently with reading or derivation.
 */
class PrivateKey private constructor(
    private val bytes: ByteArray,
) {
    private var cleared = false

    fun copyBytes(): ByteArray = bytes.copyOf()

    fun clear() {
        bytes.fill(0)
        cleared = true
    }

    override fun toString(): String = REDACTED_PRIVATE_KEY

    companion object {
        fun fromBytes(bytes: ByteArray): PrivateKey {
            require(bytes.size == PRIVATE_KEY_BYTES) {
                "Ethereum private keys must contain $PRIVATE_KEY_BYTES bytes"
            }
            require(bytes.isValidPrivateKey()) {
                "Invalid secp256k1 private key"
            }
            return PrivateKey(bytes.copyOf())
        }

        internal fun fromNativeBytes(bytes: ByteArray): PrivateKey {
            if (bytes.size != PRIVATE_KEY_BYTES || !bytes.isValidPrivateKey()) {
                bytes.fill(0)
                throw IllegalStateException("Rust returned an invalid secp256k1 private key")
            }
            return PrivateKey(bytes)
        }
    }

    internal fun requireUsable() {
        check(!cleared) { "Private key has been cleared" }
    }
}

private fun ByteArray.isValidPrivateKey(): Boolean {
    if (none { it.toInt() != 0 }) return false

    for (index in indices) {
        val value = this[index].toInt() and 0xff
        val order = SECP256K1_ORDER[index].toInt() and 0xff
        if (value != order) {
            return value < order
        }
    }
    return false
}

/**
 * The secp256k1 subgroup order `n`, serialized as a 32-byte big-endian integer.
 *
 * Defined by SEC 2, Recommended Elliptic Curve Domain Parameters, section 2.4.1:
 * https://www.secg.org/sec2-v2.pdf
 */
private val SECP256K1_ORDER = byteArrayOf(
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xff.toByte(),
    0xfe.toByte(),
    0xba.toByte(),
    0xae.toByte(),
    0xdc.toByte(),
    0xe6.toByte(),
    0xaf.toByte(),
    0x48,
    0xa0.toByte(),
    0x3b,
    0xbf.toByte(),
    0xd2.toByte(),
    0x5e,
    0x8c.toByte(),
    0xd0.toByte(),
    0x36,
    0x41,
    0x41,
)
