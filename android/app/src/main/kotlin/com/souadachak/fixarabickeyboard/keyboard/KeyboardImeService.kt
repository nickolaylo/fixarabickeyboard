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

    override fun onCreate() {
        super.onCreate()
        coinManager = CoinManager(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(KeyboardColors.background)
        }

        root.addView(makeTopBar())
        arabicRows().forEach { row -> root.addView(makeLetterRow(row)) }
        root.addView(makeBottomRow())
        updatePreview()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        typedText.clear()
        updatePreview()
    }

    private fun makeTopBar(): LinearLayout {
        previewText = TextView(this).apply {
            text = "اكتب هنا للإصلاح..."
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            textSize = 16f
            setTextColor(KeyboardColors.text)
            setPadding(dp(14), 0, dp(14), 0)
            setSingleLine(true)
            background = roundedBackground(KeyboardColors.bar, dp(22))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(7))
            addView(makeActionKey("×", KeyboardColors.actionKey) { clearText() }, LinearLayout.LayoutParams(dp(48), dp(46)))
            addView(previewText, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(6), 0, dp(6), 0)
            })
            addView(makeActionKey("✓", KeyboardColors.accent) { replaceTypedTextWithFixedText() }, LinearLayout.LayoutParams(dp(48), dp(46)))
        }
    }

    private fun makeLetterRow(keys: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
            keys.forEach { key ->
                addView(
                    makeLetterKey(key) { handleKey(key) },
                    LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
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
            addView(makeActionKey("123", KeyboardColors.specialKey) {}, LinearLayout.LayoutParams(dp(58), dp(58)).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(makeActionKey("⚙", KeyboardColors.specialKey) {}, LinearLayout.LayoutParams(dp(58), dp(58)).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(makeActionKey("⌫", KeyboardColors.specialKey) { handleKey("⌫") }, LinearLayout.LayoutParams(dp(62), dp(58)).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(makeActionKey("↵", KeyboardColors.enterKey) { handleKey("↵") }, LinearLayout.LayoutParams(dp(70), dp(58)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun makeLetterKey(label: String, onClick: () -> Unit): TextView {
        return makeBaseKey(label, KeyboardColors.key, KeyboardColors.text, 22f, onClick)
    }

    private fun makeActionKey(label: String, color: Int, onClick: () -> Unit): TextView {
        return makeBaseKey(label, color, KeyboardColors.text, 20f, onClick)
    }

    private fun makeSpaceKey(): TextView {
        return makeBaseKey("مسافة", KeyboardColors.key, KeyboardColors.text, 18f) { handleKey("مسافة") }
    }

    private fun makeBaseKey(label: String, bgColor: Int, textColor: Int, size: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = roundedBackground(bgColor, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(45).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(65).start()
                }
                false
            }
        }
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
            listOf("ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د")
        )
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object KeyboardColors {
        val background: Int = Color.rgb(30, 30, 30)
        val bar: Int = Color.rgb(38, 42, 44)
        val key: Int = Color.rgb(64, 64, 64)
        val specialKey: Int = Color.rgb(46, 46, 46)
        val actionKey: Int = Color.rgb(42, 42, 42)
        val enterKey: Int = Color.rgb(66, 49, 145)
        val accent: Int = Color.rgb(79, 210, 167)
        val text: Int = Color.WHITE
    }
}
