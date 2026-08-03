from pathlib import Path
p = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/DictionaryManager.kt')
s = p.read_text(encoding='utf-8')
old = '    private val commonBuckets = mutableMapOf<DictionaryLanguage, IndexedBucket?>()\n'
new = old + '    private val coldStartPools = mutableMapOf<DictionaryLanguage, List<String>>()\n'
if s.count(old) != 1:
    raise RuntimeError('unexpected common bucket state')
s = s.replace(old, new, 1)
marker = '    private fun searchVariants(\n'
method = '''    fun coldStartWords(
        language: DictionaryLanguage,
        contextWords: List<String>,
        limit: Int = 8
    ): List<String> {
        if (limit <= 0 || contextWords.isEmpty()) return emptyList()
        val pool = coldStartPoolFor(language)
        if (pool.isEmpty()) return emptyList()

        val recentKeys = contextWords
            .takeLast(3)
            .mapTo(HashSet()) { language.normalize(it) }
        val contextKey = contextWords
            .takeLast(2)
            .joinToString(" ") { language.normalize(it) }
        val positiveHash = contextKey.hashCode().toLong() and 0x7FFFFFFFL
        val offset = (positiveHash % pool.size.toLong()).toInt()

        val result = ArrayList<String>(limit)
        var step = 0
        while (step < pool.size && result.size < limit) {
            val word = pool[(offset + step) % pool.size]
            if (language.normalize(word) !in recentKeys) result.add(word)
            step += 1
        }
        return result
    }

    @Synchronized
    private fun coldStartPoolFor(language: DictionaryLanguage): List<String> {
        coldStartPools[language]?.let { return it }

        val entries = when (language) {
            DictionaryLanguage.ARABIC ->
                commonTopSuggestionIndexFor(language)?.values?.flatten().orEmpty() +
                    topSuggestionIndexFor(language)?.values?.flatten().orEmpty()
            DictionaryLanguage.ENGLISH,
            DictionaryLanguage.FRENCH ->
                topSuggestionIndexFor(language)?.values?.flatten().orEmpty()
        }

        val seen = HashSet<String>()
        val pool = entries
            .sortedWith(
                compareBy<DictionaryEntry>(
                    { if (it.isCommonWord) 0 else 1 },
                    { it.sourceRank },
                    { if (it.isGeneratedDerivative) 1 else 0 },
                    { language.normalize(it.word) }
                )
            )
            .mapNotNull { entry ->
                val key = language.normalize(entry.word)
                entry.word.takeIf { key.isNotEmpty() && seen.add(key) }
            }
            .take(COLD_START_POOL_LIMIT)

        coldStartPools[language] = pool
        return pool
    }

'''
if s.count(marker) != 1:
    raise RuntimeError('unexpected search variant state')
s = s.replace(marker, method + marker, 1)
old_const = '        private const val COMMON_PREFIX_SCAN_LIMIT = 256\n'
new_const = old_const + '        private const val COLD_START_POOL_LIMIT = 36\n'
if s.count(old_const) != 1:
    raise RuntimeError('unexpected constants state')
s = s.replace(old_const, new_const, 1)
p.write_text(s, encoding='utf-8')
