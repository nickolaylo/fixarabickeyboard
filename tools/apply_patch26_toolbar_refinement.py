from pathlib import Path
import re

source_path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = source_path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


def replace_regex(pattern: str, replacement: str, label: str) -> None:
    global text
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")


replace_once(
    "    private var repairExpanded: Boolean = false\n",
    "    private var repairExpanded: Boolean = false\n    private var morePanelExpanded: Boolean = false\n",
    "more panel state",
)

replace_once(
    """        keyboardStack.addView(makeStableTopArea())
        keyboardStack.addView(makeNumberRow())
        activeRows().forEachIndexed { index, row ->
            keyboardStack.addView(makeLetterRow(row, showBackspace = index == 2))
        }
        keyboardStack.addView(makeBottomRow())
""",
    """        keyboardStack.addView(makeStableTopArea())
        if (morePanelExpanded) {
            keyboardStack.addView(makeMoreToolsPanel())
        } else {
            keyboardStack.addView(makeNumberRow())
            activeRows().forEachIndexed { index, row ->
                keyboardStack.addView(makeLetterRow(row, showBackspace = index == 2))
            }
            keyboardStack.addView(makeBottomRow())
        }
""",
    "more panel replaces key area",
)

replace_once(
    """            toolsExpanded = true
            lastSmartRowMode = SmartRowMode.TOOLS
""",
    """            toolsExpanded = true
            morePanelExpanded = false
            lastSmartRowMode = SmartRowMode.TOOLS
""",
    "reset more panel on new editor",
)

replace_regex(
    r"    private fun makeRepairInputRow\(\): LinearLayout \{.*?\n    \}\n\n    private fun resizeRepairSlot",
    """    private fun makeRepairInputRow(): LinearLayout {
        repairEditText = EditText(this).apply {
            hint = "اكتب بالعربية هنا…"
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
            addView(repairEditText, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
    }

    private fun resizeRepairSlot""",
    "remove action button from magic field",
)

replace_once(
    "makeSideArrowButton(),",
    "makeMoreToolsButton(),",
    "replace permanent side arrow",
)

replace_once(
    """                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.onAccent else KeyboardColors.repairActive) { toggleRepairExpanded() },
""",
    """                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.onAccent else KeyboardColors.toolbarIcon) { toggleRepairExpanded() },
""",
    "uniform wand icon color",
)

text = text.replace(
    "makeIconButton(R.drawable.ic_keyboard_paste, if (hasClipboardText) KeyboardColors.text else KeyboardColors.disabledIcon, enabled = hasClipboardText)",
    "makeIconButton(R.drawable.ic_keyboard_paste, if (hasClipboardText) KeyboardColors.toolbarIcon else KeyboardColors.disabledIcon, enabled = hasClipboardText)",
)
text = text.replace(
    "makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.textMuted)",
    "makeIconButton(R.drawable.ic_keyboard_emoji, KeyboardColors.toolbarIcon)",
)
text = text.replace(
    "makeIconButton(R.drawable.ic_keyboard_settings, KeyboardColors.textMuted)",
    "makeIconButton(R.drawable.ic_keyboard_settings, KeyboardColors.toolbarIcon)",
)

replace_once(
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        return when {
            repairExpanded && sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            repairExpanded -> SmartRowMode.TOOLS
            toolsExpanded -> SmartRowMode.TOOLS
            sourceText.isNotBlank() -> SmartRowMode.DICTIONARY
            else -> SmartRowMode.DICTIONARY
        }
    }

    private fun makeNumberRow(): LinearLayout {
""",
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
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

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(firstRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(secondRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(247))
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
        val shareText = "Fix Arabic Keyboard\\nhttps://play.google.com/store/apps/details?id=$packageName"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(shareIntent, moreToolLabel("مشاركة لوحة المفاتيح", "Share keyboard", "Partager le clavier"))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooser)
    }

    private fun makeNumberRow(): LinearLayout {
""",
    "more tools panel",
)

enter_call = 'addView(makeActionKey("↵", KeyboardColors.enterKey, 22f) { handleKey("↵") }, bottomParams(dp(64)))'
enter_count = text.count(enter_call)
if enter_count != 3:
    raise RuntimeError(f"primary enter actions: expected 3 matches, found {enter_count}")
text = text.replace(enter_call, 'addView(makePrimaryActionButton(), bottomParams(dp(64)))')

replace_once(
    """    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }
""",
    """    private fun makePrimaryActionButton(): View {
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
            setOnClickListener { commitFixedRepairText() }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }
""",
    "bottom magic action",
)

replace_regex(
    r"    private fun makeRepairSendButton\(enabled: Boolean, onClick: \(\) -> Unit\): ImageButton \{.*?\n    \}\n\n    private fun makeSideArrowButton\(\): ImageButton \{.*?\n    \}",
    """    private fun makeMoreToolsButton(): ImageButton {
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
    }""",
    "replace send and side-arrow helpers",
)

replace_once(
    """            val surfaceColor = when (iconRes) {
                R.drawable.ic_keyboard_magic_wand -> if (repairExpanded) KeyboardColors.wandActiveSurface else KeyboardColors.wandSurface
                R.drawable.ic_keyboard_emoji -> KeyboardColors.iconKey
                else -> Color.TRANSPARENT
            }
""",
    """            val surfaceColor = if (iconRes == R.drawable.ic_keyboard_magic_wand && repairExpanded) {
                KeyboardColors.wandActiveSurface
            } else {
                Color.TRANSPARENT
            }
""",
    "uniform toolbar surfaces",
)

replace_once(
    """    private fun toggleToolsExpanded() {
""",
    """    private fun toggleMorePanel() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        morePanelExpanded = !morePanelExpanded
        setInputView(onCreateInputView())
    }

    private fun toggleToolsExpanded() {
""",
    "more panel toggle",
)

replace_once(
    """        repairExpanded = !repairExpanded
        prefs.edit()
""",
    """        repairExpanded = !repairExpanded
        morePanelExpanded = false
        prefs.edit()
""",
    "close more panel with wand toggle",
)

replace_once(
    """        val wandSurface: Int = Color.rgb(232, 218, 252)
        val wandActiveSurface: Int = Color.rgb(111, 88, 164)
""",
    """        val wandSurface: Int = Color.rgb(232, 218, 252)
        val wandActiveSurface: Int = Color.rgb(111, 88, 164)
        val toolbarIcon: Int = Color.rgb(78, 69, 84)
""",
    "toolbar icon color",
)

source_path.write_text(text, encoding="utf-8")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")
needle = "- لا تغيير في القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob أو التحويل والإرسال النهائي في هذا الباتش."
addition = """- لا تغيير في القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob أو التحويل والإرسال النهائي في هذا الباتش.
- حُذف زر الإرسال من داخل حقل العصا؛ أصبح حقل الكتابة نظيفًا وأوسع.
- يتحول زر الإدخال السفلي إلى أيقونة العصا عند تفعيل الوضع السحري، وهو زر التنفيذ الوحيد.
- أصبحت أيقونات الشريط متساوية في الحجم واللون والمساحة افتراضيًا، ولا تتميز العصا إلا أثناء التفعيل.
- استُبدل السهم الدائم بأيقونة «المزيد» الخاصة بهوية التطبيق، وتتحول إلى رجوع عند فتح لوحة الوظائف.
- لوحة «المزيد» تستبدل منطقة المفاتيح مؤقتًا وتعرض المشاركة والإعدادات والمكافآت والمظهر، دون إدخال ميزات بعيدة عن مهمة إصلاح العربية."""
if needle not in journal:
    raise RuntimeError("journal Patch 26 anchor not found")
journal = journal.replace(needle, addition, 1)
journal_path.write_text(journal, encoding="utf-8")
