package com.souadachak.fixarabickeyboard.keyboard

import android.content.Context

/** Languages that can provide an external, centrally managed dictionary file. */
enum class DictionaryLanguage(val assetFileName: String) {
    ARABIC("ar.txt"),
    ENGLISH("en.txt"),
    FRENCH("fr.txt")
}

/**
 * Loads only the requested language dictionary and returns lexicographically
 * nearest words without keeping hundreds of thousands of String objects.
 */
class DictionaryManager(context: Context) {
    private val assets = context.applicationContext.assets
    private val loadedDictionaries = mutableMapOf<DictionaryLanguage, IndexedTextDictionary?>()

    /** Preload a bundled dictionary once, normally when the IME service starts. */
    fun preload(language: DictionaryLanguage) {
        dictionaryFor(language)
    }

    /**
     * Finds the insertion point of the current token in the sorted dictionary,
     * then returns up to [limit] words starting at that position.
     */
    fun suggestions(
        input: String,
        language: DictionaryLanguage,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()

        val token = currentToken(input)
        if (token.isEmpty()) return emptyList()
        if (language == DictionaryLanguage.ARABIC && !token.all(::isArabicLetterOrMark)) {
            return emptyList()
        }

        val normalizedToken = normalizeForSearch(token, language)
        if (normalizedToken.isEmpty()) return emptyList()

        return dictionaryFor(language)?.wordsFrom(normalizedToken, language, limit).orEmpty()
    }

    @Synchronized
    private fun dictionaryFor(language: DictionaryLanguage): IndexedTextDictionary? {
        if (loadedDictionaries.containsKey(language)) {
            return loadedDictionaries[language]
        }

        val dictionary = runCatching {
            assets.open("dictionaries/${language.assetFileName}").use { stream ->
                IndexedTextDictionary(stream.readBytes())
            }
        }.getOrNull()

        loadedDictionaries[language] = dictionary
        return dictionary
    }

    private fun currentToken(input: String): String {
        if (input.isEmpty()) return ""

        var end = input.length
        val last = input[end - 1]
        if (!isTokenCharacter(last)) return ""

        var start = end - 1
        while (start > 0 && isTokenCharacter(input[start - 1])) {
            start--
        }
        return input.substring(start, end)
    }

    private fun isTokenCharacter(char: Char): Boolean {
        return Character.isLetter(char) || isCombiningMark(char)
    }

    private fun isArabicLetterOrMark(char: Char): Boolean {
        if (isCombiningMark(char)) return true
        if (!Character.isLetter(char)) return false
        return char in '\u0600'..'\u06FF' ||
            char in '\u0750'..'\u077F' ||
            char in '\u08A0'..'\u08FF' ||
            char in '\uFB50'..'\uFDFF' ||
            char in '\uFE70'..'\uFEFF'
    }

    private fun isCombiningMark(char: Char): Boolean {
        return when (Character.getType(char)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
    }

    private fun normalizeForSearch(value: String, language: DictionaryLanguage): String {
        return when (language) {
            DictionaryLanguage.ARABIC -> buildString(value.length) {
                value.forEach { char ->
                    when {
                        char == '\u0640' || isCombiningMark(char) -> Unit
                        char == 'أ' || char == 'إ' || char == 'آ' || char == 'ٱ' -> append('ا')
                        else -> append(char)
                    }
                }
            }
            DictionaryLanguage.ENGLISH,
            DictionaryLanguage.FRENCH -> value.lowercase()
        }
    }

    private inner class IndexedTextDictionary(private val bytes: ByteArray) {
        private val lineStarts: IntArray = buildLineStarts(bytes)

        fun wordsFrom(
            normalizedToken: String,
            language: DictionaryLanguage,
            limit: Int
        ): List<String> {
            if (lineStarts.isEmpty()) return emptyList()

            var low = 0
            var high = lineStarts.size
            while (low < high) {
                val middle = (low + high) ushr 1
                val middleWord = wordAt(middle)
                val middleKey = normalizeForSearch(middleWord, language)
                if (middleKey < normalizedToken) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }

            if (low >= lineStarts.size) return emptyList()

            val result = ArrayList<String>(limit)
            var index = low
            while (index < lineStarts.size && result.size < limit) {
                val word = wordAt(index)
                if (word.isNotEmpty() && (result.isEmpty() || result.last() != word)) {
                    result.add(word)
                }
                index++
            }
            return result
        }

        private fun wordAt(index: Int): String {
            val start = lineStarts[index]
            var end = if (index + 1 < lineStarts.size) lineStarts[index + 1] - 1 else bytes.size
            if (end > start && bytes[end - 1] == '\r'.code.toByte()) {
                end--
            }
            return String(bytes, start, end - start, Charsets.UTF_8)
        }

        private fun buildLineStarts(data: ByteArray): IntArray {
            if (data.isEmpty()) return IntArray(0)

            var lineCount = data.count { it == '\n'.code.toByte() }
            if (data.last() != '\n'.code.toByte()) lineCount++
            if (lineCount == 0) return IntArray(0)

            val starts = IntArray(lineCount)
            starts[0] = 0
            var next = 1
            data.forEachIndexed { index, byte ->
                if (byte == '\n'.code.toByte() && index + 1 < data.size && next < starts.size) {
                    starts[next++] = index + 1
                }
            }
            return if (next == starts.size) starts else starts.copyOf(next)
        }
    }
}
