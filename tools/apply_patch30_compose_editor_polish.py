from pathlib import Path
import re

SOURCE = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
JOURNAL = Path("PROJECT_JOURNAL.md")

text = SOURCE.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE",
    "inputType = InputType.TYPE_CLASS_TEXT or\n                InputType.TYPE_TEXT_FLAG_MULTI_LINE or\n                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS",
    "disable repair-field spell checking",
)

old_bottom = '''    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeActionKey(comma, KeyboardColors.specialKey, 17f) { handleKey(comma) }, bottomParams(dp(34)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        } else {
            addView(makeBottomIconButton(R.drawable.ic_keyboard_emoji) { handleKey("🙂") }, bottomParams(dp(38)))
        }
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        addView(makePrimaryActionButton(), bottomParams(dp(64)))
    }

'''
new_bottom = '''    private fun LinearLayout.addTextModeBottomRow(symbolsLabel: String, comma: String, period: String) {
        addView(makeActionKey(symbolsLabel, KeyboardColors.specialKey, 16f) { switchMode(KeyboardMode.SYMBOLS_1) }, bottomParams(dp(50)))
        addView(makeCommaEmojiKey(comma), bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(makeActionKey(period, KeyboardColors.specialKey, 17f) { handleKey(period) }, bottomParams(dp(34)))
        addView(makePrimaryActionButton(), bottomParams(dp(64)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        }
    }

    private fun makeCommaEmojiKey(comma: String): FrameLayout {
        return FrameLayout(this).apply {
            background = roundedBackground(KeyboardColors.specialKey, dp(11))
            isClickable = true
            isFocusable = true
            contentDescription = moreToolLabel(
                "فاصلة، ضغط مطول لإدخال رمز تعبيري",
                "Comma, long press for emoji",
                "Virgule, appui long pour emoji"
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
                handleKey("🙂")
                true
            }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }
    }

'''
replace_once(old_bottom, new_bottom, "compact emoji and newline layout")

old_toggle = '''    private fun toggleRepairExpanded() {
        if (repairExpanded) {
            repairBuffer = repairEditText?.text?.toString() ?: repairBuffer
        }
        repairExpanded = !repairExpanded
        morePanelExpanded = false
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()

        // Patch 26: opening the wand adds one compact compose line above the toolbar.
        // The toolbar keeps its place and only its content changes with the text state.
        setInputView(onCreateInputView())
    }
'''
new_toggle = '''    private fun toggleRepairExpanded() {
        if (repairExpanded) {
            // Patch 30: closing conversion mode discards the draft by design.
            repairBuffer = ""
            repairEditText?.text?.clear()
        }
        repairExpanded = !repairExpanded
        morePanelExpanded = false
        prefs.edit()
            .putBoolean("repair_expanded", repairExpanded)
            .putBoolean("tools_expanded", toolsExpanded)
            .apply()

        // Patch 26: opening the wand adds one compact compose line above the toolbar.
        // The toolbar keeps its place and only its content changes with the text state.
        setInputView(onCreateInputView())
    }
'''
replace_once(old_toggle, new_toggle, "discard draft on close")

old_replace = '''    private fun replaceOrAppendTokenInEditText(edit: EditText, word: String) {
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
'''
new_replace = '''    private fun replaceOrAppendTokenInEditText(edit: EditText, word: String) {
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
'''
replace_once(old_replace, new_replace, "cursor-aware dictionary insertion")

SOURCE.write_text(text, encoding="utf-8")

journal = JOURNAL.read_text(encoding="utf-8")
old_header = '''- `Patch 28 — Magic Compose Close & Multiline Input` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.
- تدمج Patch 28 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #7 هو مصدر البناء الوحيد.
- `Patch 29 — Stable More Page & Compact Compose` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.'''
new_header = '''- `Patch 29 — Stable More Page & Compact Compose` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.
- تدمج Patch 29 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #8 هو مصدر البناء الوحيد.
- `Patch 30 — Compose Editor Polish` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.'''
if old_header not in journal:
    raise RuntimeError("journal final-base header was not found")
journal = journal.replace(old_header, new_header, 1)

old_phase = '''- اعتُمدت Patch 28 كقاعدة البناء الحالية بعد نجاح زر الإغلاق المستقل ودعم الأسطر المتعددة.
- يجري الآن اختبار `Patch 29 — Stable More Page & Compact Compose`.
- الفرع النشط: `agent/patch-29-stable-more-page-compact-compose`.
- النطاق محصور في صفحة المزيد الثابتة، منع القفز، تقليل حقل التحويل إلى سطرين، وتحسين موضع زر الإغلاق.
- لا تغيير في القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.'''
new_phase = '''- اعتُمدت Patch 29 كقاعدة البناء الحالية بعد نجاح صفحة المزيد الثابتة وحقل التحويل ذي السطرين.
- يجري الآن اختبار `Patch 30 — Compose Editor Polish`.
- الفرع النشط: `agent/patch-30-compose-editor-polish`.
- النطاق محصور في مسح المسودة عند الإغلاق، إصلاح إدراج الاقتراح عند المؤشر، منع الخطوط الحمراء، ودمج الإيموجي مع زر الفاصلة وإعادة ترتيب زر السطر الجديد.
- لا تغيير في القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.'''
if old_phase not in journal:
    raise RuntimeError("journal phase block was not found")
journal = journal.replace(old_phase, new_phase, 1)

journal = journal.replace(
    "## Patch 29 — Stable More Page & Compact Compose (Candidate)",
    "## Patch 29 — Stable More Page & Compact Compose (Final Base)",
    1,
)
patch29_heading = "## Patch 29 — Stable More Page & Compact Compose (Final Base)"
patch29_start = journal.find(patch29_heading)
if patch29_start < 0:
    raise RuntimeError("Patch 29 section not found")
next_heading = journal.find("\n## ", patch29_start + len(patch29_heading))
if next_heading < 0:
    raise RuntimeError("Could not locate end of Patch 29 section")
patch29_block = journal[patch29_start:next_heading]
adoption_line = "- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #8 في `main`."
if adoption_line not in patch29_block:
    patch29_block = patch29_block.rstrip() + "\n" + adoption_line + "\n"
    journal = journal[:patch29_start] + patch29_block + journal[next_heading:]

patch30_section = '''
## Patch 30 — Compose Editor Polish (Candidate)

- عند إغلاق وضع التحويل تُمسح المسودة فورًا ولا تعود عند فتحه مجددًا.
- أصبح اختيار اقتراح القاموس يعتمد موضع المؤشر والتحديد ويحترم فواصل الأسطر، فلا يعود إلى السطر السابق بعد إنشاء سطر جديد.
- عُطلت اقتراحات التدقيق الإملائي داخل حقل التحويل لمنع الخطوط الحمراء تحت كلمات القاموس.
- أزيل زر الإيموجي المستقل من الصف السفلي، وأصبح رمزًا صغيرًا خافتًا داخل زر الفاصلة ويُستخدم بالضغط المطول.
- استُغلت المساحة المحررة لتثبيت زر السطر الجديد بعد عصا التنفيذ أثناء وضع الإصلاح.
- لا تغيير في محتوى القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob أو نظام المكافآت.

'''
if "## Patch 30 — Compose Editor Polish" not in journal:
    marker = "## ملاحظات التصميم المعتمدة"
    if marker not in journal:
        raise RuntimeError("journal design-notes marker not found")
    journal = journal.replace(marker, patch30_section + marker, 1)

JOURNAL.write_text(journal, encoding="utf-8")
