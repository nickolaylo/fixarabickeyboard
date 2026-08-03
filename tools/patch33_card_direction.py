from pathlib import Path
p = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt')
s = p.read_text(encoding='utf-8')
old = '''    private fun LinearLayout.addDictionaryContentToSmartRow(suggestions: List<SuggestionItem>) {
        val visibleSuggestions = suggestions.take(3)
        visibleSuggestions.forEach { suggestion ->
            val onLongClick = suggestion.learnedContextWords?.let {
                { forgetLearnedSuggestion(suggestion) }
            }
            addView(
                makeSuggestionKey(
                    label = suggestion.displayText,
                    onClick = { commitSuggestion(suggestion.commitText) },
                    onLongClick = onLongClick
                ),
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) }
            )
        }
        repeat((3 - visibleSuggestions.size).coerceAtLeast(0)) {
            addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) })
        }
    }
'''
new = '''    private fun LinearLayout.addDictionaryContentToSmartRow(suggestions: List<SuggestionItem>) {
        val visibleSuggestions = suggestions.take(3)
        val missingSlots = (3 - visibleSuggestions.size).coerceAtLeast(0)

        fun addEmptySlot() {
            addView(
                View(this@KeyboardImeService),
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), dp(1), dp(2), dp(1))
                }
            )
        }

        if (activeDictionaryLanguage() == DictionaryLanguage.ARABIC) {
            repeat(missingSlots) { addEmptySlot() }
        }

        visibleSuggestions.forEach { suggestion ->
            val onLongClick = suggestion.learnedContextWords?.let {
                { forgetLearnedSuggestion(suggestion) }
            }
            addView(
                makeSuggestionKey(
                    label = suggestion.displayText,
                    onClick = { commitSuggestion(suggestion.commitText) },
                    onLongClick = onLongClick
                ),
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), dp(1), dp(2), dp(1))
                }
            )
        }

        if (activeDictionaryLanguage() != DictionaryLanguage.ARABIC) {
            repeat(missingSlots) { addEmptySlot() }
        }
    }
'''
if s.count(old) != 1:
    raise RuntimeError('unexpected suggestion row state')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
