from pathlib import Path

path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """    private var stableTopArea: FrameLayout? = null
    private var smartTopSlotView: View? = null
""",
    """    private var stableTopArea: FrameLayout? = null
    private var topStack: LinearLayout? = null
    private var smartTopSlotView: View? = null
""",
    "top stack field",
)

replace_once(
    """    private fun makeStableTopArea(): FrameLayout {
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
""",
    """    private fun makeStableTopArea(): FrameLayout {
        val sourceText = currentSuggestionSource()
        val smartMode = smartRowMode(sourceText)
        lastDictionaryVisible = sourceText.isNotBlank()
        lastSmartRowMode = smartMode

        val smartSlotHeight = dp(49)
        val repairSlotHeight = dp(52)
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(2) else 0

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
                    ).apply { bottomMargin = dp(2) }
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
""",
    "stacked repair row above toolbar",
)

replace_once(
    """    private fun makeRepairInputRow(): LinearLayout {
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
                    sendButton.setColorFilter(if (active) KeyboardColors.onAccent else KeyboardColors.disabledIcon)
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
""",
    """    private fun makeRepairInputRow(): LinearLayout {
        val sendEnabled = repairBuffer.isNotBlank()
        val sendButton = makeRepairSendButton(sendEnabled) { commitFixedRepairText() }

        repairEditText = EditText(this).apply {
            hint = "اكتب بالعربية هنا… سنصلحها عند الإرسال"
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textSize = 17f
            minLines = 1
            maxLines = 1
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(14), dp(6), dp(14), dp(6))
            minHeight = dp(44)
            background = roundedBackground(KeyboardColors.repairField, dp(14))
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
                    sendButton.setColorFilter(if (active) KeyboardColors.onAccent else KeyboardColors.disabledIcon)
                    sendButton.background = ovalBackground(if (active) KeyboardColors.repairButton else KeyboardColors.specialKey)
                }
                override fun afterTextChanged(text: Editable?) {}
            })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(16), dp(1))
            clipChildren = false
            clipToPadding = false
            addView(
                sendButton,
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(0, 0, dp(4), 0) }
            )
            addView(repairEditText, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
    }
""",
    "single-line magic compose field",
)

replace_once(
    """                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.repairActive else KeyboardColors.textMuted) { toggleRepairExpanded() },
""",
    """                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.onAccent else KeyboardColors.repairActive) { toggleRepairExpanded() },
""",
    "active wand icon",
)

replace_once(
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        return when {
            toolsExpanded -> SmartRowMode.TOOLS
            sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            else -> SmartRowMode.DICTIONARY
        }
    }
""",
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        return when {
            repairExpanded && sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            repairExpanded -> SmartRowMode.TOOLS
            toolsExpanded -> SmartRowMode.TOOLS
            sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            else -> SmartRowMode.DICTIONARY
        }
    }
""",
    "repair-aware smart row mode",
)

replace_once(
    """            val surfaceColor = when (iconRes) {
                R.drawable.ic_keyboard_magic_wand -> KeyboardColors.wandSurface
                R.drawable.ic_keyboard_emoji -> KeyboardColors.iconKey
                else -> Color.TRANSPARENT
            }
""",
    """            val surfaceColor = when (iconRes) {
                R.drawable.ic_keyboard_magic_wand -> if (repairExpanded) KeyboardColors.wandActiveSurface else KeyboardColors.wandSurface
                R.drawable.ic_keyboard_emoji -> KeyboardColors.iconKey
                else -> Color.TRANSPARENT
            }
""",
    "active wand surface",
)

replace_once(
    """    private fun toggleRepairExpanded() {
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
""",
    """    private fun toggleRepairExpanded() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        repairExpanded = !repairExpanded
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()

        // Patch 26: opening the wand adds one compact compose line above the toolbar.
        // The toolbar keeps its place and only its content changes with the text state.
        setInputView(onCreateInputView())
    }
""",
    "wand toggle rebuild",
)

replace_once(
    """    private fun refreshStableTopAreaOnly() {
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
""",
    """    private fun replaceSmartRow(mode: SmartRowMode, sourceText: String) {
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
                dp(49)
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
""",
    "smart toolbar replacement",
)

replace_once(
    """    private fun updateSuggestions(source: String = currentSuggestionSource()) {
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
""",
    """    private fun updateSuggestions(source: String = currentSuggestionSource()) {
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
""",
    "stable smart toolbar updates",
)

replace_once(
    """        val repairButton: Int = Color.rgb(111, 88, 164)
        val wandSurface: Int = Color.rgb(232, 218, 252)
""",
    """        val repairButton: Int = Color.rgb(111, 88, 164)
        val repairField: Int = Color.rgb(249, 245, 252)
        val wandSurface: Int = Color.rgb(232, 218, 252)
        val wandActiveSurface: Int = Color.rgb(111, 88, 164)
""",
    "magic compose colors",
)

path.write_text(text, encoding="utf-8")
print("Patch 26 source transformation completed")
