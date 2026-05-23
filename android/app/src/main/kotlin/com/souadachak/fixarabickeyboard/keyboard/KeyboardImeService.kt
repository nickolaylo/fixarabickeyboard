package com.souadachak.fixarabickeyboard.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardImeService : InputMethodService() {
    private lateinit var coinManager: CoinManager
    private val typedText = StringBuilder()
    private var previewText: TextView? = null
    private var previewContainer: View? = null
    private var isPreviewVisible: Boolean = true

    override fun onCreate() {
        super.onCreate()
        coinManager = CoinManager(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(KeyboardColors.background)
        }

        root.addView(makePreviewBar())
        root.addView(makeToolbarRow())
        arabicRows().forEachIndexed { index, row ->
            root.addView(makeLetterRow(row, showBackspace = index == 2))
        }
        root.addView(makeBottomRow())
        updatePreview()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        typedText.clear()
        updatePreview()
    }

    private fun makePreviewBar(): View {
        previewText = TextView(this).apply {
            text = "اكتب هنا للإصلاح..."
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            textSize = 15f
            setTextColor(KeyboardColors.text)
            setPadding(dp(12), 0, dp(12), 0)
            setSingleLine(true)
            background = roundedStrokeBackground(KeyboardColors.previewBar, KeyboardColors.previewStroke, dp(22), dp(1))
        }

        return LinearLayout(this).apply {
            previewContainer = this
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
            addView(makeActionKey("×", KeyboardColors.actionKey, 18f) { clearText() }, LinearLayout.LayoutParams(dp(44), dp(42)))
            addView(previewText, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(7), 0, dp(7), 0)
            })
            addView(makeActionKey("✓", KeyboardColors.accent, 23f) { replaceTypedTextWithFixedText() }, LinearLayout.LayoutParams(dp(50), dp(42)))
        }
    }

    private fun makeToolbarRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
            background = roundedStrokeBackground(KeyboardColors.toolbar, KeyboardColors.toolbarStroke, dp(18), dp(1))

            addView(makeToolKey("🎙") {}, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(makeToolKey("⚙") {}, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(makeToolKey("▣") {}, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(makeToolKey("GIF") {}, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(makeToolKey("▤") {}, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(makeToolKey("💬") { togglePreviewBar() }, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
    }

    private fun makeLetterRow(keys: List<String>, showBackspace: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(3))
            keys.forEach { key ->
                addView(
                    makeLetterKey(key) { handleKey(key) },
                    LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                )
            }
            if (showBackspace) {
                addView(
                    makeActionKey("⌫", KeyboardColors.specialKey, 20f) { handleKey("⌫") },
                    LinearLayout.LayoutParams(0, dp(48), 1.12f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                )
            }
        }
    }

    private fun makeBottomRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            addView(makeActionKey("123", KeyboardColors.specialKey, 20f) {}, LinearLayout.LayoutParams(dp(62), dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
            addView(makeActionKey("،", KeyboardColors.specialKey, 20f) {}, LinearLayout.LayoutParams(dp(48), dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
            addView(makeActionKey("🌐", KeyboardColors.specialKey, 20f) {}, LinearLayout.LayoutParams(dp(58), dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
            addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            addView(makeActionKey(".", KeyboardColors.specialKey, 20f) { commitAndRemember(".") }, LinearLayout.LayoutParams(dp(48), dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
            addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, LinearLayout.LayoutParams(dp(72), dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun makeLetterKey(label: String, onClick: () -> Unit): TextView {
        return makeBaseKey(label, KeyboardColors.key, KeyboardColors.text, 20f, Typeface.NORMAL, onClick)
    }

    private fun makeActionKey(label: String, color: Int, size: Float, onClick: () -> Unit): TextView {
        return makeBaseKey(label, color, KeyboardColors.text, size, Typeface.BOLD, onClick)
    }

    private fun makeToolKey(label: String, onClick: () -> Unit): TextView {
        return makeBaseKey(label, Color.TRANSPARENT, KeyboardColors.toolText, 18f, Typeface.BOLD, onClick)
    }

    private fun makeSpaceKey(): TextView {
        return makeBaseKey("العربية", KeyboardColors.key, KeyboardColors.text, 17f, Typeface.NORMAL) { handleKey("مسافة") }
    }

    private fun makeBaseKey(label: String, bgColor: Int, textColor: Int, size: Float, style: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, style)
            setTextColor(textColor)
            background = roundedBackground(bgColor, dp(9))
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(40).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(55).start()
                }
                false
            }
        }
    }

    private fun togglePreviewBar() {
        isPreviewVisible = !isPreviewVisible
        previewContainer?.visibility = if (isPreviewVisible) View.VISIBLE else View.GONE
    }

    private fun handleKey(key: String) {
        when (key) {
            "⌫" -> handleBackspace()
            "مسافة" -> commitAndRemember(" ")
            "↵" -> handleEnter()
            else -> commitAndRemember(key)
        }
        updatePreview()
    }

    private fun commitAndRemember(text: String) {
        currentInputConnection?.commitText(text, 1)
        typedText.append(text)
    }

    private fun handleBackspace() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        if (typedText.isNotEmpty()) typedText.deleteCharAt(typedText.length - 1)
        updatePreview()
    }

    private fun handleEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        typedText.clear()
        updatePreview()
    }

    private fun replaceTypedTextWithFixedText() {
        if (typedText.isEmpty()) return
        if (!coinManager.canSpendFixCoin()) {
            previewText?.text = "الرصيد انتهى"
            return
        }
        val rawText = typedText.toString()
        val fixedText = CorrectionEngine.fix(rawText)
        currentInputConnection?.deleteSurroundingText(rawText.length, 0)
        currentInputConnection?.commitText(fixedText, 1)
        coinManager.consumeFixCoinIfNeeded()
        typedText.clear()
        updatePreview()
    }

    private fun clearText() {
        if (typedText.isNotEmpty()) {
            currentInputConnection?.deleteSurroundingText(typedText.length, 0)
            typedText.clear()
        }
        updatePreview()
    }

    private fun updatePreview() {
        previewText?.text = if (typedText.isEmpty()) "اكتب هنا للإصلاح..." else CorrectionEngine.fix(typedText.toString())
    }

    private fun arabicRows(): List<List<String>> {
        return listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            listOf("ذ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د")
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

    private object KeyboardColors {
        val background: Int = Color.rgb(38, 50, 56)
        val previewBar: Int = Color.rgb(43, 51, 55)
        val previewStroke: Int = Color.rgb(119, 213, 203)
        val toolbar: Int = Color.rgb(43, 51, 55)
        val toolbarStroke: Int = Color.rgb(52, 64, 69)
        val key: Int = Color.rgb(61, 73, 79)
        val specialKey: Int = Color.rgb(47, 59, 65)
        val actionKey: Int = Color.rgb(47, 59, 65)
        val enterKey: Int = Color.rgb(108, 178, 174)
        val accent: Int = Color.rgb(75, 207, 165)
        val text: Int = Color.rgb(238, 238, 238)
        val toolText: Int = Color.rgb(185, 194, 198)
    }
}
