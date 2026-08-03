from pathlib import Path
p = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/SuggestionEngine.kt')
s = p.read_text(encoding='utf-8')
old = '''        if (currentToken.isEmpty()) {
            // A completed word with no learned continuation must not fall back to
            // the same generic greetings after every space. Keep only a genuinely
            // learned next-word candidate; otherwise the toolbar is shown.
            return learned?.let { candidate ->
                listOf(
                    SuggestionItem(
                        displayText = candidate.word,
                        commitText = candidate.word,
                        learnedContextWords = candidate.contextWords
                    )
                )
            }.orEmpty()
        }
'''
new = '''        if (currentToken.isEmpty()) {
            if (completedWords.isEmpty()) return emptyList()
            val coldStartWords = dictionaries.coldStartWords(
                language = language,
                contextWords = completedWords,
                limit = 8
            )
            return arrangeSuggestions(
                learned = learned,
                dictionaryWords = coldStartWords,
                language = language,
                allowDefaultFallbacks = false
            )
        }
'''
if s.count(old) != 1:
    raise RuntimeError('unexpected SuggestionEngine state')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
