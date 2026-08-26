package me.sdi.ethwallet.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REDACTED_MNEMONIC = "Mnemonic(**redacted**)"
private const val REDACTED_WORD = "MnemonicWord(**redacted**)"

/**
 * Mutable, single-owner BIP-39 recovery phrase.
 *
 * Do not clear this instance concurrently with reading or derivation.
 */
class Mnemonic private constructor(
    private val characters: CharArray,
) {
    val words: List<CharSequence> = buildWords(characters)

    private var cleared = false

    fun clear() {
        characters.fill('\u0000')
        cleared = true
    }

    override fun toString(): String = REDACTED_MNEMONIC

    companion object {
        /**
         * Parses and validates a BIP-39 phrase.
         */
        suspend fun parse(value: CharSequence): Mnemonic = withContext(Dispatchers.Default) {
            val normalized = normalizeMnemonic(value)
            val bytes = normalized.copyAsciiBytes()
            var accepted = false
            try {
                NativeCrypto.validateMnemonic(bytes)
                accepted = true
                Mnemonic(normalized)
            } finally {
                bytes.fill(0)
                if (!accepted) {
                    normalized.fill('\u0000')
                }
            }
        }

        internal fun fromNativeCharacters(characters: CharArray): Mnemonic = Mnemonic(characters)
    }

    internal fun copyAsciiBytes(): ByteArray = characters.copyAsciiBytes()

    internal fun requireUsable() {
        check(!cleared) { "Mnemonic has been cleared" }
    }
}

private fun normalizeMnemonic(value: CharSequence): CharArray {
    val scratch = CharArray(value.length)
    var length = 0
    var needsSeparator = false
    try {
        for (character in value) {
            if (character.isWhitespace()) {
                needsSeparator = length > 0
                continue
            }

            val lowercase = when (character) {
                in 'A'..'Z' -> character.lowercaseChar()
                in 'a'..'z' -> character
                else -> throw IllegalArgumentException("Mnemonic must contain English words")
            }
            if (needsSeparator) {
                scratch[length++] = ' '
                needsSeparator = false
            }
            scratch[length++] = lowercase
        }

        if (length == 0) {
            throw IllegalArgumentException("Mnemonic must not be empty")
        }
        return scratch.copyOf(length)
    } finally {
        scratch.fill('\u0000')
    }
}

private fun CharArray.copyAsciiBytes(): ByteArray = ByteArray(size) { index ->
    val character = this[index]
    require(character.code <= 0x7f) { "Mnemonic must contain ASCII English words" }
    character.code.toByte()
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
