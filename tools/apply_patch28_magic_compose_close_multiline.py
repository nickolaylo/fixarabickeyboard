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
    """        val smartSlotHeight = dp(43)
        val repairSlotHeight = dp(45)
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(1) else 0
""",
    """        val smartSlotHeight = dp(43)
        val repairSlotHeight = if (repairExpanded) currentRepairSlotHeight() else dp(45)
        if (repairExpanded) lastRepairSlotHeight = repairSlotHeight
        val totalHeight = smartSlotHeight + if (repairExpanded) repairSlotHeight + dp(1) else 0
""",
    "dynamic repair slot height",
)

replace_regex(
    r"    private fun repairSlotHeightForText\(text: String\): Int \{.*?\n    \}\n\n    private fun makeRepairInputRow\(\): LinearLayout \{.*?\n    \}\n\n    private fun resizeRepairSlot",
    """    private fun repairSlotHeightForText(text: String): Int {
        val lines = text
            .split('\\n')
            .sumOf { line -> ((line.length / 34) + 1).coerceAtLeast(1) }
            .coerceIn(1, 4)
        return when (lines) {
            1 -> dp(45)
            2 -> dp(69)
            3 -> dp(93)
            else -> dp(117)
        }
    }

    private fun makeRepairInputRow(): FrameLayout {
        repairEditText = EditText(this).apply {
            hint = "اكتب النص هنا لتحويله."
            gravity = Gravity.RIGHT or Gravity.TOP
            textSize = 16f
            minLines = 1
            maxLines = 4
            setSingleLine(false)
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(KeyboardColors.text)
            setHintTextColor(KeyboardColors.textMuted)
            includeFontPadding = false
            setPadding(dp(12), dp(7), dp(36), dp(5))
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
                override fun afterTextChanged(text: Editable?) {
                    post { updateRepairAreaHeight(text?.toString().orEmpty()) }
                }
            })
        }

        val closeButton = TextView(this).apply {
            text = "×"
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(KeyboardColors.toolbarIcon)
            includeFontPadding = false
            background = roundedStrokeBackground(
                KeyboardColors.panel,
                KeyboardColors.repairStroke,
                dp(13),
                dp(1)
            )
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = moreToolLabel("إغلاق وضع التحويل", "Close conversion mode", "Fermer le mode de conversion")
            setOnClickListener { toggleRepairExpanded() }
            setOnTouchListener { view, event -> animatePress(view, event, null, false) }
        }

        return FrameLayout(this).apply {
            setPadding(dp(2), 0, dp(2), dp(2))
            background = roundedStrokeBackground(KeyboardColors.panel, KeyboardColors.repairStroke, dp(14), dp(1))
            clipChildren = false
            clipToPadding = false
            addView(
                repairEditText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    topMargin = dp(5)
                }
            )
            addView(
                closeButton,
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.RIGHT).apply {
                    topMargin = -dp(2)
                    rightMargin = dp(5)
                }
            )
        }
    }

    private fun updateRepairAreaHeight(text: String) {
        if (!repairExpanded) return
        val nextHeight = repairSlotHeightForText(text)
        if (nextHeight == lastRepairSlotHeight) return
        lastRepairSlotHeight = nextHeight

        repairTopSlotView?.let { view ->
            view.layoutParams = view.layoutParams.apply { height = nextHeight }
            view.requestLayout()
        }
        stableTopArea?.let { area ->
            area.layoutParams = area.layoutParams.apply {
                height = dp(43) + nextHeight + dp(1)
            }
            area.requestLayout()
        }
        repairOverlayView?.let { overlay ->
            overlay.layoutParams = overlay.layoutParams.apply { height = nextHeight }
            overlay.requestLayout()
        }
    }

    private fun resizeRepairSlot""",
    "multiline repair field and close button",
)

replace_once(
    """        addView(makeActionKey(comma, KeyboardColors.specialKey, 17f) { handleKey(comma) }, bottomParams(dp(34)))
        addView(makeBottomIconButton(R.drawable.ic_keyboard_emoji) { handleKey("🙂") }, bottomParams(dp(38)))
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
""",
    """        addView(makeActionKey(comma, KeyboardColors.specialKey, 17f) { handleKey(comma) }, bottomParams(dp(34)))
        if (repairExpanded) {
            addView(makeActionKey("↵", KeyboardColors.specialKey, 18f) { handleKey("↵") }, bottomParams(dp(38)))
        } else {
            addView(makeBottomIconButton(R.drawable.ic_keyboard_emoji) { handleKey("🙂") }, bottomParams(dp(38)))
        }
        addView(makeSpaceKey(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
""",
    "line break key in repair mode",
)

replace_once(
    """            setOnClickListener {
                if (repairBuffer.isBlank()) {
                    toggleRepairExpanded()
                } else {
                    commitFixedRepairText()
                }
            }
""",
    """            setOnClickListener {
                if (repairBuffer.isNotBlank()) commitFixedRepairText()
            }
""",
    "bottom wand no longer closes empty mode",
)

source_path.write_text(text, encoding="utf-8")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")
journal = re.sub(
    r"## Final Base الحالية\n\n.*?\n\n## المرحلة الحالية",
    """## Final Base الحالية

- `Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.
- تدمج Patch 27 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #6 هو مصدر البناء الوحيد.
- `Patch 28 — Magic Compose Close & Multiline Input` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.

## المرحلة الحالية""",
    journal,
    count=1,
    flags=re.S,
)
journal = re.sub(
    r"- الفرع النشط: `[^`]+`\.\n- Draft PR النشطة: `#[^`]+`\.\n- نطاق Patch \d+ يقتصر على.*?\n",
    """- الفرع النشط: `agent/patch-28-magic-compose-close-and-multiline`.
- نطاق Patch 28 يقتصر على إغلاق وضع التحويل ودعم الأسطر المتعددة؛ لا يغير القواميس أو المكافآت أو AdMob أو منطق التحويل.
""",
    journal,
    count=1,
    flags=re.S,
)
anchor = "## ملاحظات التصميم المعتمدة"
section = """## Patch 28 — Magic Compose Close & Multiline Input (Candidate)

- أُلغي إغلاق وضع التحويل عبر الضغط على العصا السفلية عندما يكون الحقل فارغًا.
- أضيف زر `×` مستقل فوق الحافة اليمنى لحقل التحويل، ويغلق الوضع دون حذف المسودة المكتوبة.
- العصا السفلية أصبحت للتنفيذ فقط؛ وإذا كان الحقل فارغًا لا تنفذ شيئًا ولا تغلق الوضع.
- أصبح حقل التحويل متعدد الأسطر حتى أربعة أسطر، ويتمدد تدريجيًا حسب المحتوى بدل حجز مساحة كبيرة دائمًا.
- أثناء وضع التحويل تتحول أيقونة الإيموجي في الصف السفلي إلى زر سطر جديد `↵` واضح، بينما تبقى العصا للتنفيذ.
- يحافظ التحويل على فواصل الأسطر؛ لذلك يمكن إعداد عدة جمل، كل جملة في سطر، ثم تحويلها دفعة واحدة.
- لا تغيير في القواميس أو الاقتراحات أو العملات أو AdMob أو نظام المكافآت.

"""
if anchor not in journal:
    raise RuntimeError("journal design anchor not found")
journal = journal.replace(anchor, section + anchor, 1)

journal += """

## اختبار Patch 28

- فتح العصا والتأكد من ظهور زر `×` فوق يمين الحقل لا داخله.
- الضغط على العصا السفلية والحقل فارغ؛ يجب أن يبقى وضع التحويل مفتوحًا.
- إغلاق الوضع عبر `×` ثم فتحه مجددًا والتأكد من بقاء المسودة دون فقدانها.
- كتابة أربع جمل، كل جملة في سطر، والتأكد من تمدد الحقل تدريجيًا دون قفزة غير ضرورية.
- الضغط على زر `↵` في الصف السفلي لإضافة سطر جديد، ثم تنفيذ التحويل بالعصا السفلية.
- التأكد من وصول النص المحول مع فواصل الأسطر نفسها إلى التطبيق الهدف.
- اختبار Portrait وLandscape والشاشة الصغيرة والخط الكبير.
"""
journal_path.write_text(journal, encoding="utf-8")
