from pathlib import Path

source_path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = source_path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "    private var repairOverlayView: View? = null\n",
    "    private var repairOverlayView: View? = null\n    private var keyAreaContainer: FrameLayout? = null\n",
    "key area container state",
)

replace_once(
    "    private companion object {\n        const val SUGGESTION_CONTEXT_LIMIT = 160\n    }\n",
    "    private companion object {\n        const val SUGGESTION_CONTEXT_LIMIT = 160\n        const val KEY_AREA_HEIGHT_DP = 264\n    }\n",
    "fixed key area height",
)

replace_once(
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
    """        keyboardStack.addView(makeStableTopArea())
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
            if (morePanelExpanded) makeMoreToolsPanel() else makeKeyboardKeyArea(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        keyboardStack.addView(keyArea)
""",
    "fixed key area in root",
)

replace_once(
    "            .coerceIn(1, 4)\n        return when (lines) {\n            1 -> dp(45)\n            2 -> dp(69)\n            3 -> dp(93)\n            else -> dp(117)\n        }\n",
    "            .coerceIn(1, 2)\n        return if (lines == 1) dp(45) else dp(69)\n",
    "two-line compose height",
)

replace_once(
    "            maxLines = 4\n",
    "            maxLines = 2\n",
    "two-line compose limit",
)

replace_once(
    "                    topMargin = -dp(2)\n                    rightMargin = dp(5)\n",
    "                    topMargin = -dp(3)\n                    rightMargin = -dp(4)\n",
    "close button position",
)

replace_once(
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }

    private fun makeMoreToolsPanel(): LinearLayout {
""",
    """    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded) return SmartRowMode.TOOLS
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

    private fun makeMoreToolsPanel(): LinearLayout {
""",
    "keyboard key area helper and toolbar override",
)

replace_once(
    """        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(firstRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(secondRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(247))
        }
""",
    """        val title = TextView(this).apply {
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
""",
    "named fixed-height more page",
)

replace_once(
    """    private fun toggleMorePanel() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        morePanelExpanded = !morePanelExpanded
        setInputView(onCreateInputView())
    }
""",
    """    private fun toggleMorePanel() {
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
    }
""",
    "stable more page toggle",
)

source_path.write_text(text, encoding="utf-8")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")

journal = journal.replace(
    "- `Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.\n- تدمج Patch 27 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #6 هو مصدر البناء الوحيد.\n- `Patch 28 — Magic Compose Close & Multiline Input` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.\n",
    "- `Patch 28 — Magic Compose Close & Multiline Input` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.\n- تدمج Patch 28 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #7 هو مصدر البناء الوحيد.\n- `Patch 29 — Stable More Page & Compact Compose` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.\n",
    1,
)

start = journal.index("## المرحلة الحالية")
end = journal.index("## Patch 24", start)
journal = journal[:start] + """## المرحلة الحالية

- اكتمل الأساس البصري للتطبيق المرافق ولوحة Android الفعلية.
- اعتُمدت Patch 28 كقاعدة البناء الحالية بعد نجاح زر الإغلاق المستقل ودعم الأسطر المتعددة.
- يجري الآن اختبار `Patch 29 — Stable More Page & Compact Compose`.
- الفرع النشط: `agent/patch-29-stable-more-page-compact-compose`.
- النطاق محصور في صفحة المزيد الثابتة، منع القفز، تقليل حقل التحويل إلى سطرين، وتحسين موضع زر الإغلاق.
- لا تغيير في القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.

""" + journal[end:]

journal = journal.replace(
    "## Patch 28 — Magic Compose Close & Multiline Input (Candidate)",
    "## Patch 28 — Magic Compose Close & Multiline Input (Final Base)",
    1,
)
patch28_anchor = "- لا تغيير في القواميس أو الاقتراحات أو العملات أو AdMob أو نظام المكافآت."
patch28_pos = journal.index(patch28_anchor, journal.index("## Patch 28")) + len(patch28_anchor)
journal = journal[:patch28_pos] + "\n- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #7 في `main`." + journal[patch28_pos:]

patch29 = """

## Patch 29 — Stable More Page & Compact Compose (Candidate)

- عند فتح صفحة «المزيد» تختفي اقتراحات القاموس ويظهر شريط الأيقونات كاملًا فوق الصفحة.
- صفحة «المزيد» تستبدل منطقة المفاتيح فقط، وتحتوي المشاركة والإعدادات والمكافآت والمظهر.
- أصبحت منطقة المفاتيح وصفحة المزيد داخل حاوية واحدة ثابتة الارتفاع، فلا يحدث انكماش ثم قفز عند التبديل.
- يتم تبديل المحتوى داخل الحاوية مباشرة دون إعادة بناء لوحة المفاتيح كاملة.
- خُفّض الحد الأقصى لحقل التحويل من أربعة أسطر إلى سطرين، بما يناسب الوضعين العمودي والأفقي.
- تحرك زر `×` أكثر نحو الزاوية اليمنى مع بقائه خارج مساحة النص العملية.
- لا تغيير في القواميس أو الاقتراحات أو العملات أو AdMob أو نظام المكافآت أو منطق التحويل.
"""
insert_at = journal.index("## ملاحظات التصميم المعتمدة")
journal = journal[:insert_at] + patch29 + "\n" + journal[insert_at:]
journal_path.write_text(journal, encoding="utf-8")
