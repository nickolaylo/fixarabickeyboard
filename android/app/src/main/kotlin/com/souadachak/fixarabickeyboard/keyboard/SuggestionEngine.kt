package com.souadachak.fixarabickeyboard.keyboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo

data class SuggestionItem(
    val displayText: String,
    val commitText: String = displayText
)

/** Central suggestion policy shared by Arabic and every present/future language. */
class SuggestionEngine(context: Context) {
    private val dictionaries = DictionaryManager(context)
    private val nextWordStore = LocalNextWordStore(context)

    fun preload(language: DictionaryLanguage) {
        dictionaries.preload(language)
    }

    fun suggestions(
        input: String,
        language: DictionaryLanguage,
        editorInfo: EditorInfo?
    ): List<SuggestionItem> {
        if (EditorPrivacy.isPasswordOrNumeric(editorInfo)) return emptyList()

        emailSuggestions(input)?.let { return it }
        if ('@' in input.takeLastWhile { !it.isWhitespace() }) return emptyList()

        val currentToken = dictionaries.currentToken(input, language)
        val words = wordsIn(input, language)
        val completedWords = if (currentToken.isNotEmpty() && words.isNotEmpty()) {
            words.dropLast(1)
        } else {
            words
        }

        val learned = if (EditorPrivacy.canUsePersonalizedSuggestions(editorInfo)) {
            val completionFromContext = bestLearnedFromContext(language, completedWords)
                ?.takeIf {
                    currentToken.isEmpty() ||
                        language.normalize(it).startsWith(language.normalize(currentToken))
                }

            // Keeps the already approved behavior: typing the complete word
            // "السلام" can immediately suggest its learned follower "عليكم".
            // It also enables: "الرجال و" -> "النساء" before another space.
            val nextAfterCurrentContext = currentToken
                .takeIf(String::isNotEmpty)
                ?.let { token -> bestLearnedFromContext(language, completedWords + token) }

            completionFromContext ?: nextAfterCurrentContext
        } else {
            null
        }

        val dictionaryWords = dictionaries.suggestions(input, language, 8)
            .map { it.word }
            .distinct()
            .filterNot { learned != null && language.normalize(it) == language.normalize(learned) }

        if (currentToken.isEmpty()) {
            val idlePool = buildList {
                learned?.let(::add)
                addAll(language.defaultWords)
            }
            return arrangeSuggestions(
                learned = learned,
                dictionaryWords = idlePool,
                language = language,
                allowDefaultFallbacks = true
            )
        }

        return arrangeSuggestions(
            learned = learned,
            dictionaryWords = dictionaryWords,
            language = language,
            allowDefaultFallbacks = false
        )
    }

    private fun bestLearnedFromContext(
        language: DictionaryLanguage,
        contextWords: List<String>
    ): String? {
        if (contextWords.isEmpty()) return null

        if (contextWords.size >= 2) {
            nextWordStore.bestNextWord(language, contextWords.takeLast(2))?.let { return it }
        }

        val lastWord = contextWords.last()
        // One-letter connectors such as Arabic "و" are useful only inside
        // a two-word context. Avoid making them broad global predictors.
        if (lastWord.length >= 2) {
            return nextWordStore.bestNextWord(language, listOf(lastWord))
        }
        return null
    }

    private fun arrangeSuggestions(
        learned: String?,
        dictionaryWords: List<String>,
        language: DictionaryLanguage,
        allowDefaultFallbacks: Boolean
    ): List<SuggestionItem> {
        val firstPreferred = learned ?: dictionaryWords.getOrNull(1)
        val middlePreferred = dictionaryWords.getOrNull(0)
        val thirdPreferred = dictionaryWords.getOrNull(2)
        val pool = buildList {
            addAll(dictionaryWords)
            if (allowDefaultFallbacks) addAll(language.defaultWords)
        }

        val used = HashSet<String>()
        fun choose(preferred: String?, fallbacks: List<String>): SuggestionItem? {
            val candidate = sequenceOf(preferred).plus(fallbacks.asSequence())
                .filterNotNull()
                .firstOrNull { word ->
                    val key = language.normalize(word)
                    key.isNotEmpty() && used.add(key)
                }
            return candidate?.let(::SuggestionItem)
        }

        val middleKey = middlePreferred?.let(language::normalize)
        val firstFallbacks = pool.filterNot { language.normalize(it) == middleKey }
        val logicalFirst = choose(firstPreferred, firstFallbacks)
        val logicalMiddle = choose(middlePreferred, pool)
        val logicalThird = choose(thirdPreferred, pool)
        val logicalOrder = listOfNotNull(logicalFirst, logicalMiddle, logicalThird).take(3)

        return if (language == DictionaryLanguage.ARABIC) {
            logicalOrder.reversed()
        } else {
            logicalOrder
        }
    }

    fun learnFromCompletedText(
        textBeforeBoundary: String,
        language: DictionaryLanguage,
        editorInfo: EditorInfo?
    ) {
        if (!EditorPrivacy.canLearn(editorInfo, textBeforeBoundary)) return
        val words = wordsIn(textBeforeBoundary, language)
        if (words.size < 2) return

        val nextWord = words.last()
        if (!isSafeLearnableWord(nextWord, language)) return
        recordLearnedContexts(
            language = language,
            contextWords = words.dropLast(1),
            nextWord = nextWord
        )
    }

    fun learnSelectedWord(
        sourceBeforeSelection: String,
        selectedWord: String,
        language: DictionaryLanguage,
        editorInfo: EditorInfo?
    ) {
        if (!EditorPrivacy.canLearn(editorInfo, sourceBeforeSelection) ||
            !isSafeLearnableWord(selectedWord, language)
        ) return

        val currentToken = dictionaries.currentToken(sourceBeforeSelection, language)
        val words = wordsIn(sourceBeforeSelection, language)
        val completedWords = if (currentToken.isNotEmpty() && words.isNotEmpty()) {
            words.dropLast(1)
        } else {
            words
        }
        recordLearnedContexts(language, completedWords, selectedWord)
    }

    private fun recordLearnedContexts(
        language: DictionaryLanguage,
        contextWords: List<String>,
        nextWord: String
    ) {
        if (contextWords.isEmpty()) return

        val lastWord = contextWords.last()
        if (isSafeStandaloneContextWord(lastWord, language)) {
            nextWordStore.recordContext(language, listOf(lastWord), nextWord)
        }

        if (contextWords.size >= 2) {
            val twoWordContext = contextWords.takeLast(2)
            if (twoWordContext.all { isSafeContextWord(it, language) }) {
                nextWordStore.recordContext(language, twoWordContext, nextWord)
            }
        }
    }

    private fun emailSuggestions(input: String): List<SuggestionItem>? {
        val token = input.takeLastWhile { !it.isWhitespace() }
        val at = token.lastIndexOf('@')
        if (at <= 0 || token.indexOf('@') != at) return null

        val localPart = token.substring(0, at)
        val domainPrefix = token.substring(at + 1).lowercase()
        if (!isValidEmailLocalPart(localPart)) return null
        if (domainPrefix.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) return null

        val matches = EMAIL_DOMAINS.filter { it.startsWith(domainPrefix) }.take(3)
        if (matches.isEmpty()) return null
        return matches.map { domain ->
            SuggestionItem(displayText = domain, commitText = "$localPart@$domain")
        }
    }

    private fun isValidEmailLocalPart(localPart: String): Boolean {
        if (localPart.isBlank() || localPart.length > 64) return false
        if (localPart.first() == '.' || localPart.last() == '.' || ".." in localPart) return false
        return localPart.all { char ->
            char.isLetterOrDigit() || char in EMAIL_LOCAL_SYMBOLS
        }
    }

    private fun wordsIn(input: String, language: DictionaryLanguage): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val word = current.toString().trim('\'', '’', '-')
            if (word.isNotEmpty() && language.acceptsWord(word)) result.add(word)
            current.setLength(0)
        }
        input.forEach { char ->
            if (Character.isLetter(char) || char == '\'' || char == '’' || char == '-') {
                current.append(char)
            } else {
                flush()
            }
        }
        flush()
        return result
    }

    private fun isSafeContextWord(word: String, language: DictionaryLanguage): Boolean {
        if (word.isEmpty() || !language.acceptsWord(word)) return false
        if (word.any { it.isDigit() || it == '@' }) return false
        return word.first().isLetter() && word.last().isLetter()
    }

    private fun isSafeStandaloneContextWord(
        word: String,
        language: DictionaryLanguage
    ): Boolean {
        return word.length >= 2 && isSafeContextWord(word, language)
    }

    private fun isSafeLearnableWord(word: String, language: DictionaryLanguage): Boolean {
        return word.length >= 2 && isSafeContextWord(word, language)
    }

    private object EditorPrivacy {
        fun isPasswordOrNumeric(editorInfo: EditorInfo?): Boolean {
            val inputType = editorInfo?.inputType ?: return false
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            if (inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            ) return true
            if (inputClass != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
                else -> false
            }
        }

        fun canUsePersonalizedSuggestions(editorInfo: EditorInfo?): Boolean {
            if (isPasswordOrNumeric(editorInfo)) return false
            val info = editorInfo ?: return true
            if ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false
            val inputType = info.inputType
            if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI -> false
                else -> true
            }
        }

        fun canLearn(editorInfo: EditorInfo?, context: String): Boolean {
            if (!canUsePersonalizedSuggestions(editorInfo)) return false
            if (context.any { it.isDigit() || it == '@' }) return false
            val lower = context.lowercase()
            if ("://" in lower || "www." in lower) return false
            return true
        }
    }

    companion object {
        private val EMAIL_DOMAINS = listOf("gmail.com", "outlook.com", "yahoo.com")
        private val EMAIL_LOCAL_SYMBOLS = setOf('.', '_', '-', '+')
    }
}
