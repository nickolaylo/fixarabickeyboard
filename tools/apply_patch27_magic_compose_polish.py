from pathlib import Path
import re

source_path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = source_path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


def replace_regex(pattern: str, replacement: str, label: str) -> None:
    global text
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")


replace_once(
    """        val smartSlotHeight = dp(49)
        val repairSlotHeight = dp(52)
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(2) else 0
""",
    """        val smartSlotHeight = dp(43)
        val repairSlotHeight = dp(45)
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(1) else 0
""",
    "compact stable top area",
)

replace_once(
    """                    ).apply { bottomMargin = dp(2) }
""",
    """                    ).apply { bottomMargin = dp(1) }
""",
    "compact repair margin",
)

replace_once(
    """        val minimumRepairSlotHeight = dp(58)
""",
    """        val minimumRepairSlotHeight = dp(45)
""",
    "compact repair minimum",
)

replace_once(
    """        return when (lines) {
            1 -> dp(58)
            2 -> dp(82)
            3 -> dp(106)
            else -> dp(130)
        }
""",
    """        return when (lines) {
            1 -> dp(45)
            2 -> dp(69)
            3 -> dp(93)
            else -> dp(117)
        }
""",
    "compact repair height scale",
)

replace_regex(
    r"    private fun makeRepairInputRow\(\): LinearLayout \{.*?\n    \}\n\n    private fun resizeRepairSlot",
    """    private fun makeRepairInputRow(): LinearLayout {
        repairEditText = EditText(this).apply {
            hint = "اكتب النص هنا لتحويله."
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textSize = 16f
            minLines = 1
            maxLines = 1
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(12), dp(2), dp(12), dp(2))
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
                override fun afterTextChanged(text: Editable?) {}
            })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(14), dp(1))
            clipChildren = false
            clipToPadding = false
            addView(repairEditText, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
    }

    private fun resizeRepairSlot""",
    "compact repair input and hint",
)

replace_regex(
    r"    private fun makeSmartRow\(mode: SmartRowMode, suggestions: List<SuggestionItem>\): LinearLayout \{.*?\n    private fun smartRowMode",
    """    private fun makeSmartRow(mode: SmartRowMode, suggestions: List<SuggestionItem>): LinearLayout {
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
            makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.toolbarIcon) { handleKey("🙂") },
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

    private fun smartRowMode""",
    "compact toolbar and suggestion buttons",
)

replace_once(
    """            Color.TRANSPARENT,
            KeyboardColors.text,
            15f,
""",
    """            KeyboardColors.key,
            KeyboardColors.text,
            15f,
""",
    "suggestion button surface",
)

replace_once(
    """                dp(49)
""",
    """                dp(43)
""",
    "compact smart row replacement height",
)

replace_regex(
    r"    private fun commitFixedRepairText\(\) \{.*?\n    \}\n\n    private fun updateSuggestions",
    """    private fun commitFixedRepairText() {
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

    private fun updateSuggestions""",
    "safe boundary between converted messages",
)

source_path.write_text(text, encoding="utf-8")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")

journal = journal.replace(
    """- المرحلة التالية تعالج فصل الرسائل المتتابعة، تخفيف ارتفاع سطر الإدخال وشريط الأدوات، وتحويل اقتراحات القاموس إلى أزرار أوضح.
- لا تغيير في نظام المكافآت أو AdMob أو الإرسال النهائي إلى التطبيق الهدف ضمن هذه المرحلة الصغيرة.
""",
    """- يجري الآن اختبار `Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys`.
- الفرع النشط: `agent/patch-27-magic-compose-spacing-compact-suggestions`.
- النطاق محصور في فصل الرسائل المتتابعة، تخفيف ارتفاع سطر الإدخال وشريط الأدوات، وتحويل اقتراحات القاموس إلى أزرار أوضح.
- لا تغيير في نظام المكافآت أو AdMob أو الإرسال النهائي إلى التطبيق الهدف ضمن هذا الباتش.
""",
    1,
)

anchor = "- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #5 في `main`.\n"
addition = anchor + """
## Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys (Candidate)

- يمنع التصاق الرسالة الجديدة بنهاية الرسالة السابقة؛ تضاف مسافة فاصلة واحدة فقط عندما يحتاج السياق إليها.
- لا تضاف مسافة زائدة إذا كان قبل المؤشر فراغ بالفعل أو إذا بدأت النتيجة بعلامة ترقيم ختامية.
- أصبح هنت حقل العصا: `اكتب النص هنا لتحويله.`
- خُفّض ارتفاع سطر الإدخال وشريط الأدوات، وقلّت الهوامش الرأسية حول الأيقونات مع الحفاظ على وضوح اللمس.
- تظهر اقتراحات القاموس داخل مستطيلات مستديرة بنفس سطح مفاتيح اللوحة بدل النصوص العائمة.
- لا تغيير في القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob.
"""
if anchor not in journal:
    raise RuntimeError("Patch 26 journal anchor not found")
journal = journal.replace(anchor, addition, 1)

journal += """
## اختبار Patch 27

- إرسال `السلام عليكم` ثم `يا شباب` والتأكد أن النتيجة لا تلتصق وأن بينهما مسافة واحدة فقط.
- تجربة رسالة جديدة بعد فراغ موجود وبعد علامات الترقيم للتأكد من عدم إنشاء مسافات زائدة.
- فتح العصا والتأكد أن الهنت هو `اكتب النص هنا لتحويله.`
- مقارنة ارتفاع سطر الإدخال وشريط الأدوات قبل وبعد التحديث، خاصة على شاشة صغيرة.
- كتابة أول حرف والتأكد أن اقتراحات القاموس تظهر كأزرار مستطيلة واضحة وقابلة للمس.
- تشغيل `flutter analyze` و`flutter test` ثم الاختبار على Android.
"""

journal_path.write_text(journal, encoding="utf-8")
