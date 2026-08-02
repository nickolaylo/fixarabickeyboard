from pathlib import Path
import re

KOTLIN_PATH = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
JOURNAL_PATH = Path("PROJECT_JOURNAL.md")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


text = KOTLIN_PATH.read_text(encoding="utf-8")

text = replace_once(
    text,
    "import android.content.Intent\nimport android.graphics.Color",
    "import android.content.Intent\nimport android.icu.text.BreakIterator\nimport android.graphics.Color",
    "BreakIterator import",
)

text = replace_once(
    text,
    "    private var repairExpanded: Boolean = false\n    private var morePanelExpanded: Boolean = false\n    private var repairEditText: EditText? = null",
    "    private var repairExpanded: Boolean = false\n    private var morePanelExpanded: Boolean = false\n    private var emojiPanelExpanded: Boolean = false\n    private var activeEmojiCategory: Int = 0\n    private var repairEditText: EditText? = null",
    "emoji panel state",
)

text = replace_once(
    text,
    "    private val suggestionRefreshHandler = Handler(Looper.getMainLooper())\n    private var suggestionRefreshRunnable: Runnable? = null\n\n    private companion object {\n        const val SUGGESTION_CONTEXT_LIMIT = 160\n        const val KEY_AREA_HEIGHT_DP = 264\n    }",
    r'''    private val suggestionRefreshHandler = Handler(Looper.getMainLooper())
    private var suggestionRefreshRunnable: Runnable? = null
    private var emojiRecentRow: LinearLayout? = null

    private companion object {
        const val SUGGESTION_CONTEXT_LIMIT = 160
        const val KEY_AREA_HEIGHT_DP = 264
        const val RECENT_EMOJIS_KEY = "recent_emojis"
        const val EMOJI_SEPARATOR = "\u001F"

        val EMOJI_CATEGORY_ICONS = listOf("🙂", "👍", "❤️", "🎉")
        val EMOJI_CATEGORIES = listOf(
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂",
                "🙂", "🙃", "😉", "😊", "😍", "🥰", "😘",
                "😎", "🤔", "😐", "😢", "😭", "😡", "😴",
                "🤩", "🥳", "🤗", "🤭", "🤫", "😇", "😌"
            ),
            listOf(
                "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘",
                "👋", "🤚", "🖐️", "✋", "🙌", "👏", "🤝",
                "💪", "🙏", "👆", "👇", "👈", "👉", "✍️",
                "🤙", "🫶", "👊", "🤛", "🤜", "☝️", "👐"
            ),
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤",
                "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓",
                "💗", "💖", "💘", "💝", "💟", "💯", "✅",
                "❌", "⚠️", "⭐", "🌟", "✨", "❗", "❓"
            ),
            listOf(
                "🎉", "🎊", "🎈", "🎁", "🏆", "⚽", "🏀",
                "🎮", "📱", "💻", "📷", "📚", "🔥", "🌹",
                "☀️", "🌙", "☕", "🍕", "🍔", "🚗", "✈️",
                "🏠", "💡", "📌", "📍", "⏰", "🔔", "🛒"
            )
        )
    }''',
    "emoji constants",
)

text = replace_once(
    text,
    "            if (morePanelExpanded) makeMoreToolsPanel() else makeKeyboardKeyArea(),",
    "            when {\n                emojiPanelExpanded -> makeEmojiPanel()\n                morePanelExpanded -> makeMoreToolsPanel()\n                else -> makeKeyboardKeyArea()\n            },",
    "initial key-area content",
)

text = replace_once(
    text,
    "            toolsExpanded = true\n            morePanelExpanded = false\n            lastSmartRowMode = SmartRowMode.TOOLS",
    "            toolsExpanded = true\n            morePanelExpanded = false\n            emojiPanelExpanded = false\n            activeEmojiCategory = 0\n            emojiRecentRow = null\n            lastSmartRowMode = SmartRowMode.TOOLS",
    "new editor resets emoji panel",
)

text = replace_once(
    text,
    r'''    private fun repairSlotHeightForText(text: String): Int {
        val lines = text
            .lineSequence()
            .sumOf { line -> ((line.length / 34) + 1).coerceAtLeast(1) }
            .coerceIn(1, 2)
        return if (lines == 1) dp(45) else dp(69)
    }''',
    r'''    private fun repairSlotHeightForText(text: String): Int {
        val explicitLines = (text.count { it == '\n' } + 1).coerceIn(1, 2)
        return if (explicitLines == 1) dp(45) else dp(69)
    }''',
    "manual-only repair lines",
)

text = replace_once(
    text,
    "            setHorizontallyScrolling(false)\n            isVerticalScrollBarEnabled = true",
    "            setHorizontallyScrolling(true)\n            isHorizontalScrollBarEnabled = false\n            isVerticalScrollBarEnabled = true",
    "disable automatic visual wrapping",
)

text = replace_once(
    text,
    "            makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.toolbarIcon) { handleKey(\"🙂\") },",
    "            makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.toolbarIcon) { openEmojiPanel() },",
    "toolbar emoji opens panel",
)

text = replace_once(
    text,
    r'''    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }''',
    r'''    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }''',
    "smart row while auxiliary panel open",
)

emoji_panel_code = r'''

    private fun makeEmojiPanel(): LinearLayout {
        val recentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        emojiRecentRow = recentContainer

        val recentLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(this@KeyboardImeService).apply {
                    text = moreToolLabel("الأخيرة", "Recent", "Récents")
                    gravity = Gravity.CENTER
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(KeyboardColors.textMuted)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.MATCH_PARENT)
            )
            addView(
                recentContainer,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        renderRecentEmojis(recentContainer)

        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            EMOJI_CATEGORY_ICONS.forEachIndexed { index, icon ->
                addView(
                    TextView(this@KeyboardImeService).apply {
                        text = icon
                        gravity = Gravity.CENTER
                        textSize = 20f
                        includeFontPadding = false
                        background = roundedBackground(
                            if (index == activeEmojiCategory) KeyboardColors.keyPressed else KeyboardColors.specialKey,
                            dp(10)
                        )
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            activeEmojiCategory = index
                            refreshKeyAreaContent()
                        }
                        setOnTouchListener { view, event -> animatePress(view, event, null, false) }
                    },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                        setMargins(dp(3), dp(1), dp(3), dp(1))
                    }
                )
            }
        }

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val emojis = EMOJI_CATEGORIES[activeEmojiCategory]
            repeat(4) { rowIndex ->
                val row = LinearLayout(this@KeyboardImeService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                val rowItems = emojis.drop(rowIndex * 7).take(7)
                rowItems.forEach { emoji ->
                    row.addView(
                        makeEmojiKey(emoji),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            setMargins(dp(2), dp(2), dp(2), dp(2))
                        }
                    )
                }
                repeat((7 - rowItems.size).coerceAtLeast(0)) {
                    row.addView(
                        View(this@KeyboardImeService),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            setMargins(dp(2), dp(2), dp(2), dp(2))
                        }
                    )
                }
                addView(
                    row,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                )
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            background = roundedBackground(KeyboardColors.panel, dp(14))
            addView(recentLine, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
            addView(categoryRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun makeEmojiKey(emoji: String, compact: Boolean = false): TextView {
        return TextView(this).apply {
            text = emoji
            gravity = Gravity.CENTER
            textSize = if (compact) 20f else 23f
            includeFontPadding = false
            background = roundedBackground(KeyboardColors.key, dp(9))
            isClickable = true
            isFocusable = true
            setOnClickListener { insertEmoji(emoji) }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun renderRecentEmojis(row: LinearLayout) {
        row.removeAllViews()
        val recent = recentEmojis().take(6)
        if (recent.isEmpty()) {
            row.addView(
                TextView(this).apply {
                    text = moreToolLabel("لا توجد رموز حديثة", "No recent emoji", "Aucun emoji récent")
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(KeyboardColors.textMuted)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
            return
        }
        recent.forEach { emoji ->
            row.addView(
                makeEmojiKey(emoji, compact = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }
    }

    private fun openEmojiPanel() {
        morePanelExpanded = false
        emojiPanelExpanded = true
        refreshKeyAreaContent()
    }

    private fun insertEmoji(emoji: String) {
        if (repairExpanded) {
            val edit = repairEditText ?: return
            val editable = edit.text ?: return
            val start = kotlin.math.min(
                edit.selectionStart.coerceIn(0, editable.length),
                edit.selectionEnd.coerceIn(0, editable.length)
            )
            val end = kotlin.math.max(
                edit.selectionStart.coerceIn(0, editable.length),
                edit.selectionEnd.coerceIn(0, editable.length)
            )
            editable.replace(start, end, emoji)
            edit.setSelection((start + emoji.length).coerceAtMost(editable.length))
            repairBuffer = editable.toString()
        } else {
            currentInputConnection?.commitText(emoji, 1)
            typedText.append(emoji)
        }
        rememberEmoji(emoji)
        scheduleSuggestionRefresh()
    }

    private fun recentEmojis(): List<String> {
        return prefs.getString(RECENT_EMOJIS_KEY, "")
            .orEmpty()
            .split(EMOJI_SEPARATOR)
            .filter(String::isNotEmpty)
    }

    private fun rememberEmoji(emoji: String) {
        val updated = (listOf(emoji) + recentEmojis().filterNot { it == emoji }).take(6)
        prefs.edit().putString(RECENT_EMOJIS_KEY, updated.joinToString(EMOJI_SEPARATOR)).apply()
        emojiRecentRow?.post { row -> renderRecentEmojis(row) }
    }
'''

text = replace_once(
    text,
    "\n    private fun makeMoreToolsPanel(): LinearLayout {",
    emoji_panel_code + "\n    private fun makeMoreToolsPanel(): LinearLayout {",
    "emoji panel functions",
)

old_bottom = r'''    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeCommaEmojiKey(comma), bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        addView(makePrimaryActionButton(), bottomParams(dp(64)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        }
    }'''
new_bottom = r'''    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeCommaEmojiKey(comma), bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        }
        addView(makePrimaryActionButton(), bottomParams(dp(64)))
    }'''
text = replace_once(text, old_bottom, new_bottom, "swap repair action buttons")

text = replace_once(
    text,
    r'''            contentDescription = moreToolLabel(
                "فاصلة، ضغط مطول لإدخال رمز تعبيري",
                "Comma, long press for emoji",
                "Virgule, appui long pour emoji"
            )''',
    r'''            contentDescription = moreToolLabel(
                "فاصلة، ضغط مطول لفتح الرموز التعبيرية",
                "Comma, long press to open emoji",
                "Virgule, appui long pour ouvrir les emojis"
            )''',
    "comma accessibility label",
)

text = replace_once(
    text,
    r'''            setOnLongClickListener {
                handleKey("🙂")
                true
            }''',
    r'''            setOnLongClickListener {
                openEmojiPanel()
                true
            }''',
    "comma long press opens panel",
)

old_more_button = r'''    private fun makeMoreToolsButton(): ImageButton {
        return ImageButton(this).apply {
            if (morePanelExpanded) {
                setImageResource(R.drawable.ic_keyboard_expand_less)
                rotation = -90f
            } else {
                setImageResource(R.drawable.ic_keyboard_more_tools)
                rotation = 0f
            }
            setColorFilter(KeyboardColors.toolbarIcon)
            background = roundedBackground(Color.TRANSPARENT, dp(14))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = moreToolLabel(
                if (morePanelExpanded) "رجوع" else "المزيد",
                if (morePanelExpanded) "Back" else "More",
                if (morePanelExpanded) "Retour" else "Plus"
            )
            setOnClickListener { toggleMorePanel() }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }'''
new_more_button = r'''    private fun makeMoreToolsButton(): ImageButton {
        return ImageButton(this).apply {
            val auxiliaryPanelOpen = morePanelExpanded || emojiPanelExpanded
            if (auxiliaryPanelOpen) {
                setImageResource(R.drawable.ic_keyboard_expand_less)
                rotation = -90f
            } else {
                setImageResource(R.drawable.ic_keyboard_more_tools)
                rotation = 0f
            }
            setColorFilter(KeyboardColors.toolbarIcon)
            background = roundedBackground(Color.TRANSPARENT, dp(14))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = moreToolLabel(
                if (auxiliaryPanelOpen) "رجوع" else "المزيد",
                if (auxiliaryPanelOpen) "Back" else "More",
                if (auxiliaryPanelOpen) "Retour" else "Plus"
            )
            setOnClickListener {
                if (auxiliaryPanelOpen) closeAuxiliaryPanel() else toggleMorePanel()
            }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }'''
text = replace_once(text, old_more_button, new_more_button, "auxiliary back button")

old_toggle = r'''    private fun toggleMorePanel() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        morePanelExpanded = !morePanelExpanded
        refreshKeyAreaContent()
    }

    private fun refreshKeyAreaContent() {
        val container = keyAreaContainer ?: return
        container.removeAllViews()
        container.addView(
            if (morePanelExpanded) makeMoreToolsPanel() else makeKeyboardKeyArea(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val sourceText = currentSuggestionSource()
        val mode = if (morePanelExpanded) SmartRowMode.TOOLS else smartRowMode(sourceText)
        replaceSmartRow(mode, if (morePanelExpanded) "" else sourceText)
    }'''
new_toggle = r'''    private fun toggleMorePanel() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        emojiPanelExpanded = false
        emojiRecentRow = null
        morePanelExpanded = !morePanelExpanded
        refreshKeyAreaContent()
    }

    private fun closeAuxiliaryPanel() {
        morePanelExpanded = false
        emojiPanelExpanded = false
        emojiRecentRow = null
        refreshKeyAreaContent()
    }

    private fun refreshKeyAreaContent() {
        val container = keyAreaContainer ?: return
        container.removeAllViews()
        container.addView(
            when {
                emojiPanelExpanded -> makeEmojiPanel()
                morePanelExpanded -> makeMoreToolsPanel()
                else -> makeKeyboardKeyArea()
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val auxiliaryPanelOpen = morePanelExpanded || emojiPanelExpanded
        val sourceText = currentSuggestionSource()
        val mode = if (auxiliaryPanelOpen) SmartRowMode.TOOLS else smartRowMode(sourceText)
        replaceSmartRow(mode, if (auxiliaryPanelOpen) "" else sourceText)
    }'''
text = replace_once(text, old_toggle, new_toggle, "auxiliary panel switching")

text = replace_once(
    text,
    "        repairExpanded = !repairExpanded\n        morePanelExpanded = false\n        prefs.edit()",
    "        repairExpanded = !repairExpanded\n        morePanelExpanded = false\n        emojiPanelExpanded = false\n        emojiRecentRow = null\n        prefs.edit()",
    "close emoji panel with repair toggle",
)

old_repair_keys = r'''    private fun handleRepairBoxKey(key: String) {
        val edit = repairEditText ?: return
        val editable = edit.text ?: return
        val cursor = edit.selectionStart.coerceAtLeast(0)
        when (key) {
            "⌫" -> {
                val start = edit.selectionStart.coerceAtLeast(0)
                val end = edit.selectionEnd.coerceAtLeast(0)
                if (start != end) {
                    editable.delete(kotlin.math.min(start, end), kotlin.math.max(start, end))
                } else if (cursor > 0) {
                    editable.delete(cursor - 1, cursor)
                } else if (editable.isEmpty()) {
                    handleBackspace()
                }
            }
            "مسافة" -> editable.insert(cursor, " ")
            "↵" -> editable.insert(cursor, "\n")
            else -> {
                editable.insert(cursor, key)
                consumeShiftIfNeeded()
            }
        }
        updateSuggestions(edit.text.toString())
    }'''
new_repair_keys = r'''    private fun handleRepairBoxKey(key: String) {
        val edit = repairEditText ?: return
        val editable = edit.text ?: return
        val cursor = edit.selectionStart.coerceAtLeast(0)
        when (key) {
            "⌫" -> {
                val start = edit.selectionStart.coerceAtLeast(0)
                val end = edit.selectionEnd.coerceAtLeast(0)
                if (start != end) {
                    editable.delete(kotlin.math.min(start, end), kotlin.math.max(start, end))
                } else if (cursor > 0) {
                    deletePreviousGrapheme(editable, cursor)
                } else if (editable.isEmpty()) {
                    handleBackspace()
                }
            }
            "مسافة" -> editable.insert(cursor, " ")
            "↵" -> {
                if (editable.count { it == '\n' } < 1) {
                    editable.insert(cursor, "\n")
                }
            }
            else -> {
                editable.insert(cursor, key)
                consumeShiftIfNeeded()
            }
        }
        repairBuffer = editable.toString()
        scheduleSuggestionRefresh()
    }

    private fun deletePreviousGrapheme(editable: Editable, cursor: Int) {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(editable.toString())
        val boundary = iterator.preceding(cursor)
        if (boundary != BreakIterator.DONE && boundary < cursor) {
            editable.delete(boundary, cursor)
            return
        }
        val fallback = Character.offsetByCodePoints(editable, cursor, -1)
        editable.delete(fallback, cursor)
    }'''
text = replace_once(text, old_repair_keys, new_repair_keys, "grapheme-safe repair editing")

text = replace_once(
    text,
    "        if (typedText.isNotEmpty()) typedText.deleteCharAt(typedText.length - 1)\n    }\n\n    private fun handleEnter()",
    r'''        if (typedText.isNotEmpty()) deleteLastTrackedGrapheme()
    }

    private fun deleteLastTrackedGrapheme() {
        val value = typedText.toString()
        if (value.isEmpty()) return
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        val boundary = iterator.preceding(value.length)
        if (boundary != BreakIterator.DONE && boundary < value.length) {
            typedText.delete(boundary, value.length)
        } else {
            val fallback = Character.offsetByCodePoints(value, value.length, -1)
            typedText.delete(fallback, value.length)
        }
    }

    private fun handleEnter()''',
    "grapheme-safe tracked deletion",
)

old_refresh = r'''    /**
     * Refresh from the local buffer immediately, then reconcile once Android has
     * committed the character to the editor. This keeps dictionary suggestions
     * live on every key instead of waiting for a space/boundary event.
     */
    private fun refreshSuggestionsAfterLocalEdit() {
        val localSnapshot = typedText.toString()
        updateSuggestions(localSnapshot)

        suggestionRefreshRunnable?.let(suggestionRefreshHandler::removeCallbacks)
        val runnable = Runnable {
            suggestionRefreshRunnable = null
            updateSuggestions(currentSuggestionSource())
        }
        suggestionRefreshRunnable = runnable
        suggestionRefreshHandler.postDelayed(runnable, 24L)
    }'''
new_refresh = r'''    /**
     * Key input stays immediate. Dictionary lookup and row rebuilding are coalesced
     * into one refresh after rapid consecutive taps instead of running twice per key.
     */
    private fun refreshSuggestionsAfterLocalEdit() {
        scheduleSuggestionRefresh()
    }

    private fun scheduleSuggestionRefresh(delayMillis: Long = 36L) {
        suggestionRefreshRunnable?.let(suggestionRefreshHandler::removeCallbacks)
        val runnable = Runnable {
            suggestionRefreshRunnable = null
            updateSuggestions(currentSuggestionSource())
        }
        suggestionRefreshRunnable = runnable
        suggestionRefreshHandler.postDelayed(runnable, delayMillis)
    }'''
text = replace_once(text, old_refresh, new_refresh, "coalesced suggestion refresh")

text = replace_once(
    text,
    "            updateSuggestions(text)\n        }\n    }\n\n    private fun commitFixedRepairText()",
    "            scheduleSuggestionRefresh(0L)\n        }\n    }\n\n    private fun commitFixedRepairText()",
    "defer pasted suggestions",
)

text = replace_once(
    text,
    "            replaceOrAppendTokenInEditText(edit, word)\n            updateSuggestions(edit.text.toString())\n            return",
    "            replaceOrAppendTokenInEditText(edit, word)\n            scheduleSuggestionRefresh(0L)\n            return",
    "defer repair suggestion refresh",
)

KOTLIN_PATH.write_text(text, encoding="utf-8")

journal = JOURNAL_PATH.read_text(encoding="utf-8")
journal = replace_once(
    journal,
    "- `Patch 29 — Stable More Page & Compact Compose` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.\n- تدمج Patch 29 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #8 هو مصدر البناء الوحيد.\n- `Patch 30 — Compose Editor Polish` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.",
    "- `Patch 30 — Compose Editor Polish` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.\n- تدمج Patch 30 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #9 هو مصدر البناء الوحيد.\n- `Patch 31 — Performance, Emoji Panel & Manual Lines` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.",
    "journal final base",
)

journal = replace_once(
    journal,
    "- اعتُمدت Patch 29 كقاعدة البناء الحالية بعد نجاح صفحة المزيد الثابتة وحقل التحويل ذي السطرين.\n- يجري الآن اختبار `Patch 30 — Compose Editor Polish`.\n- الفرع النشط: `agent/patch-30-compose-editor-polish`.\n- النطاق محصور في مسح المسودة عند الإغلاق، إصلاح إدراج الاقتراح عند المؤشر، منع الخطوط الحمراء، ودمج الإيموجي مع زر الفاصلة وإعادة ترتيب زر السطر الجديد.\n- لا تغيير في القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.",
    "- اعتُمدت Patch 30 كقاعدة البناء الحالية بعد نجاح مسح المسودة وإدراج الاقتراح عند المؤشر وترتيب صف الإجراءات.\n- يجري الآن اختبار `Patch 31 — Performance, Emoji Panel & Manual Lines`.\n- الفرع النشط: `agent/patch-31-performance-emoji-panel-manual-lines`.\n- النطاق محصور في تسريع الكتابة، منع الالتفاف التلقائي، إنشاء لوحة إيموجي مع الأخيرة، والحذف الآمن للرموز وتبديل موضعي زر السطر والعصا.\n- لا تغيير في محتوى القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.",
    "journal current stage",
)

journal = replace_once(
    journal,
    "## Patch 30 — Compose Editor Polish (Candidate)",
    "## Patch 30 — Compose Editor Polish (Final Base)",
    "Patch 30 heading",
)

journal = replace_once(
    journal,
    "- لا تغيير في محتوى القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob أو نظام المكافآت.\n\n## ملاحظات التصميم المعتمدة",
    "- لا تغيير في محتوى القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob أو نظام المكافآت.\n- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #9 في `main`.\n\n## Patch 31 — Performance, Emoji Panel & Manual Lines (Candidate)\n\n- أزيل حساب القاموس المكرر مع كل ضغطة، وأصبحت تحديثات الاقتراحات تُدمج في تحديث واحد قصير بعد الضربات المتتابعة لتبقى الكتابة فورية.\n- لا يظهر السطر الثاني بسبب التفاف النص الطويل؛ يظهر فقط عند الضغط الصريح على زر السطر الجديد، مع حد أقصى سطرين.\n- الضغط المطول على الفاصلة يفتح لوحة إيموجي كاملة بدل إدخال وجه واحد مباشرة.\n- تحتوي لوحة الإيموجي فئات مرتبة وسطرًا محليًا لآخر ستة رموز استعملها المستخدم.\n- أصبح حذف الإيموجي آمنًا على مستوى الرمز الكامل، بما في ذلك الرموز المركبة، لمنع بقايا الرموز وعلامة الاستفهام.\n- أصبح زر السطر الجديد قبل عصا التنفيذ في ترتيب الصف السفلي أثناء وضع الإصلاح.\n- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.\n\n## ملاحظات التصميم المعتمدة",
    "Patch 31 journal section",
)

JOURNAL_PATH.write_text(journal, encoding="utf-8")
print("Patch 31 transformation completed")
