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
    val isGeneratedDerivative: Boolean,
    val sourceRank: Int = Int.MAX_VALUE
)

/**
 * Central dictionary reader used by every keyboard language.
 *
 * Dictionaries stay split into sorted buckets. Each bucket now carries its
 * source rank inside the existing file, so quality ranking does not require a
 * second parallel asset tree or extra runtime lookups.
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
        if (index.size == 0) return emptyList()

        val result = ArrayList<DictionaryEntry>(limit)
        val seenWords = HashSet<String>(limit * 3)

        // Real prefix completions always come first. Arabic additionally
        // resolves attached particles/articles against the same base lexicon:
        // "الرجا" -> search "رجا" -> surface result "الرجال".
        for (variant in searchVariants(normalizedToken, language)) {
            val candidates = prefixMatches(
                normalizedToken = variant.lookupToken,
                language = language,
                index = index,
                scanLimit = PREFIX_SCAN_LIMIT
            ).sortedWith(matchComparator(variant.lookupToken, language))

            for (entry in candidates) {
                val normalizedEntry = language.normalize(entry.word)
                if (variant.displayPrefix.isNotEmpty() && normalizedEntry == variant.lookupToken) continue

                val surfaceWord = variant.displayPrefix + entry.word
                val normalizedSurface = language.normalize(surfaceWord)
                if (!normalizedSurface.startsWith(normalizedToken)) continue
                if (!seenWords.add(normalizedSurface)) continue

                result.add(
                    DictionaryEntry(
                        word = surfaceWord,
                        isGeneratedDerivative = entry.isGeneratedDerivative || variant.displayPrefix.isNotEmpty(),
                        sourceRank = entry.sourceRank
                    )
                )
                if (result.size >= limit) return result
            }
        }

        // Preserve the agreed lexicographic-neighbour fallback only after true
        // prefix completions have been exhausted.
        for (entry in nearestEntries(normalizedToken, language, index, limit * 2)) {
            val key = language.normalize(entry.word)
            if (seenWords.add(key)) result.add(entry)
            if (result.size >= limit) break
        }
        return result
    }

    private fun searchVariants(
        normalizedToken: String,
        language: DictionaryLanguage
    ): List<SearchVariant> {
        val variants = ArrayList<SearchVariant>()
        variants.add(SearchVariant(normalizedToken, ""))
        if (language != DictionaryLanguage.ARABIC) return variants

        for (prefix in ARABIC_SEARCH_PREFIXES) {
            if (normalizedToken.length <= prefix.length || !normalizedToken.startsWith(prefix)) continue
            val stripped = normalizedToken.substring(prefix.length)
            if (stripped.length < MIN_STRIPPED_PREFIX_LENGTH) continue
            variants.add(SearchVariant(stripped, prefix))
        }
        return variants.distinctBy { it.lookupToken to it.displayPrefix }
    }

    private fun prefixMatches(
        normalizedToken: String,
        language: DictionaryLanguage,
        index: LanguageIndex,
        scanLimit: Int
    ): List<DictionaryEntry> {
        if (normalizedToken.isEmpty() || scanLimit <= 0) return emptyList()
        val targetKey = normalizedToken.take(2)
        var recordIndex = index.lowerBound(targetKey)
        val result = ArrayList<DictionaryEntry>(minOf(scanLimit, 64))

        while (recordIndex < index.size && result.size < scanLimit) {
            val record = index.recordAt(recordIndex)
            val keyMatches = if (normalizedToken.length == 1) {
                record.key.startsWith(normalizedToken)
            } else {
                record.key == targetKey
            }
            if (!keyMatches) break

            val bucket = bucketFor(language, record.fileName)
            if (bucket != null) {
                result.addAll(
                    bucket.entriesStartingWith(
                        normalizedToken = normalizedToken,
                        language = language,
                        limit = scanLimit - result.size
                    )
                )
            }
            recordIndex++
        }
        return result
    }

    private fun nearestEntries(
        normalizedToken: String,
        language: DictionaryLanguage,
        index: LanguageIndex,
        limit: Int
    ): List<DictionaryEntry> {
        if (limit <= 0) return emptyList()
        val targetKey = normalizedToken.take(2)
        var recordIndex = index.lowerBound(targetKey)
        val result = ArrayList<DictionaryEntry>(limit)
        var firstBucket = true

        while (recordIndex < index.size && result.size < limit) {
            val record = index.recordAt(recordIndex)
            val bucket = bucketFor(language, record.fileName)
            if (bucket != null) {
                val bucketToken = if (firstBucket) normalizedToken else ""
                result.addAll(
                    bucket.entriesFrom(
                        normalizedToken = bucketToken,
                        language = language,
                        limit = limit - result.size
                    )
                )
            }
            firstBucket = false
            recordIndex++
        }
        return result
    }

    private fun matchComparator(
        normalizedToken: String,
        language: DictionaryLanguage
    ): Comparator<DictionaryEntry> {
        return compareBy<DictionaryEntry>(
            { if (language.normalize(it.word) == normalizedToken) 0 else 1 },
            { if (it.isGeneratedDerivative) 1 else 0 },
            { (language.normalize(it.word).length - normalizedToken.length).coerceAtLeast(0) },
            { it.sourceRank },
            { language.normalize(it.word) }
        )
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

    private class LanguageIndex(private val records: List<BucketRecord>) {
        val size: Int
            get() = records.size

        fun recordAt(index: Int): BucketRecord = records[index]

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

        fun entriesStartingWith(
            normalizedToken: String,
            language: DictionaryLanguage,
            limit: Int
        ): List<DictionaryEntry> {
            if (lineStarts.isEmpty() || normalizedToken.isEmpty() || limit <= 0) return emptyList()
            var low = lowerBound(normalizedToken, language)
            val result = ArrayList<DictionaryEntry>(limit)
            while (low < lineStarts.size && result.size < limit) {
                val entry = entryAt(low)
                val key = language.normalize(entry.word)
                if (!key.startsWith(normalizedToken)) break
                result.add(entry)
                low++
            }
            return result
        }

        fun entriesFrom(
            normalizedToken: String,
            language: DictionaryLanguage,
            limit: Int
        ): List<DictionaryEntry> {
            if (lineStarts.isEmpty() || limit <= 0) return emptyList()
            val low = if (normalizedToken.isEmpty()) 0 else lowerBound(normalizedToken, language)

            val result = ArrayList<DictionaryEntry>(limit)
            var index = low
            while (index < lineStarts.size && result.size < limit) {
                result.add(entryAt(index))
                index++
            }
            return result
        }

        private fun lowerBound(
            normalizedToken: String,
            language: DictionaryLanguage
        ): Int {
            var low = 0
            var high = lineStarts.size
            while (low < high) {
                val middle = (low + high) ushr 1
                val entry = entryAt(middle)
                val key = language.normalize(entry.word)
                if (key < normalizedToken) low = middle + 1 else high = middle
            }
            return low
        }

        private fun entryAt(index: Int): DictionaryEntry {
            val start = lineStarts[index]
            var end = if (index + 1 < lineStarts.size) lineStarts[index + 1] - 1 else bytes.size
            if (end > start && bytes[end - 1] == '\n'.code.toByte()) end--
            if (end > start && bytes[end - 1] == '\r'.code.toByte()) end--
            val line = String(bytes, start, end - start, Charsets.UTF_8)
            val firstTab = line.indexOf('\t')
            if (firstTab < 0) return DictionaryEntry(line, false)

            val priority = line.substring(0, firstTab).toIntOrNull() ?: 0
            val secondTab = line.indexOf('\t', firstTab + 1)
            if (secondTab < 0) {
                return DictionaryEntry(
                    word = line.substring(firstTab + 1),
                    isGeneratedDerivative = priority > 0
                )
            }

            val sourceRank = line.substring(firstTab + 1, secondTab).toIntOrNull() ?: Int.MAX_VALUE
            return DictionaryEntry(
                word = line.substring(secondTab + 1),
                isGeneratedDerivative = priority > 0,
                sourceRank = sourceRank
            )
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

    private data class SearchVariant(
        val lookupToken: String,
        val displayPrefix: String
    )

    companion object {
        private const val MAX_CACHED_BUCKETS = 2
        private const val PREFIX_SCAN_LIMIT = 512
        private const val MIN_STRIPPED_PREFIX_LENGTH = 2
        private val ARABIC_SEARCH_PREFIXES = listOf(
            "وال", "فال", "بال", "كال", "لل", "ال",
            "و", "ف", "ب", "ك", "ل"
        )
        private const val GZIP_MAGIC_FIRST = 0x1F
        private const val GZIP_MAGIC_SECOND = 0x8B
    }
}
