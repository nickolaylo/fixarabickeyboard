package com.souadachak.fixarabickeyboard.keyboard

import android.content.Context
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import java.util.zip.GZIPInputStream

enum class DictionaryLanguage(
    val code: String,
    val defaultWords: List<String>
) {
    ARABIC("ar", listOf("السلام", "مرحبا", "شكرا")),
    ENGLISH("en", listOf("hello", "thanks", "please")),
    FRENCH("fr", listOf("bonjour", "merci", "salut"));

    fun normalize(value: String): String {
        val clean = buildString(value.length) {
            value.forEach { char ->
                when {
                    char == '\u0640' || isCombiningMark(char) -> Unit
                    this@DictionaryLanguage == ARABIC && char in listOf('أ', 'إ', 'آ', 'ٱ') -> append('ا')
                    this@DictionaryLanguage == ENGLISH || this@DictionaryLanguage == FRENCH -> append(char.lowercaseChar())
                    else -> append(char)
                }
            }
        }
        return clean
    }

    fun acceptsWord(value: String): Boolean {
        if (value.isBlank()) return false
        return when (this) {
            ARABIC -> value.all { isArabicLetter(it) || isCombiningMark(it) }
            ENGLISH, FRENCH -> value.all {
                Character.isLetter(it) || it == '\'' || it == '’' || it == '-'
            }
        }
    }

    private fun isArabicLetter(char: Char): Boolean {
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
}

data class DictionaryEntry(
    val word: String,
    val isGeneratedDerivative: Boolean
)

/**
 * Central dictionary reader used by every keyboard language.
 *
 * Dictionaries are split into sorted gzip buckets. Only the bucket matching
 * the current token is decompressed, which keeps the multi-million-word Arabic
 * dictionary out of the IME heap.
 */
class DictionaryManager(context: Context) {
    private val assets = context.applicationContext.assets
    private val indexes = mutableMapOf<DictionaryLanguage, LanguageIndex?>()

    private val bucketCache = object : LinkedHashMap<String, IndexedBucket>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IndexedBucket>?): Boolean {
            return size > MAX_CACHED_BUCKETS
        }
    }

    fun preload(language: DictionaryLanguage) {
        indexFor(language)
    }

    fun suggestions(
        input: String,
        language: DictionaryLanguage,
        limit: Int = 6
    ): List<DictionaryEntry> {
        if (limit <= 0) return emptyList()
        val token = currentToken(input, language)
        if (token.isEmpty() || !language.acceptsWord(token)) return emptyList()
        val normalizedToken = language.normalize(token)
        if (normalizedToken.isEmpty()) return emptyList()

        val index = indexFor(language) ?: return emptyList()
        if (index.records.isEmpty()) return emptyList()

        val targetKey = normalizedToken.take(2)
        var recordIndex = index.lowerBound(targetKey)
        if (recordIndex >= index.records.size) return emptyList()

        val result = ArrayList<DictionaryEntry>(limit)
        val seenWords = HashSet<String>(limit * 2)
        var firstBucket = true

        while (recordIndex < index.records.size && result.size < limit) {
            val record = index.records[recordIndex]
            val bucket = bucketFor(language, record.fileName)
            if (bucket != null) {
                val bucketToken = if (firstBucket) normalizedToken else ""
                for (entry in bucket.entriesFrom(bucketToken, language, limit - result.size)) {
                    if (seenWords.add(entry.word)) result.add(entry)
                    if (result.size >= limit) break
                }
            }
            firstBucket = false
            recordIndex++
        }
        return result
    }

    fun currentToken(input: String, language: DictionaryLanguage): String {
        if (input.isEmpty()) return ""
        var end = input.length
        if (!isTokenCharacter(input[end - 1], language)) return ""
        var start = end - 1
        while (start > 0 && isTokenCharacter(input[start - 1], language)) start--
        return input.substring(start, end).trim('\'', '’', '-')
    }

    @Synchronized
    private fun indexFor(language: DictionaryLanguage): LanguageIndex? {
        if (indexes.containsKey(language)) return indexes[language]
        val loaded = runCatching {
            assets.open("dictionaries/${language.code}/index.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                val records = lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) null else BucketRecord(parts[0], parts[1])
                }.toList().sortedBy { it.key }
                LanguageIndex(records)
            }
        }.getOrNull()
        indexes[language] = loaded
        return loaded
    }

    @Synchronized
    private fun bucketFor(language: DictionaryLanguage, fileName: String): IndexedBucket? {
        val cacheKey = "${language.code}/$fileName"
        bucketCache[cacheKey]?.let { return it }

        // Android's asset packager may transparently unpack *.gz files and
        // expose them without the .gz suffix inside the APK. The index keeps
        // the source filename, so resolve both packaged forms centrally for
        // every current and future dictionary language.
        val basePath = "dictionaries/${language.code}/"
        val candidateNames = buildList {
            add(fileName)
            if (fileName.endsWith(".gz")) add(fileName.removeSuffix(".gz"))
        }

        var loaded: IndexedBucket? = null
        for (candidateName in candidateNames) {
            val bytes = runCatching {
                readPossiblyGzippedAsset(basePath + candidateName)
            }.getOrNull() ?: continue
            loaded = IndexedBucket(bytes)
            break
        }

        if (loaded != null) bucketCache[cacheKey] = loaded
        return loaded
    }

    private fun readPossiblyGzippedAsset(path: String): ByteArray {
        return assets.open(path).use { raw ->
            BufferedInputStream(raw, 16 * 1024).use { buffered ->
                buffered.mark(2)
                val first = buffered.read()
                val second = buffered.read()
                buffered.reset()

                val input = if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
                    GZIPInputStream(buffered)
                } else {
                    buffered
                }

                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private fun isTokenCharacter(char: Char, language: DictionaryLanguage): Boolean {
        return when (language) {
            DictionaryLanguage.ARABIC -> language.acceptsWord(char.toString())
            DictionaryLanguage.ENGLISH,
            DictionaryLanguage.FRENCH -> Character.isLetter(char) || char == '\'' || char == '’' || char == '-'
        }
    }

    private data class BucketRecord(val key: String, val fileName: String)

    private class LanguageIndex(val records: List<BucketRecord>) {
        fun lowerBound(target: String): Int {
            var low = 0
            var high = records.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (records[middle].key < target) low = middle + 1 else high = middle
            }
            return low
        }
    }

    private class IndexedBucket(private val bytes: ByteArray) {
        private val lineStarts: IntArray = buildLineStarts(bytes)

        fun entriesFrom(
            normalizedToken: String,
            language: DictionaryLanguage,
            limit: Int
        ): List<DictionaryEntry> {
            if (lineStarts.isEmpty() || limit <= 0) return emptyList()
            var low = 0
            var high = lineStarts.size
            if (normalizedToken.isNotEmpty()) {
                while (low < high) {
                    val middle = (low + high) ushr 1
                    val entry = entryAt(middle)
                    val key = language.normalize(entry.word)
                    if (key < normalizedToken) low = middle + 1 else high = middle
                }
            }

            val result = ArrayList<DictionaryEntry>(limit)
            var index = low
            while (index < lineStarts.size && result.size < limit) {
                result.add(entryAt(index))
                index++
            }
            return result
        }

        private fun entryAt(index: Int): DictionaryEntry {
            val start = lineStarts[index]
            var end = if (index + 1 < lineStarts.size) lineStarts[index + 1] - 1 else bytes.size
            if (end > start && bytes[end - 1] == '\n'.code.toByte()) end--
            if (end > start && bytes[end - 1] == '\r'.code.toByte()) end--
            val line = String(bytes, start, end - start, Charsets.UTF_8)
            val tab = line.indexOf('\t')
            if (tab < 0) return DictionaryEntry(line, false)
            val priority = line.substring(0, tab).toIntOrNull() ?: 0
            return DictionaryEntry(line.substring(tab + 1), priority > 0)
        }

        private fun buildLineStarts(data: ByteArray): IntArray {
            if (data.isEmpty()) return IntArray(0)
            var lineCount = data.count { it == '\n'.code.toByte() }
            if (data.last() != '\n'.code.toByte()) lineCount++
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

    companion object {
        private const val MAX_CACHED_BUCKETS = 2
        private const val GZIP_MAGIC_FIRST = 0x1F
        private const val GZIP_MAGIC_SECOND = 0x8B
    }
}
