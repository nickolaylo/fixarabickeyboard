package com.souadachak.fixarabickeyboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.text.BreakIterator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.souadachak.fixarabickeyboard.R

class KeyboardImeService : InputMethodService() {
    private lateinit var coinManager: CoinManager
    private lateinit var suggestionEngine: SuggestionEngine
    private var activeEditorInfo: EditorInfo? = null
    private val typedText = StringBuilder()
    private var keyboardMode: KeyboardMode = KeyboardMode.ARABIC
    private var lastLetterMode: KeyboardMode = KeyboardMode.ARABIC
    private var toolsExpanded: Boolean = false
    private var repairExpanded: Boolean = false
    private var morePanelExpanded: Boolean = false
    private var emojiPanelExpanded: Boolean = false
    private var activeEmojiCategory: Int = 0
    private var repairEditText: EditText? = null
    private var suggestionsRow: LinearLayout? = null
    private var repairBuffer: String = ""
    private var lastDictionaryVisible: Boolean = false
    private var lastSmartRowMode: SmartRowMode = SmartRowMode.TOOLS
    private var keyPreviewPopup: PopupWindow? = null
    private var shiftState: ShiftState = ShiftState.OFF
    private var lastRepairSlotHeight: Int = 0
    private var stableTopArea: FrameLayout? = null
    private var topStack: LinearLayout? = null
    private var smartTopSlotView: View? = null
    private var repairTopSlotView: View? = null
    private var repairRowContainer: FrameLayout? = null
    private var keyboardRoot: FrameLayout? = null
    private var repairOverlayView: View? = null
    private var keyAreaContainer: FrameLayout? = null
    private val deleteRepeatHandler = Handler(Looper.getMainLooper())
    private var deleteRepeatRunnable: Runnable? = null
    private var deleteRepeatCount: Int = 0
    private val suggestionRefreshHandler = Handler(Looper.getMainLooper())
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
    }

    private val prefs by lazy { getSharedPreferences("keyboard_ui_state", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        coinManager = CoinManager(this)
        suggestionEngine = SuggestionEngine(this)
        suggestionEngine.preload(DictionaryLanguage.ARABIC)
        toolsExpanded = prefs.getBoolean("tools_expanded", true)
        repairExpanded = prefs.getBoolean("repair_expanded", false)
    }

    override fun onCreateInputView(): View {
        // Patch 04: build the keyboard from the bottom upward.
        // The key area stays anchored to the bottom while optional rows above it change.
        val root = FrameLayout(this).apply {
            setBackgroundColor(KeyboardColors.background)
            clipChildren = false
            clipToPadding = false
        }
        keyboardRoot = root
        repairOverlayView = null

        val keyboardStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(KeyboardColors.background)
            clipChildren = false
            clipToPadding = false
        }

        keyboardStack.addView(makeStableTopArea())
        val keyArea = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(KEY_AREA_HEIGHT_DP)
            )
        }
        keyAreaContainer = keyArea
        keyArea.addView(
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
        keyboardStack.addView(keyArea)

        root.addView(
            keyboardStack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        updateSuggestions()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        activeEditorInfo = attribute
        suggestionRefreshRunnable?.let(suggestionRefreshHandler::removeCallbacks)
        suggestionRefreshRunnable = null

        if (!restarting) {
            typedText.clear()
            repairBuffer = ""
            repairEditText?.setText("")
            lastDictionaryVisible = false
            toolsExpanded = true
            morePanelExpanded = false
            emojiPanelExpanded = false
            activeEmojiCategory = 0
            emojiRecentRow = null
            lastSmartRowMode = SmartRowMode.TOOLS
        } else if (!repairExpanded) {
            syncTypedTextFromEditor()
        }

        updateSuggestions()
    }

    private fun makeStableTopArea(): FrameLayout {
        val sourceText = currentSuggestionSource()
        val smartMode = smartRowMode(sourceText)
        lastDictionaryVisible = sourceText.isNotBlank()
        lastSmartRowMode = smartMode

        val smartSlotHeight = dp(43)
        val repairSlotHeight = if (repairExpanded) currentRepairSlotHeight() else dp(45)
        if (repairExpanded) lastRepairSlotHeight = repairSlotHeight
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(1) else 0

        return FrameLayout(this).apply {
            stableTopArea = this
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                totalHeight
            )

            val stack = LinearLayout(this@KeyboardImeService).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = false
                clipToPadding = false
            }
            topStack = stack

            if (repairExpanded) {
                val repairContent = makeRepairInputRow()
                repairTopSlotView = repairContent
                stack.addView(
                    repairContent,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        repairSlotHeight
                    ).apply { bottomMargin = dp(1) }
                )
            } else {
                repairTopSlotView = null
                repairEditText = null
            }

            val smartContent = makeSmartRow(smartMode, dictionarySuggestions(sourceText))
            smartTopSlotView = smartContent
            stack.addView(
                smartContent,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    smartSlotHeight
                )
            )

            addView(
                stack,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun makeRepairSlot(repairSlotHeight: Int): FrameLayout {
        return FrameLayout(this).apply {
            repairRowContainer = this
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = repairExpanded
            isFocusable = repairExpanded
            isEnabled = repairExpanded
            visibility = if (repairExpanded) View.VISIBLE else View.INVISIBLE
            alpha = if (repairExpanded) 1f else 0f
            if (repairExpanded) {
                addView(
                    makeRepairInputRow(),
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        repairSlotHeight
                    )
                )
            }
        }
    }

    private fun currentRepairSlotHeight(): Int {
        val minimumRepairSlotHeight = dp(45)
        if (!repairExpanded) {
            // Patch 05: keep the physical slot height reserved even while the repair row is hidden.
            // This prevents the IME window from changing height when the wand is toggled.
            // The repair view itself is not drawn, so no repair panel/background is visible.
            return minimumRepairSlotHeight
        }
        return repairSlotHeightForText(repairBuffer).coerceAtLeast(minimumRepairSlotHeight)
    }

    private fun repairSlotHeightForText(text: String): Int {
        val explicitLines = (text.count { it == '\n' } + 1).coerceIn(1, 2)
        return if (explicitLines == 1) dp(45) else dp(69)
    }

    private fun makeRepairInputRow(): FrameLayout {
        repairEditText = EditText(this).apply {
            hint = "اكتب النص هنا لتحويله."
            gravity = Gravity.RIGHT or Gravity.TOP
            textSize = 16f
            minLines = 1
            maxLines = 2
            setSingleLine(false)
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(12), dp(7), dp(36), dp(5))
            minHeight = dp(38)
            background = roundedBackground(KeyboardColors.repairField, dp(12))
            setText(repairBuffer)
            setSelection(text?.length ?: 0)
            setShowSoftInputOnFocus(false)
            isCursorVisible = true
            post {
                requestFocus()
                setSelection(text?.length ?: 0)
                isCursorVisible = true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    repairBuffer = text?.toString().orEmpty()
                }
                override fun afterTextChanged(text: Editable?) {
                    post { updateRepairAreaHeight(text?.toString().orEmpty()) }
                }
            })
        }

        val closeButton = TextView(this).apply {
            text = "×"
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(KeyboardColors.toolbarIcon)
            includeFontPadding = false
            background = roundedStrokeBackground(
                KeyboardColors.panel,
                KeyboardColors.repairStroke,
                dp(13),
                dp(1)
            )
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = moreToolLabel("إغلاق وضع التحويل", "Close conversion mode", "Fermer le mode de conversion")
            setOnClickListener { toggleRepairExpanded() }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }

        return FrameLayout(this).apply {
            setPadding(dp(2), 0, dp(2), dp(2))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(14), dp(1))
            clipChildren = false
            clipToPadding = false
            addView(
                repairEditText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    topMargin = dp(5)
                }
            )
            addView(
                closeButton,
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.RIGHT).apply {
                    topMargin = -dp(3)
                    rightMargin = -dp(4)
                }
            )
        }
    }

    private fun updateRepairAreaHeight(text: String) {
        if (!repairExpanded) return
        val nextHeight = repairSlotHeightForText(text)
        if (nextHeight == lastRepairSlotHeight) return
        lastRepairSlotHeight = nextHeight

        repairTopSlotView?.let { view ->
            view.layoutParams = view.layoutParams.apply { height = nextHeight }
            view.requestLayout()
        }
        stableTopArea?.let { area ->
            area.layoutParams = area.layoutParams.apply {
                height = dp(43) + nextHeight + dp(1)
            }
            area.requestLayout()
        }
        repairOverlayView?.let { overlay ->
            overlay.layoutParams = overlay.layoutParams.apply { height = nextHeight }
            overlay.requestLayout()
        }
    }

    private fun resizeRepairSlot(nextHeight: Int) {
        // Patch 10: while the repair UI lives inside the fixed TopSlot, resizing it would
        // recreate the old stacked-row/jump problem. Keep the slot fixed and only remember
        // the requested height for future full-panel work.
        lastRepairSlotHeight = nextHeight
        if (repairOverlayView != null) {
            val repairView = repairOverlayView ?: return
            repairView.layoutParams = repairView.layoutParams.apply {
                height = nextHeight
            }
            repairView.requestLayout()
        }
    }

    private fun makeSmartRow(mode: SmartRowMode, suggestions: List<SuggestionItem>): LinearLayout {
        val hasClipboardText = clipboardText().isNotBlank()
        return LinearLayout(this).apply {
            suggestionsRow = if (mode == SmartRowMode.DICTIONARY) this else null
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(1))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.panelStroke, dp(9), dp(1))

            addView(
                makeMoreToolsButton(),
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(0, 0, dp(1), 0) }
            )

            when (mode) {
                SmartRowMode.TOOLS -> addToolIconsToSmartRow(hasClipboardText)
                SmartRowMode.DICTIONARY -> addDictionaryContentToSmartRow(suggestions)
                SmartRowMode.HIDDEN -> addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(40), 1f))
            }

            if (!repairExpanded) {
                addView(
                    makeIconButton(R.drawable.ic_keyboard_magic_wand, KeyboardColors.toolbarIcon) { toggleRepairExpanded() },
                    LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(1), 0, 0, 0) }
                )
            }
        }
    }

    private fun LinearLayout.addToolIconsToSmartRow(hasClipboardText: Boolean) {
        addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(40), 1f))
        addView(
            makeIconButton(R.drawable.ic_keyboard_paste, if (hasClipboardText) KeyboardColors.toolbarIcon else KeyboardColors.disabledIcon, enabled = hasClipboardText) { pasteToRepairBox() },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(1), 0, dp(1), 0) }
        )
        addView(
            makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.toolbarIcon) { openEmojiPanel() },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(1), 0, dp(1), 0) }
        )
        addView(
            makeIconButton(R.drawable.ic_keyboard_settings, KeyboardColors.toolbarIcon) { openAppSettings() },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(1), 0, dp(1), 0) }
        )
        addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(40), 1f))
    }

    private fun LinearLayout.addDictionaryContentToSmartRow(suggestions: List<SuggestionItem>) {
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

    private fun rebuildDictionaryContent(suggestions: List<SuggestionItem>) {
        val row = suggestionsRow ?: return
        val trailingWand = if (!repairExpanded && row.childCount > 1) {
            row.getChildAt(row.childCount - 1).also(row::removeView)
        } else {
            null
        }
        if (row.childCount > 1) {
            row.removeViews(1, row.childCount - 1)
        }
        row.addDictionaryContentToSmartRow(suggestions)
        if (trailingWand != null) {
            row.addView(
                trailingWand,
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(1), 0, 0, 0) }
            )
        }
    }

    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }

    private fun makeKeyboardKeyArea(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(makeNumberRow())
            activeRows().forEachIndexed { index, row ->
                addView(makeLetterRow(row, showBackspace = index == 2))
            }
            addView(makeBottomRow())
        }
    }


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
        emojiRecentRow?.let { row -> row.post { renderRecentEmojis(row) } }
    }

    private fun makeMoreToolsPanel(): LinearLayout {
        fun row(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val firstRow = row().apply {
            addView(
                makeMoreToolTile(R.drawable.ic_keyboard_share, moreToolLabel("مشاركة", "Share", "Partager")) { shareKeyboardApp() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            )
            addView(
                makeMoreToolTile(R.drawable.ic_keyboard_settings, moreToolLabel("الإعدادات", "Settings", "Paramètres")) { openAppSettings() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            )
        }
        val secondRow = row().apply {
            addView(
                makeMoreToolTile(R.drawable.ic_keyboard_magic_wand, moreToolLabel("المكافآت", "Rewards", "Récompenses")) { openAppSettings() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            )
            addView(
                makeMoreToolTile(R.drawable.ic_keyboard_palette, moreToolLabel("المظهر", "Appearance", "Apparence")) { openAppSettings() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            )
        }

        val title = TextView(this).apply {
            text = moreToolLabel("المزيد", "More", "Plus")
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(KeyboardColors.text)
            includeFontPadding = false
            setPadding(dp(10), 0, dp(10), 0)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            background = roundedBackground(KeyboardColors.panel, dp(14))
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
            addView(firstRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(secondRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun makeMoreToolTile(iconRes: Int, label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(KeyboardColors.text)
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, iconRes, 0, 0)
            compoundDrawablePadding = dp(10)
            background = roundedBackground(KeyboardColors.key, dp(16))
            setPadding(dp(10), dp(16), dp(10), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun moreToolLabel(arabic: String, english: String, french: String): String {
        return when (lastLetterMode) {
            KeyboardMode.ARABIC -> arabic
            KeyboardMode.FRENCH -> french
            else -> english
        }
    }

    private fun shareKeyboardApp() {
        val shareText = "Fix Arabic Keyboard\nhttps://play.google.com/store/apps/details?id=$packageName"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(shareIntent, moreToolLabel("مشاركة لوحة المفاتيح", "Share keyboard", "Partager le clavier"))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooser)
    }

    private fun makeNumberRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(3))
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { key ->
                addView(makeActionKey(key, KeyboardColors.key, 19f) { handleKey(key) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    private fun makeLetterRow(keys: List<String>, showBackspace: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(3))
            if (showBackspace && isLatinMode()) {
                addView(makeShiftKey(), LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            keys.forEach { key ->
                val label = displayLetter(key)
                addView(makeLetterKey(label) { handleKey(label) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            if (showBackspace) {
                addView(makeBackspaceKey(), LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    private fun makeBottomRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)

            when (keyboardMode) {
                KeyboardMode.ARABIC -> addTextModeBottomRow(symbolsLabel = "?123", comma = "،", period = ".")
                KeyboardMode.ENGLISH -> addTextModeBottomRow(symbolsLabel = "?123", comma = ",", period = ".")
                KeyboardMode.FRENCH -> addTextModeBottomRow(symbolsLabel = "?123", comma = ",", period = ".")
                KeyboardMode.SYMBOLS_1 -> {
                    addView(makeActionKey(languageReturnLabel(), KeyboardColors.specialKey, 15f) { switchMode(lastLetterMode) }, bottomParams(dp(50)))
                    addView(makeActionKey("،", KeyboardColors.specialKey, 17f) { handleKey("،") }, bottomParams(dp(34)))
                    addView(makeActionKey("1/2", KeyboardColors.specialKey, 14f) { switchMode(KeyboardMode.SYMBOLS_2) }, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey(".", KeyboardColors.specialKey, 17f) { handleKey(".") }, bottomParams(dp(34)))
                    addView(makePrimaryActionButton(), bottomParams(dp(64)))
                }
                KeyboardMode.SYMBOLS_2 -> {
                    addView(makeActionKey(languageReturnLabel(), KeyboardColors.specialKey, 15f) { switchMode(lastLetterMode) }, bottomParams(dp(50)))
                    addView(makeActionKey("«", KeyboardColors.specialKey, 17f) { handleKey("«") }, bottomParams(dp(34)))
                    addView(makeActionKey("2/2", KeyboardColors.specialKey, 14f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey("»", KeyboardColors.specialKey, 17f) { handleKey("»") }, bottomParams(dp(34)))
                    addView(makePrimaryActionButton(), bottomParams(dp(64)))
                }
            }
        }
    }

    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeCommaEmojiKey(comma), bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        }
        addView(makePrimaryActionButton(), bottomParams(dp(64)))
    }

    private fun makeCommaEmojiKey(comma: String): FrameLayout {
        return FrameLayout(this).apply {
            background = roundedBackground(KeyboardColors.specialKey, dp(11))
            isClickable = true
            isFocusable = true
            contentDescription = moreToolLabel(
                "فاصلة، ضغط مطول لفتح الرموز التعبيرية",
                "Comma, long press to open emoji",
                "Virgule, appui long pour ouvrir les emojis"
            )

            val commaLabel = TextView(this@KeyboardImeService).apply {
                text = comma
                gravity = Gravity.CENTER
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(KeyboardColors.text)
                includeFontPadding = false
            }
            val emojiHint = TextView(this@KeyboardImeService).apply {
                text = "🙂"
                gravity = Gravity.CENTER
                textSize = 8f
                setTextColor(KeyboardColors.disabledIcon)
                includeFontPadding = false
                alpha = 0.58f
            }

            addView(
                commaLabel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                emojiHint,
                FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(2)
                    rightMargin = dp(2)
                }
            )

            setOnClickListener { handleKey(comma) }
            setOnLongClickListener {
                openEmojiPanel()
                true
            }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun makePrimaryActionButton(): View {
        if (!repairExpanded) {
            return makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }
        }
        return ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard_magic_wand)
            setColorFilter(KeyboardColors.onAccent)
            background = roundedBackground(KeyboardColors.enterKey, dp(11))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = moreToolLabel("تنفيذ الإصلاح", "Apply repair", "Appliquer la correction")
            setOnClickListener {
                if (repairBuffer.isNotBlank()) commitFixedRepairText()
            }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }

    private fun makeLetterKey(label: String, onClick: () -> Unit): TextView = makeBaseKey(label, KeyboardColors.key, KeyboardColors.text, 20f, Typeface.NORMAL, true, onClick)

    private fun makeActionKey(label: String, color: Int, size: Float, onClick: () -> Unit): TextView = makeBaseKey(
        label,
        color,
        if (color == KeyboardColors.enterKey) KeyboardColors.onAccent else KeyboardColors.text,
        size,
        Typeface.BOLD,
        label.length <= 2 && label != "⌫" && label != "↵",
        onClick
    )

    private fun makeSuggestionKey(
        label: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ): TextView {
        return makeBaseKey(
            label,
            KeyboardColors.key,
            KeyboardColors.text,
            15f,
            Typeface.NORMAL,
            false,
            onClick
        ).apply {
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        }
    }

    private fun makeToolButton(label: String, bgColor: Int, textColor: Int, size: Float, onClick: () -> Unit): TextView = makeBaseKey(label, bgColor, textColor, size, Typeface.BOLD, false, onClick)

    private fun makeShiftKey(): TextView {
        val label = when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ONCE -> "⇧"
            ShiftState.LOCKED -> "⇪"
        }
        val color = if (shiftState == ShiftState.OFF) KeyboardColors.specialKey else KeyboardColors.keyPressed
        return makeActionKey(label, color, 20f) { toggleShiftState() }
    }

    private fun makeBackspaceKey(): TextView {
        return makeBaseKey("⌫", KeyboardColors.specialKey, KeyboardColors.text, 20f, Typeface.BOLD, false) { }
            .apply {
                setOnClickListener(null)
                setOnTouchListener { view, event ->
                    animatePress(view, event, null, false)
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handleKey("⌫")
                            startDeleteRepeat()
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            stopDeleteRepeat()
                            true
                        }
                        else -> true
                    }
                }
            }
    }

    private fun toggleShiftState() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
        setInputView(onCreateInputView())
    }

    private fun isLatinMode(): Boolean = keyboardMode == KeyboardMode.ENGLISH || keyboardMode == KeyboardMode.FRENCH

    private fun displayLetter(key: String): String {
        return if (isLatinMode() && shiftState != ShiftState.OFF) key.uppercase() else key
    }

    private fun consumeShiftIfNeeded() {
        if (isLatinMode() && shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            setInputView(onCreateInputView())
        }
    }

    private fun makeMoreToolsButton(): ImageButton {
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
    }

    private fun makeBottomIconButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return makeIconButton(iconRes, KeyboardColors.textMuted, onClick)
    }

    private fun makeIconButton(iconRes: Int, iconColor: Int, onClick: () -> Unit): ImageButton {
        return makeIconButton(iconRes, iconColor, true, onClick)
    }

    private fun makeIconButton(iconRes: Int, iconColor: Int, enabled: Boolean, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            setColorFilter(iconColor)
            val surfaceColor = if (iconRes == R.drawable.ic_keyboard_magic_wand && repairExpanded) {
                KeyboardColors.wandActiveSurface
            } else {
                Color.TRANSPARENT
            }
            background = roundedBackground(surfaceColor, dp(14))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.36f
            isClickable = enabled
            isFocusable = enabled
            contentDescription = null
            if (enabled) setOnClickListener { onClick() }
            if (enabled) setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun makeSpaceKey(): View {
        var downX = 0f
        return FrameLayout(this).apply {
            background = roundedBackground(KeyboardColors.key, dp(9))
            isClickable = true
            isFocusable = true
            val leftArrow = TextView(this@KeyboardImeService).apply {
                text = "‹"
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(KeyboardColors.textMuted)
                includeFontPadding = false
            }
            val label = TextView(this@KeyboardImeService).apply {
                text = languageSpaceLabel()
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(KeyboardColors.languageLabel)
                includeFontPadding = false
                setSingleLine(true)
            }
            val rightArrow = TextView(this@KeyboardImeService).apply {
                text = "›"
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(KeyboardColors.textMuted)
                includeFontPadding = false
            }
            addView(leftArrow, FrameLayout.LayoutParams(dp(52), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            addView(rightArrow, FrameLayout.LayoutParams(dp(52), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))
            setOnClickListener { handleKey("مسافة") }
            setOnTouchListener { view, event ->
                animatePress(view, event, null, false)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> downX = event.x
                    MotionEvent.ACTION_UP -> {
                        val delta = event.x - downX
                        if (kotlin.math.abs(delta) > dp(42)) {
                            switchLanguageBySwipe(delta)
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
    }

    private fun languageSpaceLabel(): String {
        return when (lastLetterMode) {
            KeyboardMode.ARABIC -> "العربية"
            KeyboardMode.ENGLISH -> "English"
            KeyboardMode.FRENCH -> "Français"
            else -> "العربية"
        }
    }

    private fun languageReturnLabel(): String {
        return when (lastLetterMode) {
            KeyboardMode.ARABIC -> "أبج"
            KeyboardMode.ENGLISH -> "ABC"
            KeyboardMode.FRENCH -> "FR"
            else -> "أبج"
        }
    }

    private fun switchLanguageBySwipe(delta: Float) {
        val order = listOf(KeyboardMode.ARABIC, KeyboardMode.ENGLISH, KeyboardMode.FRENCH)
        val currentIndex = order.indexOf(lastLetterMode).coerceAtLeast(0)
        val nextIndex = if (delta > 0) {
            (currentIndex + 1) % order.size
        } else {
            (currentIndex - 1 + order.size) % order.size
        }
        switchMode(order[nextIndex])
    }

    private fun makeBaseKey(label: String, bgColor: Int, textColor: Int, size: Float, style: Int, showPreview: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, style)
            setTextColor(textColor)
            background = roundedBackground(bgColor, dp(9))
            includeFontPadding = false
            setSingleLine(true)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { view, event -> animatePress(view, event, label, showPreview) }
        }
    }

    private fun animatePress(view: View, event: MotionEvent, label: String?, showPreview: Boolean): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(35).start()
                if (showPreview && label != null) showKeyPreview(view, label)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1f).scaleY(1f).setDuration(55).start()
                dismissKeyPreview()
            }
        }
        return false
    }

    private fun showKeyPreview(anchor: View, label: String) {
        dismissKeyPreview()
        val preview = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(KeyboardColors.text)
            includeFontPadding = false
            background = roundedBackground(KeyboardColors.keyPressed, dp(16))
        }
        keyPreviewPopup = PopupWindow(preview, anchor.width.coerceAtLeast(dp(46)), dp(76), false).apply {
            isClippingEnabled = false
            elevation = dp(4).toFloat()
        }
        runCatching { keyPreviewPopup?.showAsDropDown(anchor, 0, -anchor.height - dp(76)) }
    }

    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        deleteRepeatCount = 0
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                handleKey("⌫")
                deleteRepeatCount += 1
                val nextDelay = when {
                    deleteRepeatCount < 6 -> 42L
                    deleteRepeatCount < 16 -> 30L
                    else -> 20L
                }
                deleteRepeatHandler.postDelayed(this, nextDelay)
            }
        }
        deleteRepeatHandler.postDelayed(deleteRepeatRunnable!!, 260L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
        deleteRepeatCount = 0
    }

    private fun toggleMorePanel() {
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
    }

    private fun toggleToolsExpanded() {
        toolsExpanded = !toolsExpanded
        prefs.edit()
            .putBoolean("tools_expanded", toolsExpanded)
            .putBoolean("repair_expanded", repairExpanded)
            .apply()
        setInputView(onCreateInputView())
    }

    private fun toggleRepairExpanded() {
        if (repairExpanded) {
            // Patch 30: closing conversion mode discards the draft by design.
            repairBuffer = ""
            repairEditText?.text?.clear()
        }
        repairExpanded = !repairExpanded
        morePanelExpanded = false
        emojiPanelExpanded = false
        emojiRecentRow = null
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()

        // Patch 26: opening the wand adds one compact compose line above the toolbar.
        // The toolbar keeps its place and only its content changes with the text state.
        setInputView(onCreateInputView())
    }

    private fun showRepairOverlay(animated: Boolean) {
        val root = keyboardRoot ?: return
        repairOverlayView?.let { root.removeView(it) }
        val overlay = makeRepairOverlay()
        repairOverlayView = overlay
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                currentRepairSlotHeight(),
                Gravity.TOP
            ).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                topMargin = dp(6)
            }
        )
        if (animated) {
            overlay.alpha = 0f
            overlay.translationY = -dp(10).toFloat()
            overlay.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            overlay.alpha = 1f
            overlay.translationY = 0f
        }
    }

    private fun hideRepairOverlay(animated: Boolean) {
        val root = keyboardRoot ?: return
        val overlay = repairOverlayView ?: return
        repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        val removeAction = {
            if (repairOverlayView === overlay) {
                root.removeView(overlay)
                repairOverlayView = null
                repairEditText = null
            }
        }
        if (animated) {
            overlay.animate()
                .alpha(0f)
                .translationY(-dp(10).toFloat())
                .setDuration(120)
                .withEndAction { removeAction() }
                .start()
        } else {
            removeAction()
        }
    }

    private fun makeRepairOverlay(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
            addView(
                makeRepairInputRow(),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun replaceSmartRow(mode: SmartRowMode, sourceText: String) {
        val stack = topStack ?: return
        smartTopSlotView?.let(stack::removeView)

        val smartContent = makeSmartRow(mode, dictionarySuggestions(sourceText))
        smartTopSlotView = smartContent
        val index = if (repairExpanded && repairTopSlotView != null) 1 else 0
        stack.addView(
            smartContent,
            index,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(43)
            )
        )
        smartContent.alpha = 0f
        smartContent.animate().alpha(1f).setDuration(90).start()
        lastSmartRowMode = mode
    }

    private fun refreshStableTopAreaOnly() {
        val sourceText = currentSuggestionSource()
        val mode = smartRowMode(sourceText)
        lastDictionaryVisible = sourceText.isNotBlank()
        replaceSmartRow(mode, sourceText)
    }

    private fun applyTopSlotState(animated: Boolean) {
        val smart = smartTopSlotView
        val repair = repairTopSlotView
        if (smart == null || repair == null) return

        smart.animate().cancel()
        repair.animate().cancel()

        // Patch 11: true TopSlot swap.
        // Do not crossfade both rows together, because the transparent parts can make
        // the icon row appear under the repair row. One child is visible, the other
        // is invisible inside the same FrameLayout slot.
        if (repairExpanded) {
            smart.alpha = 0f
            smart.visibility = View.INVISIBLE
            repair.bringToFront()
            repair.visibility = View.VISIBLE
            repair.alpha = 1f
        } else {
            repair.alpha = 0f
            repair.visibility = View.INVISIBLE
            smart.bringToFront()
            smart.visibility = View.VISIBLE
            smart.alpha = 1f
        }
    }

    private fun openAppSettings() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun switchMode(newMode: KeyboardMode) {
        keyboardMode = newMode
        if (newMode == KeyboardMode.ARABIC || newMode == KeyboardMode.ENGLISH || newMode == KeyboardMode.FRENCH) {
            lastLetterMode = newMode
            if (newMode == KeyboardMode.ARABIC) shiftState = ShiftState.OFF
        }
        setInputView(onCreateInputView())
    }

    private fun handleKey(key: String) {
        if (repairExpanded) {
            handleRepairBoxKey(key)
            return
        }
        if (toolsExpanded && key != "⌫") toolsExpanded = false
        when (key) {
            "⌫" -> handleBackspace()
            "مسافة" -> {
                suggestionEngine.learnFromCompletedText(
                    textBeforeBoundary = currentSuggestionSource(),
                    language = activeDictionaryLanguage(),
                    editorInfo = activeEditorInfo
                )
                commitAndRemember(" ")
            }
            "↵" -> handleEnter()
            else -> {
                commitAndRemember(key)
                consumeShiftIfNeeded()
            }
        }
        refreshSuggestionsAfterLocalEdit()
    }

    private fun handleRepairBoxKey(key: String) {
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
    }

    private fun commitAndRemember(text: String) {
        currentInputConnection?.commitText(text, 1)
        typedText.append(text)
    }

    private fun forgetLearnedSuggestion(suggestion: SuggestionItem) {
        val removed = suggestionEngine.forgetLearnedSuggestion(
            suggestion = suggestion,
            language = activeDictionaryLanguage()
        )
        if (!removed) return

        val message = when (activeDictionaryLanguage()) {
            DictionaryLanguage.ARABIC -> "تم حذف الاقتراح المتعلم"
            DictionaryLanguage.FRENCH -> "Suggestion apprise supprimée"
            DictionaryLanguage.ENGLISH -> "Learned suggestion removed"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateSuggestions(currentSuggestionSource())
    }

    private fun commitSuggestion(word: String) {
        toolsExpanded = false
        if (!repairExpanded) {
            suggestionEngine.learnSelectedWord(
                sourceBeforeSelection = currentSuggestionSource(),
                selectedWord = word,
                language = activeDictionaryLanguage(),
                editorInfo = activeEditorInfo
            )
        }
        if (repairExpanded) {
            val edit = repairEditText ?: return
            replaceOrAppendTokenInEditText(edit, word)
            scheduleSuggestionRefresh(0L)
            return
        }
        val current = currentSuggestionSource()
        val lastToken = if ('@' in word) {
            current.takeLastWhile { char -> !char.isWhitespace() }
        } else {
            current.takeLastWhile { char ->
                !char.isWhitespace() && !isDictionarySeparator(char)
            }
        }
        if (lastToken.isNotEmpty()) {
            currentInputConnection?.deleteSurroundingText(lastToken.length, 0)
        }
        currentInputConnection?.commitText("$word ", 1)
        typedText.clear()
        typedText.append(word).append(' ')
        updateSuggestions()
    }

    private fun replaceOrAppendTokenInEditText(edit: EditText, word: String) {
        val editable = edit.text ?: return
        val selectionStart = edit.selectionStart.coerceIn(0, editable.length)
        val selectionEnd = edit.selectionEnd.coerceIn(0, editable.length)
        val selectionMin = kotlin.math.min(selectionStart, selectionEnd)
        val selectionMax = kotlin.math.max(selectionStart, selectionEnd)

        var replaceStart = selectionMin
        var replaceEnd = selectionMax
        if (selectionMin == selectionMax) {
            while (replaceStart > 0) {
                val previous = editable[replaceStart - 1]
                if (previous.isWhitespace() || isDictionarySeparator(previous)) break
                replaceStart -= 1
            }
            while (replaceEnd < editable.length) {
                val next = editable[replaceEnd]
                if (next.isWhitespace() || isDictionarySeparator(next)) break
                replaceEnd += 1
            }
        }

        val hasFollowingWhitespace = replaceEnd < editable.length && editable[replaceEnd].isWhitespace()
        val replacement = if (hasFollowingWhitespace) word else "$word "
        editable.replace(replaceStart, replaceEnd, replacement)
        val nextCursor = (replaceStart + replacement.length).coerceAtMost(editable.length)
        edit.setSelection(nextCursor)
        repairBuffer = editable.toString()
    }

    private fun isDictionarySeparator(char: Char): Boolean {
        return char in listOf('،', ',', '.', '؟', '?', '!', '؛', ':', ';', '\n', '\t')
    }

    private fun handleBackspace() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        if (typedText.isNotEmpty()) deleteLastTrackedGrapheme()
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

    private fun handleEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        typedText.clear()
    }

    /**
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
    }

    private fun pasteToRepairBox() {
        if (!repairExpanded) toggleRepairExpanded()
        val text = clipboardText()
        if (text.isNotBlank()) {
            repairBuffer = text
            repairEditText?.setText(text)
            repairEditText?.setSelection(repairEditText?.text?.length ?: 0)
            scheduleSuggestionRefresh(0L)
        }
    }

    private fun commitFixedRepairText() {
        val rawText = repairEditText?.text?.toString().orEmpty()
        if (rawText.isBlank()) return
        if (!coinManager.canSpendFixCoin()) {
            repairEditText?.setText("الرصيد انتهى")
            repairEditText?.setSelection(repairEditText?.text?.length ?: 0)
            return
        }

        val fixedText = CorrectionEngine.fix(rawText).trim()
        if (fixedText.isEmpty()) return
        val previousContext = currentInputConnection
            ?.getTextBeforeCursor(8, 0)
            ?.toString()
            .orEmpty()
        val boundary = if (needsBoundarySpace(previousContext, fixedText)) " " else ""
        currentInputConnection?.commitText(boundary + fixedText, 1)
        coinManager.consumeFixCoinIfNeeded()
        repairBuffer = ""
        repairEditText?.setText("")
        typedText.clear()
        updateSuggestions()
    }

    private fun needsBoundarySpace(previousText: String, nextText: String): Boolean {
        if (previousText.isEmpty() || nextText.isEmpty()) return false
        val previous = previousText.lastOrNull { Character.getType(it) != Character.FORMAT.toInt() } ?: return false
        val next = nextText.firstOrNull { Character.getType(it) != Character.FORMAT.toInt() } ?: return false
        if (previous.isWhitespace() || next.isWhitespace()) return false
        val closingPunctuation = setOf('،', ',', '.', '؟', '?', '!', '؛', ':', ';', ')', ']', '}', '»')
        return next !in closingPunctuation
    }

    private fun updateSuggestions(source: String = currentSuggestionSource()) {
        val smartMode = smartRowMode(source)
        val shouldShowDictionary = source.isNotBlank()

        if (smartMode != lastSmartRowMode) {
            replaceSmartRow(smartMode, source)
            lastDictionaryVisible = shouldShowDictionary
            return
        }

        lastDictionaryVisible = shouldShowDictionary
        if (smartMode == SmartRowMode.DICTIONARY) {
            rebuildDictionaryContent(dictionarySuggestions(source))
        }
    }

    private fun dictionarySuggestions(source: String): List<SuggestionItem> {
        return suggestionEngine.suggestions(
            input = source,
            language = activeDictionaryLanguage(),
            editorInfo = activeEditorInfo
        )
    }

    private fun activeDictionaryLanguage(): DictionaryLanguage {
        return when (lastLetterMode) {
            KeyboardMode.ARABIC -> DictionaryLanguage.ARABIC
            KeyboardMode.ENGLISH -> DictionaryLanguage.ENGLISH
            KeyboardMode.FRENCH -> DictionaryLanguage.FRENCH
            else -> DictionaryLanguage.ARABIC
        }
    }

    private fun currentSuggestionSource(): String {
        if (repairExpanded) {
            return repairEditText?.text?.toString() ?: repairBuffer
        }
        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(SUGGESTION_CONTEXT_LIMIT, 0)
            ?.toString()
            .orEmpty()
        val trackedText = typedText.toString()

        val resolved = when {
            beforeCursor.isEmpty() -> trackedText
            trackedText.isEmpty() -> beforeCursor
            beforeCursor.endsWith(trackedText) -> beforeCursor
            beforeCursor.length < trackedText.length -> trackedText
            else -> beforeCursor
        }

        if (resolved == beforeCursor && beforeCursor.isNotEmpty()) {
            typedText.clear()
            typedText.append(beforeCursor)
        }
        return resolved
    }

    private fun syncTypedTextFromEditor() {
        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(SUGGESTION_CONTEXT_LIMIT, 0)
            ?.toString()
            .orEmpty()
        if (beforeCursor.isNotEmpty()) {
            typedText.clear()
            typedText.append(beforeCursor)
        }
    }

    private fun clipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }

    private fun activeRows(): List<List<String>> {
        return when (keyboardMode) {
            KeyboardMode.ARABIC -> arabicRows()
            KeyboardMode.ENGLISH -> englishRows()
            KeyboardMode.FRENCH -> frenchRows()
            KeyboardMode.SYMBOLS_1 -> symbolsRowsOne()
            KeyboardMode.SYMBOLS_2 -> symbolsRowsTwo()
        }
    }

    private fun arabicRows(): List<List<String>> {
        return listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            listOf("ذ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د")
        )
    }

    private fun englishRows(): List<List<String>> {
        return listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )
    }

    private fun frenchRows(): List<List<String>> {
        return listOf(
            listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
            listOf("w", "x", "c", "v", "b", "n", "é", "è", "à")
        )
    }

    private fun symbolsRowsOne(): List<List<String>> {
        return listOf(
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            listOf("-", "_", "=", "+", "/", "\\", "|", "~", "«", "»"),
            listOf(":", "؛", "\"", "'", "،", ".", "؟", ",", ";")
        )
    }

    private fun symbolsRowsTwo(): List<List<String>> {
        return listOf(
            listOf("€", "£", "¥", "¢", "©", "®", "™", "°", "·", "×"),
            listOf("±", "÷", "=", "<", ">", "{", "}", "[", "]", "∞"),
            listOf("%", "‰", "•", "…", "!", "?", "√", "π", "§")
        )
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            val keyLike = color == KeyboardColors.key ||
                color == KeyboardColors.keyPressed ||
                color == KeyboardColors.iconKey ||
                color == KeyboardColors.specialKey ||
                color == KeyboardColors.actionKey ||
                color == KeyboardColors.enterKey
            cornerRadius = maxOf(radius, if (keyLike) dp(11) else radius).toFloat()
            when (color) {
                KeyboardColors.key -> setStroke(dp(1), KeyboardColors.keyStroke)
                KeyboardColors.keyPressed -> setStroke(dp(1), KeyboardColors.repairStroke)
                KeyboardColors.iconKey,
                KeyboardColors.specialKey,
                KeyboardColors.actionKey -> setStroke(dp(1), KeyboardColors.specialStroke)
                KeyboardColors.enterKey -> setStroke(dp(1), KeyboardColors.enterStroke)
            }
        }
    }

    private fun ovalBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            when (color) {
                KeyboardColors.repairButton -> setStroke(dp(1), KeyboardColors.enterStroke)
                KeyboardColors.specialKey -> setStroke(dp(1), KeyboardColors.specialStroke)
            }
        }
    }

    private fun roundedStrokeBackground(color: Int, strokeColor: Int, radius: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = maxOf(radius, dp(13)).toFloat()
            setStroke(strokeWidth, strokeColor)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class SmartRowMode {
        TOOLS,
        DICTIONARY,
        HIDDEN
    }

    private enum class ShiftState {
        OFF,
        ONCE,
        LOCKED
    }

    private enum class KeyboardMode {
        ARABIC,
        ENGLISH,
        FRENCH,
        SYMBOLS_1,
        SYMBOLS_2
    }

    private object KeyboardColors {
        val background: Int = Color.rgb(246, 242, 249)
        val panel: Int = Color.rgb(253, 250, 255)
        val panelStroke: Int = Color.rgb(218, 209, 225)
        val repairStroke: Int = Color.rgb(111, 88, 164)
        val key: Int = Color.rgb(255, 252, 255)
        val keyPressed: Int = Color.rgb(229, 219, 242)
        val iconKey: Int = Color.rgb(242, 236, 247)
        val specialKey: Int = Color.rgb(238, 231, 245)
        val actionKey: Int = Color.rgb(238, 231, 245)
        val enterKey: Int = Color.rgb(111, 88, 164)
        val repairActive: Int = Color.rgb(102, 78, 157)
        val repairButton: Int = Color.rgb(111, 88, 164)
        val repairField: Int = Color.rgb(249, 245, 252)
        val wandSurface: Int = Color.rgb(232, 218, 252)
        val wandActiveSurface: Int = Color.rgb(111, 88, 164)
        val toolbarIcon: Int = Color.rgb(78, 69, 84)
        val keyStroke: Int = Color.rgb(215, 206, 222)
        val specialStroke: Int = Color.rgb(210, 199, 220)
        val enterStroke: Int = Color.rgb(88, 65, 139)
        val text: Int = Color.rgb(45, 38, 51)
        val textMuted: Int = Color.rgb(93, 84, 99)
        val languageLabel: Int = Color.rgb(71, 59, 79)
        val disabledIcon: Int = Color.rgb(157, 146, 164)
        val onAccent: Int = Color.WHITE
    }
}
