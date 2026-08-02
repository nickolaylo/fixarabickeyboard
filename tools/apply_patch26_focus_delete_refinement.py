from pathlib import Path
import re

path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = path.read_text(encoding="utf-8")


def once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    text = text.replace(old, new, 1)

once(
    "    private var deleteRepeatRunnable: Runnable? = null\n",
    "    private var deleteRepeatRunnable: Runnable? = null\n    private var deleteRepeatCount: Int = 0\n",
    "delete repeat counter",
)

once(
    """            addView(
                makeIconButton(R.drawable.ic_keyboard_magic_wand, if (repairExpanded) KeyboardColors.onAccent else KeyboardColors.toolbarIcon) { toggleRepairExpanded() },
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
            )
""",
    """            if (!repairExpanded) {
                addView(
                    makeIconButton(R.drawable.ic_keyboard_magic_wand, KeyboardColors.toolbarIcon) { toggleRepairExpanded() },
                    LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
                )
            }
""",
    "hide toolbar wand while magic mode is active",
)

once(
    """    private fun rebuildDictionaryContent(suggestions: List<SuggestionItem>) {
        val row = suggestionsRow ?: return
        row.removeViews(1, (row.childCount - 2).coerceAtLeast(0))
        val wand = row.getChildAt(row.childCount - 1)
        row.removeView(wand)
        row.addDictionaryContentToSmartRow(suggestions)
        row.addView(
            wand,
            LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
        )
    }
""",
    """    private fun rebuildDictionaryContent(suggestions: List<SuggestionItem>) {
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
                LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, 0, 0) }
            )
        }
    }
""",
    "dictionary refresh with optional wand",
)

once(
    """            setText(repairBuffer)
            setSelection(text?.length ?: 0)
            addTextChangedListener(object : TextWatcher {
""",
    """            setText(repairBuffer)
            setSelection(text?.length ?: 0)
            setShowSoftInputOnFocus(false)
            isCursorVisible = true
            post {
                requestFocus()
                setSelection(text?.length ?: 0)
                isCursorVisible = true
            }
            addTextChangedListener(object : TextWatcher {
""",
    "auto focus magic field",
)

once(
    """            "⌫" -> {
                val start = edit.selectionStart.coerceAtLeast(0)
                val end = edit.selectionEnd.coerceAtLeast(0)
                if (start != end) {
                    editable.delete(kotlin.math.min(start, end), kotlin.math.max(start, end))
                } else if (cursor > 0) {
                    editable.delete(cursor - 1, cursor)
                }
            }
""",
    """            "⌫" -> {
                val start = edit.selectionStart.coerceAtLeast(0)
                val end = edit.selectionEnd.coerceAtLeast(0)
                if (start != end) {
                    editable.delete(kotlin.math.min(start, end), kotlin.math.max(start, end))
                } else if (cursor > 0) {
                    editable.delete(cursor - 1, cursor)
                } else if (editable.isEmpty()) {
                    handleBackspace()
                }
            }
""",
    "delete target text from empty magic field",
)

once(
    """    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                handleKey("⌫")
                deleteRepeatHandler.postDelayed(this, 55L)
            }
        }
        deleteRepeatHandler.postDelayed(deleteRepeatRunnable!!, 330L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
    }
""",
    """    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        deleteRepeatCount = 0
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                handleKey("⌫")
                deleteRepeatCount += 1
                val nextDelay = when {
                    deleteRepeatCount < 6 -> 42L
                    deleteRepeatCount < 16 -> 30L
                    else -> 20L
                }
                deleteRepeatHandler.postDelayed(this, nextDelay)
            }
        }
        deleteRepeatHandler.postDelayed(deleteRepeatRunnable!!, 260L)
    }

    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
        deleteRepeatCount = 0
    }
""",
    "accelerated long-press delete",
)

once(
    """            setOnClickListener { commitFixedRepairText() }
""",
    """            setOnClickListener {
                if (repairBuffer.isBlank()) {
                    toggleRepairExpanded()
                } else {
                    commitFixedRepairText()
                }
            }
""",
    "bottom wand dual action",
)

path.write_text(text, encoding="utf-8")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")
anchor = "- لوحة «المزيد» تستبدل منطقة المفاتيح مؤقتًا وتعرض المشاركة والإعدادات والمكافآت والمظهر، دون إدخال ميزات بعيدة عن مهمة إصلاح العربية."
addition = """- لوحة «المزيد» تستبدل منطقة المفاتيح مؤقتًا وتعرض المشاركة والإعدادات والمكافآت والمظهر، دون إدخال ميزات بعيدة عن مهمة إصلاح العربية.
- تظهر عصا الشريط فقط قبل تفعيل الوضع؛ بعد التفعيل تختفي كي لا توجد عصوان، وتبقى العصا السفلية هي زر التنفيذ/الخروج.
- إذا كان حقل العصا فارغًا، يمرر زر الحذف إلى حقل التطبيق الهدف بدل إجبار المستخدم على إغلاق الوضع.
- الحذف المطوّل يتسارع تدريجيًا بعد بداية أقصر ليقترب من الإحساس السريع في لوحات النظام.
- يطلب حقل العصا التركيز تلقائيًا فور فتح الوضع، ويبدأ مؤشر الكتابة بالنبض دون لمسة إضافية.
- العصا السفلية تنفذ الإصلاح عند وجود نص، وتغلق الوضع عندما يكون حقل العصا فارغًا."""
if anchor not in journal:
    raise RuntimeError("journal anchor not found")
journal = journal.replace(anchor, addition, 1)
journal_path.write_text(journal, encoding="utf-8")
