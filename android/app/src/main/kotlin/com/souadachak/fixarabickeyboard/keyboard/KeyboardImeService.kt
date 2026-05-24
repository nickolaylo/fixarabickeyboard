package com.souadachak.fixarabickeyboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.souadachak.fixarabickeyboard.R

class KeyboardImeService : InputMethodService() {
    private lateinit var coinManager: CoinManager
    private val typedText = StringBuilder()
    private var keyboardMode: KeyboardMode = KeyboardMode.ARABIC
    private var toolsExpanded: Boolean = false
    private var repairExpanded: Boolean = false
    private var repairEditText: EditText? = null
    private var suggestionsRow: LinearLayout? = null
    private var keyPreviewPopup: PopupWindow? = null

    private val prefs by lazy { getSharedPreferences("keyboard_ui_state", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        coinManager = CoinManager(this)
        toolsExpanded = prefs.getBoolean("tools_expanded", false)
        repairExpanded = prefs.getBoolean("repair_expanded", false)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(KeyboardColors.background)
        }

        // The top area is now intentionally simple:
        // 1) Repair input appears only when the user enables repair mode.
        // 2) The next row is either suggestions while typing, or a calm toolbar when empty.
        if (repairExpanded) root.addView(makeRepairInputRow())
        root.addView(makeSuggestionRow())
        root.addView(makeNumberRow())
        activeRows().forEachIndexed { index, row ->
            root.addView(makeLetterRow(row, showBackspace = index == 2))
        }
        root.addView(makeBottomRow())
        updateSuggestions()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        typedText.clear()
        repairEditText?.setText("")
        updateSuggestions()
    }

    private fun makeRepairInputRow(): LinearLayout {
        repairEditText = EditText(this).apply {
            hint = "اكتب النص هنا فقط..."
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textSize = 18f
            minLines = 1
            maxLines = 5
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minHeight = dp(44)
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(14), dp(1))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 0, 0, dp(5))
            addView(repairEditText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                makeIconButton(R.drawable.ic_keyboard_send_to_app, KeyboardColors.repairActive) { commitFixedRepairText() },
                LinearLayout.LayoutParams(dp(46), dp(44)).apply { setMargins(dp(4), 0, 0, 0) }
            )
        }
    }

    private fun makeToolsRow(): LinearLayout {
        val icons = listOf(
            R.drawable.ic_keyboard_paste to { pasteToRepairBox() },
            R.drawable.ic_keyboard_language to { switchMode(KeyboardMode.ARABIC) },
            R.drawable.ic_keyboard_emoji to {},
            R.drawable.ic_keyboard_settings to { openAppSettings() },
            R.drawable.ic_keyboard_more to {}
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.panelStroke, dp(14), dp(1))
            icons.forEachIndexed { index, item ->
                addView(
                    makeIconButton(item.first, KeyboardColors.text, item.second),
                    LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                        val right = if (index == icons.lastIndex) 0 else dp(1)
                        setMargins(0, 0, right, 0)
                    }
                )
            }
        }
    }

    private fun makeSuggestionRow(): LinearLayout {
        return LinearLayout(this).apply {
            suggestionsRow = this
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.panelStroke, dp(14), dp(1))
        }
    }

    private fun rebuildSuggestionRow(suggestions: List<String>, sourceText: String) {
        val row = suggestionsRow ?: return
        row.removeAllViews()

        // The side actions must always stay visible:
        // left = tools arrow, right = magic wand / repair mode.
        // When tools are expanded, only the middle area changes.
        row.addView(
            makeIconButton(if (toolsExpanded) R.drawable.ic_keyboard_expand_more else R.drawable.ic_keyboard_expand_less, KeyboardColors.textMuted) { toggleToolsExpanded() },
            LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(0, 0, dp(2), 0) }
        )

        if (toolsExpanded) {
            row.addView(
                makeToolsRow(),
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
            )
        } else if (sourceText.isBlank()) {
            val hasClipboardText = clipboardText().isNotBlank()
            row.addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(
                makeIconButton(R.drawable.ic_keyboard_paste, if (hasClipboardText) KeyboardColors.text else KeyboardColors.disabledIcon, enabled = hasClipboardText) { pasteToRepairBox() },
                LinearLayout.LayoutParams(dp(46), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) }
            )
            row.addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f))
        } else {
            suggestions.take(3).forEach { word ->
                row.addView(
                    makeSuggestionKey(word) { commitSuggestion(word) },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
                )
            }
            while (row.childCount < 4) {
                row.addView(View(this@KeyboardImeService), LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }

        row.addView(
            makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.repairActive else KeyboardColors.textMuted) { toggleRepairExpanded() },
            LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
        )
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
            keys.forEach { key ->
                addView(makeLetterKey(key) { handleKey(key) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            if (showBackspace) {
                addView(makeActionKey("⌫", KeyboardColors.specialKey, 20f) { handleKey("⌫") }, LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    private fun makeBottomRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)

            when (keyboardMode) {
                KeyboardMode.ARABIC -> {
                    addView(makeActionKey("?123", KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
                    addView(makeActionKey("،", KeyboardColors.specialKey, 17f) { handleKey("،") }, bottomParams(dp(34)))
                    addView(makeBottomIconButton(R.drawable.ic_keyboard_emoji) {}, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey(".", KeyboardColors.specialKey, 17f) { handleKey(".") }, bottomParams(dp(34)))
                    addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
                }
                KeyboardMode.SYMBOLS_1 -> {
                    addView(makeActionKey("أبج", KeyboardColors.specialKey, 15f) { switchMode(KeyboardMode.ARABIC) }, bottomParams(dp(50)))
                    addView(makeActionKey("،", KeyboardColors.specialKey, 17f) { handleKey("،") }, bottomParams(dp(34)))
                    addView(makeActionKey("1/2", KeyboardColors.specialKey, 14f) { switchMode(KeyboardMode.SYMBOLS_2) }, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey(".", KeyboardColors.specialKey, 17f) { handleKey(".") }, bottomParams(dp(34)))
                    addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
                }
                KeyboardMode.SYMBOLS_2 -> {
                    addView(makeActionKey("أبج", KeyboardColors.specialKey, 15f) { switchMode(KeyboardMode.ARABIC) }, bottomParams(dp(50)))
                    addView(makeActionKey("«", KeyboardColors.specialKey, 17f) { handleKey("«") }, bottomParams(dp(34)))
                    addView(makeActionKey("2/2", KeyboardColors.specialKey, 14f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(38)))
                    addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                    addView(makeActionKey("»", KeyboardColors.specialKey, 17f) { handleKey("»") }, bottomParams(dp(34)))
                    addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))
                }
            }
        }
    }

    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }

    private fun makeLetterKey(label: String, onClick: () -> Unit): TextView = makeBaseKey(label, KeyboardColors.key, KeyboardColors.text, 20f, Typeface.NORMAL, true, onClick)

    private fun makeActionKey(label: String, color: Int, size: Float, onClick: () -> Unit): TextView = makeBaseKey(label, color, KeyboardColors.text, size, Typeface.BOLD, label.length <= 2 && label != "⌫" && label != "↵", onClick)

    private fun makeSuggestionKey(label: String, onClick: () -> Unit): TextView = makeBaseKey(label, Color.TRANSPARENT, KeyboardColors.text, 15f, Typeface.NORMAL, false, onClick)

    private fun makeToolButton(label: String, bgColor: Int, textColor: Int, size: Float, onClick: () -> Unit): TextView = makeBaseKey(label, bgColor, textColor, size, Typeface.BOLD, false, onClick)


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

    private fun makeSpaceKey(): TextView {
        var downX = 0f
        return makeBaseKey("‹ العربية ›", KeyboardColors.key, KeyboardColors.text, 15f, Typeface.NORMAL, false) { handleKey("مسافة") }.apply {
            setOnTouchListener { view, event ->
                animatePress(view, event, null, false)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> downX = event.x
                    MotionEvent.ACTION_UP -> {
                        val delta = event.x - downX
                        if (kotlin.math.abs(delta) > dp(42)) {
                            switchToNextInputMethod(false)
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
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

    private fun toggleToolsExpanded() {
        toolsExpanded = !toolsExpanded
        if (toolsExpanded) repairExpanded = false
        prefs.edit()
            .putBoolean("tools_expanded", toolsExpanded)
            .putBoolean("repair_expanded", repairExpanded)
            .apply()
        setInputView(onCreateInputView())
    }

    private fun toggleRepairExpanded() {
        repairExpanded = !repairExpanded
        if (repairExpanded) toolsExpanded = false
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()
        setInputView(onCreateInputView())
    }

    private fun openAppSettings() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun switchMode(newMode: KeyboardMode) {
        keyboardMode = newMode
        setInputView(onCreateInputView())
    }

    private fun handleKey(key: String) {
        if (repairExpanded) {
            handleRepairBoxKey(key)
            return
        }
        when (key) {
            "⌫" -> handleBackspace()
            "مسافة" -> commitAndRemember(" ")
            "↵" -> handleEnter()
            else -> commitAndRemember(key)
        }
        updateSuggestions()
    }

    private fun handleRepairBoxKey(key: String) {
        val edit = repairEditText ?: return
        val editable = edit.text ?: return
        val cursor = edit.selectionStart.coerceAtLeast(0)
        when (key) {
            "⌫" -> {
                if (cursor > 0) editable.delete(cursor - 1, cursor)
            }
            "مسافة" -> editable.insert(cursor, " ")
            "↵" -> editable.insert(cursor, "\n")
            else -> editable.insert(cursor, key)
        }
        updateSuggestions(edit.text.toString())
    }

    private fun commitAndRemember(text: String) {
        currentInputConnection?.commitText(text, 1)
        typedText.append(text)
    }

    private fun commitSuggestion(word: String) {
        if (repairExpanded) {
            val edit = repairEditText ?: return
            replaceLastTokenInEditText(edit, word)
            updateSuggestions(edit.text.toString())
            return
        }
        val current = typedText.toString()
        val lastToken = current.trim().split(Regex("\\s+")).lastOrNull().orEmpty()
        if (lastToken.isNotEmpty()) {
            currentInputConnection?.deleteSurroundingText(lastToken.length, 0)
            typedText.delete(typedText.length - lastToken.length, typedText.length)
        }
        currentInputConnection?.commitText("$word ", 1)
        typedText.append(word).append(' ')
        updateSuggestions()
    }

    private fun replaceLastTokenInEditText(edit: EditText, word: String) {
        val text = edit.text.toString()
        val trimmedEnd = text.trimEnd()
        val lastSpace = trimmedEnd.lastIndexOf(' ')
        val prefix = if (lastSpace >= 0) trimmedEnd.substring(0, lastSpace + 1) else ""
        edit.setText("$prefix$word ")
        edit.setSelection(edit.text.length)
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
        repairEditText?.setText("")
        typedText.clear()
        updateSuggestions()
    }

    private fun updateSuggestions(source: String = if (repairExpanded) repairEditText?.text?.toString().orEmpty() else typedText.toString()) {
        rebuildSuggestionRow(CorrectionEngine.suggestions(source), source)
    }

    private fun clipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }

    private fun activeRows(): List<List<String>> {
        return when (keyboardMode) {
            KeyboardMode.ARABIC -> arabicRows()
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

    private fun roundedStrokeBackground(color: Int, strokeColor: Int, radius: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, strokeColor)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class KeyboardMode {
        ARABIC,
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
        val disabledIcon: Int = Color.rgb(116, 128, 134)
    }
}
