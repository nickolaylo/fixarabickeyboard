package com.souadachak.fixarabickeyboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    private var repairEditText: EditText? = null
    private var suggestionsRow: LinearLayout? = null
    private var repairBuffer: String = ""
    private var lastDictionaryVisible: Boolean = false
    private var lastSmartRowMode: SmartRowMode = SmartRowMode.TOOLS
    private var keyPreviewPopup: PopupWindow? = null
    private var shiftState: ShiftState = ShiftState.OFF
    private var lastRepairSlotHeight: Int = 0
    private var stableTopArea: FrameLayout? = null
    private var smartTopSlotView: View? = null
    private var repairTopSlotView: View? = null
    private var repairRowContainer: FrameLayout? = null
    private var keyboardRoot: FrameLayout? = null
    private var repairOverlayView: View? = null
    private val deleteRepeatHandler = Handler(Looper.getMainLooper())
    private var deleteRepeatRunnable: Runnable? = null

    private companion object {
        const val SUGGESTION_CONTEXT_LIMIT = 160
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
        keyboardStack.addView(makeNumberRow())
        activeRows().forEachIndexed { index, row ->
            keyboardStack.addView(makeLetterRow(row, showBackspace = index == 2))
        }
        keyboardStack.addView(makeBottomRow())

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

        if (!restarting) {
            typedText.clear()
            repairBuffer = ""
            repairEditText?.setText("")
            lastDictionaryVisible = false
            toolsExpanded = true
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

        val smartSlotHeight = dp(49)
        return FrameLayout(this).apply {
            stableTopArea = this
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                smartSlotHeight
            )

            // Patch 09: a real fixed TopSlot.
            // Smart row and repair row are children of the same FrameLayout,
            // so they overlap in the same coordinates instead of stacking vertically.
            val smartContent = makeSmartRow(smartMode, dictionarySuggestions(sourceText))
            smartTopSlotView = smartContent
            addView(
                smartContent,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    smartSlotHeight,
                    Gravity.TOP
                )
            )

            val repairContent = makeRepairInputRow()
            repairTopSlotView = repairContent
            // Patch 10: RepairRow and SmartRow must share the exact same TopSlot height.
            // Do not let the repair row be taller than the slot here; otherwise it looks
            // like a stacked row above the keyboard instead of replacing the smart row.
            addView(
                repairContent,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP
                )
            )

            applyTopSlotState(animated = false)
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
        val minimumRepairSlotHeight = dp(58)
        if (!repairExpanded) {
            // Patch 05: keep the physical slot height reserved even while the repair row is hidden.
            // This prevents the IME window from changing height when the wand is toggled.
            // The repair view itself is not drawn, so no repair panel/background is visible.
            return minimumRepairSlotHeight
        }
        return repairSlotHeightForText(repairBuffer).coerceAtLeast(minimumRepairSlotHeight)
    }

    private fun repairSlotHeightForText(text: String): Int {
        val explicitLines = text.count { it == '\n' } + 1
        val estimatedLines = (text.length / 34) + 1
        val lines = kotlin.math.max(explicitLines, estimatedLines).coerceIn(1, 4)
        return when (lines) {
            1 -> dp(58)
            2 -> dp(82)
            3 -> dp(106)
            else -> dp(130)
        }
    }

    private fun makeRepairInputRow(): LinearLayout {
        val sendEnabled = repairBuffer.isNotBlank()
        val sendButton = makeRepairSendButton(sendEnabled) { commitFixedRepairText() }

        repairEditText = EditText(this).apply {
            hint = "اكتب النص هنا فقط…\nوضع الإصلاح مفعل…"
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textSize = 18f
            minLines = 1
            maxLines = 4
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minHeight = dp(44)
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(14), dp(1))
            setText(repairBuffer)
            setSelection(text?.length ?: 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    repairBuffer = text?.toString().orEmpty()
                    val active = repairBuffer.isNotBlank()
                    sendButton.isEnabled = active
                    sendButton.isClickable = active
                    sendButton.isFocusable = active
                    sendButton.alpha = if (active) 1f else 0.40f
                    sendButton.setColorFilter(if (active) KeyboardColors.text else KeyboardColors.disabledIcon)
                    sendButton.background = ovalBackground(if (active) KeyboardColors.repairButton else KeyboardColors.specialKey)
                    val nextHeight = repairSlotHeightForText(repairBuffer)
                    if (repairExpanded && nextHeight != lastRepairSlotHeight) {
                        resizeRepairSlot(nextHeight)
                    }
                }
                override fun afterTextChanged(text: Editable?) {}
            })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Patch 11: RepairRow is the visual replacement of SmartRow inside the same TopSlot.
            // Give it an opaque panel background so the SmartRow/icons never show through behind it.
            setPadding(0, dp(2), 0, dp(3))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(9), dp(1))
            clipChildren = false
            clipToPadding = false
            addView(
                sendButton,
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(0, 0, dp(4), 0) }
            )
            addView(repairEditText, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(
                makeIconButton(R.drawable.ic_keyboard_magic_wand, KeyboardColors.repairActive) { toggleRepairExpanded() },
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(4), 0, 0, 0) }
            )
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
            setPadding(0, dp(2), 0, dp(3))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.panelStroke, dp(9), dp(1))

            addView(
                makeSideArrowButton(),
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(0, 0, dp(2), 0) }
            )

            when (mode) {
                SmartRowMode.TOOLS -> addToolIconsToSmartRow(hasClipboardText)
                SmartRowMode.DICTIONARY -> addDictionaryContentToSmartRow(suggestions)
                SmartRowMode.HIDDEN -> addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f))
            }

            addView(
                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.repairActive else KeyboardColors.textMuted) { toggleRepairExpanded() },
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
            )
        }
    }

    private fun LinearLayout.addToolIconsToSmartRow(hasClipboardText: Boolean) {
        addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f))
        addView(
            makeIconButton(R.drawable.ic_keyboard_paste, if (hasClipboardText) KeyboardColors.text else KeyboardColors.disabledIcon, enabled = hasClipboardText) { pasteToRepairBox() },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) }
        )
        addView(
            makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.textMuted) { handleKey("🙂") },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) }
        )
        addView(
            makeIconButton(R.drawable.ic_keyboard_settings, KeyboardColors.textMuted) { openAppSettings() },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) }
        )
        addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f))
    }

    private fun LinearLayout.addDictionaryContentToSmartRow(suggestions: List<SuggestionItem>) {
        val visibleSuggestions = suggestions.take(3)
        visibleSuggestions.forEach { suggestion ->
            addView(
                makeSuggestionKey(suggestion.displayText) { commitSuggestion(suggestion.commitText) },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
            )
        }
        repeat((3 - visibleSuggestions.size).coerceAtLeast(0)) {
            addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun rebuildDictionaryContent(suggestions: List<SuggestionItem>) {
        val row = suggestionsRow ?: return
        row.removeViews(1, (row.childCount - 2).coerceAtLeast(0))
        val wand = row.getChildAt(row.childCount - 1)
        row.removeView(wand)
        row.addDictionaryContentToSmartRow(suggestions)
        row.addView(
            wand,
            LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
        )
    }

    private fun smartRowMode(sourceText: String): SmartRowMode {
        return when {
            toolsExpanded -> SmartRowMode.TOOLS
            sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            else -> SmartRowMode.DICTIONARY
        }
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
                    addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
                }
                KeyboardMode.SYMBOLS_2 -> {
                    addView(makeActionKey(languageReturnLabel(), KeyboardColors.specialKey, 15f) { switchMode(lastLetterMode) }, bottomParams(dp(50)))
                    addView(makeActionKey("«", KeyboardColors.specialKey, 17f) { handleKey("«") }, bottomParams(dp(34)))
                    addView(makeActionKey("2/2", KeyboardColors.specialKey, 14f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey("»", KeyboardColors.specialKey, 17f) { handleKey("»") }, bottomParams(dp(34)))
                    addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
                }
            }
        }
    }

    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeActionKey(comma, KeyboardColors.specialKey, 17f) { handleKey(comma) }, bottomParams(dp(34)))
        addView(makeBottomIconButton(R.drawable.ic_keyboard_emoji) { handleKey("🙂") }, bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
    }

    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }

    private fun makeLetterKey(label: String, onClick: () -> Unit): TextView = makeBaseKey(label, KeyboardColors.key, KeyboardColors.text, 20f, Typeface.NORMAL, true, onClick)

    private fun makeActionKey(label: String, color: Int, size: Float, onClick: () -> Unit): TextView = makeBaseKey(label, color, KeyboardColors.text, size, Typeface.BOLD, label.length <= 2 && label != "⌫" && label != "↵", onClick)

    private fun makeSuggestionKey(label: String, onClick: () -> Unit): TextView = makeBaseKey(label, Color.TRANSPARENT, KeyboardColors.text, 15f, Typeface.NORMAL, false, onClick)

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

    private fun makeRepairSendButton(enabled: Boolean, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard_fat_arrow_up)
            setColorFilter(if (enabled) KeyboardColors.text else KeyboardColors.disabledIcon)
            background = ovalBackground(if (enabled) KeyboardColors.repairButton else KeyboardColors.specialKey)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.40f
            isClickable = enabled
            isFocusable = enabled
            contentDescription = null
            setOnClickListener { if (isEnabled) onClick() }
            setOnTouchListener { view, event ->
                if (isEnabled) animatePress(view, event, null, false) else false
            }
        }
    }

    private fun makeSideArrowButton(): ImageButton {
        return ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard_expand_less)
            setColorFilter(KeyboardColors.textMuted)
            background = roundedBackground(Color.TRANSPARENT, dp(14))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            rotation = if (toolsExpanded) -90f else 90f
            contentDescription = null
            setOnClickListener {
                val nextRotation = if (toolsExpanded) 90f else -90f
                animate()
                    .rotation(nextRotation)
                    .setDuration(120)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { toggleToolsExpanded() }
                    .start()
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
            background = roundedBackground(Color.TRANSPARENT, dp(14))
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
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                handleKey("⌫")
                deleteRepeatHandler.postDelayed(this, 55L)
            }
        }
        deleteRepeatHandler.postDelayed(deleteRepeatRunnable!!, 330L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
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
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        repairExpanded = !repairExpanded
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()

        // Patch 08: do not add a separate repair overlay above the smart row.
        // Refresh only the stable top slot so repair replaces icons/suggestions.
        refreshStableTopAreaOnly()
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

    private fun refreshStableTopAreaOnly() {
        val topArea = stableTopArea ?: return
        val sourceText = currentSuggestionSource()
        val smartMode = smartRowMode(sourceText)
        val smartSlotHeight = dp(49)

        lastDictionaryVisible = sourceText.isNotBlank()
        lastSmartRowMode = smartMode

        // Patch 09: keep the TopSlot fixed and never add a second vertical row.
        // Only rebuild the smart child when its content changes; the repair child
        // stays in the same FrameLayout coordinate space.
        val oldSmart = smartTopSlotView
        if (oldSmart != null) topArea.removeView(oldSmart)
        val smartContent = makeSmartRow(smartMode, dictionarySuggestions(sourceText))
        smartTopSlotView = smartContent
        topArea.addView(
            smartContent,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                smartSlotHeight,
                Gravity.TOP
            )
        )

        repairTopSlotView?.bringToFront()
        applyTopSlotState(animated = true)
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
        updateSuggestions()
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
                    editable.delete(cursor - 1, cursor)
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
    }

    private fun commitAndRemember(text: String) {
        currentInputConnection?.commitText(text, 1)
        typedText.append(text)
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
            updateSuggestions(edit.text.toString())
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
        val text = edit.text.toString()
        val shouldAppend = text.isEmpty() || text.last().isWhitespace() || isDictionarySeparator(text.last())
        val nextText = if (shouldAppend) {
            "$text$word "
        } else {
            val trimmedEnd = text.trimEnd()
            val lastSpace = trimmedEnd.lastIndexOf(' ')
            val prefix = if (lastSpace >= 0) trimmedEnd.substring(0, lastSpace + 1) else ""
            "$prefix$word "
        }
        edit.setText(nextText)
        edit.setSelection(edit.text.length)
        repairBuffer = edit.text.toString()
    }

    private fun isDictionarySeparator(char: Char): Boolean {
        return char in listOf('،', ',', '.', '؟', '?', '!', '؛', ':', ';', '\n', '\t')
    }

    private fun handleBackspace() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        if (typedText.isNotEmpty()) typedText.deleteCharAt(typedText.length - 1)
        updateSuggestions()
    }

    private fun handleEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        typedText.clear()
        updateSuggestions()
    }

    private fun pasteToRepairBox() {
        if (!repairExpanded) toggleRepairExpanded()
        val text = clipboardText()
        if (text.isNotBlank()) {
            repairBuffer = text
            repairEditText?.setText(text)
            repairEditText?.setSelection(repairEditText?.text?.length ?: 0)
            updateSuggestions(text)
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
        val fixedText = CorrectionEngine.fix(rawText)
        currentInputConnection?.commitText(fixedText, 1)
        coinManager.consumeFixCoinIfNeeded()
        repairBuffer = ""
        repairEditText?.setText("")
        typedText.clear()
        updateSuggestions()
    }

    private fun updateSuggestions(source: String = currentSuggestionSource()) {
        val smartMode = smartRowMode(source)
        val shouldShowDictionary = source.isNotBlank()

        // While the user is typing inside the repair box, do not rebuild the full keyboard
        // for every character. This keeps writing stable and removes the visible lag.
        if (repairExpanded && repairEditText != null) {
            if (smartMode == SmartRowMode.DICTIONARY) {
                rebuildDictionaryContent(dictionarySuggestions(source))
            }
            lastDictionaryVisible = shouldShowDictionary
            lastSmartRowMode = smartMode
            return
        }

        if (smartMode != lastSmartRowMode) {
            setInputView(onCreateInputView())
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
            cornerRadius = radius.toFloat()
        }
    }

    private fun ovalBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun roundedStrokeBackground(color: Int, strokeColor: Int, radius: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
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
        val background: Int = Color.rgb(38, 50, 56)
        val panel: Int = Color.rgb(35, 46, 52)
        val panelStroke: Int = Color.rgb(50, 66, 74)
        val repairStroke: Int = Color.rgb(115, 87, 168)
        val key: Int = Color.rgb(61, 73, 79)
        val keyPressed: Int = Color.rgb(95, 107, 112)
        val iconKey: Int = Color.rgb(44, 56, 62)
        val specialKey: Int = Color.rgb(47, 59, 65)
        val actionKey: Int = Color.rgb(47, 59, 65)
        val enterKey: Int = Color.rgb(108, 178, 174)
        val repairActive: Int = Color.rgb(185, 142, 255)
        val repairButton: Int = Color.rgb(92, 61, 155)
        val text: Int = Color.rgb(238, 238, 238)
        val textMuted: Int = Color.rgb(172, 184, 190)
        val languageLabel: Int = Color.rgb(142, 154, 160)
        val disabledIcon: Int = Color.rgb(116, 128, 134)
    }
}
