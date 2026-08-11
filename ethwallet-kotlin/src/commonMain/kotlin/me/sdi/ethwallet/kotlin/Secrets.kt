package me.sdi.ethwallet.kotlin

private const val REDACTED_MNEMONIC = "Mnemonic(**redacted**)"
private const val REDACTED_PRIVATE_KEY = "PrivateKey(**redacted**)"
private const val REDACTED_WORD = "MnemonicWord(**redacted**)"
private const val PRIVATE_KEY_BYTES = 32

/**
 * BIP-39 recovery phrase held in mutable storage.
 *
 * The library copies [phrase] into a [CharArray] and does not create immutable phrase or word
 * strings. A caller that passes a [String] remains responsible for that string's lifetime.
 */
class Mnemonic internal constructor(
    phrase: CharSequence,
) {
    private val characters = CharArray(phrase.length) { index -> phrase[index] }

    val words: List<CharSequence> = buildWords(characters)

    fun clear() {
        characters.fill('\u0000')
    }

    override fun toString(): String = REDACTED_MNEMONIC

    companion object {
        /**
         * Parses and validates a BIP-39 phrase.
         *
         * This skeleton deliberately does not implement parsing yet.
         */
        fun parse(value: CharSequence): Mnemonic = notImplemented()
    }
}

/**
 * secp256k1 private key held as 32 mutable bytes.
 */
class PrivateKey private constructor(
    private val bytes: ByteArray,
) {
    fun copyBytes(): ByteArray = bytes.copyOf()

    fun clear() {
        bytes.fill(0)
    }

    override fun toString(): String = REDACTED_PRIVATE_KEY

    companion object {
        fun fromBytes(bytes: ByteArray): PrivateKey {
            require(bytes.size == PRIVATE_KEY_BYTES) {
                "Ethereum private keys must contain $PRIVATE_KEY_BYTES bytes"
            }
            return PrivateKey(bytes.copyOf())
        }
    }
}

private fun buildWords(characters: CharArray): List<CharSequence> {
    val words = mutableListOf<CharSequence>()
    var index = 0

    while (index < characters.size) {
        while (index < characters.size && characters[index].isWhitespace()) {
            index++
        }
        if (index == characters.size) break

        val start = index
        while (index < characters.size && !characters[index].isWhitespace()) {
            index++
        }
        words += CharArraySlice(
            characters = characters,
            startIndex = start,
            endIndex = index,
        )
    }

    return words
}

private class CharArraySlice(
    private val characters: CharArray,
    private val startIndex: Int,
    private val endIndex: Int,
) : CharSequence {
    override val length: Int = endIndex - startIndex

    override fun get(index: Int): Char {
        checkElementIndex(index, length)
        return characters[startIndex + index]
    }

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence {
        require(startIndex in 0..length) { "startIndex is out of bounds" }
        require(endIndex in startIndex..length) { "endIndex is out of bounds" }
        return CharArraySlice(
            characters = characters,
            startIndex = this.startIndex + startIndex,
            endIndex = this.startIndex + endIndex,
        )
    }

    override fun toString(): String = REDACTED_WORD
}

private fun checkElementIndex(
    index: Int,
    size: Int,
) {
    require(index in 0 until size) { "index is out of bounds" }
}
