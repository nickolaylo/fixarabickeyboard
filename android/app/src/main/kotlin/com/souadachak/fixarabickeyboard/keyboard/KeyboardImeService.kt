package com.souadachak.fixarabickeyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
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
            setPadding(8, 8, 8, 8)
        }

        previewText = TextView(this).apply {
            text = "اكتب هنا للإصلاح..."
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            setPadding(12, 10, 12, 10)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(makeButton("×") { clearText() })
            addView(previewText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(makeButton("✓") { sendFixedText() })
        }

        root.addView(bar)
        arabicRows().forEach { row -> root.addView(makeKeyboardRow(row)) }
        root.addView(makeKeyboardRow(listOf("⌫", "مسافة", "↵")))
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        typedText.clear()
        updatePreview()
    }

    private fun makeKeyboardRow(keys: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            keys.forEach { key -> addView(makeButton(key) { handleKey(key) }, LinearLayout.LayoutParams(0, 56, 1f)) }
        }
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            setOnClickListener { onClick() }
        }
    }

    private fun handleKey(key: String) {
        when (key) {
            "⌫" -> if (typedText.isNotEmpty()) typedText.deleteCharAt(typedText.length - 1)
            "مسافة" -> typedText.append(' ')
            "↵" -> currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            else -> typedText.append(key)
        }
        updatePreview()
    }

    private fun updatePreview() {
        previewText?.text = if (typedText.isEmpty()) "اكتب هنا للإصلاح..." else CorrectionEngine.fix(typedText.toString())
    }

    private fun sendFixedText() {
        if (typedText.isEmpty()) return
        if (!coinManager.canSpendFixCoin()) {
            previewText?.text = "الرصيد انتهى"
            return
        }
        val fixedText = CorrectionEngine.fix(typedText.toString())
        currentInputConnection?.commitText(fixedText, 1)
        coinManager.consumeFixCoinIfNeeded()
        typedText.clear()
        updatePreview()
    }

    private fun clearText() {
        typedText.clear()
        updatePreview()
    }

    private fun arabicRows(): List<List<String>> {
        return listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            listOf("ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د")
        )
    }
}
