from pathlib import Path

KOTLIN = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
JOURNAL = Path("PROJECT_JOURNAL.md")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = KOTLIN.read_text(encoding="utf-8")

text = replace_once(
    text,
    """        val keyboardStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(KeyboardColors.background)""",
    """        val keyboardStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = dp(if (isLandscapeKeyboard()) 3 else 4)
            setPadding(dp(8), verticalPadding, dp(8), verticalPadding)
            setBackgroundColor(KeyboardColors.background)""",
    "compact outer keyboard padding",
)

old_sizes = """    private fun currentKeyAreaHeightDp(): Int {
        val showNumberRow = shouldShowNumberRow()
        return when {
            isLandscapeKeyboard() && showNumberRow -> 226
            isLandscapeKeyboard() -> 190
            showNumberRow -> 264
            else -> 215
        }
    }

    private fun numberKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 38 else 44)
    private fun letterKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 40 else 44)
    private fun functionKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 44 else 48)
    private fun bottomKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 46 else 52)
    private fun rowTopPadding(): Int = dp(if (isLandscapeKeyboard()) 1 else 2)
    private fun rowBottomPadding(): Int = dp(if (isLandscapeKeyboard()) 2 else 3)
    private fun bottomRowTopPadding(): Int = dp(if (isLandscapeKeyboard()) 2 else 4)"""

new_sizes = """    private fun currentKeyAreaHeightDp(): Int {
        val showNumberRow = shouldShowNumberRow()
        return when {
            isLandscapeKeyboard() && showNumberRow -> 196
            isLandscapeKeyboard() -> 160
            showNumberRow -> 218
            else -> 178
        }
    }

    private fun numberKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 34 else 38)
    private fun letterKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 36 else 40)
    private fun functionKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 38 else 42)
    private fun bottomKeyHeight(): Int = dp(if (isLandscapeKeyboard()) 42 else 44)
    private fun rowTopPadding(): Int = dp(if (isLandscapeKeyboard()) 0 else 1)
    private fun rowBottomPadding(): Int = dp(1)
    private fun bottomRowTopPadding(): Int = dp(1)"""

text = replace_once(text, old_sizes, new_sizes, "compact key rows")

category_start = text.index("        val categoryRow = LinearLayout(this).apply {")
category_end = text.index("\n\n        val grid = LinearLayout(this).apply {", category_start)
category_block = text[category_start:category_end]
if "makeBackspaceKey()" in category_block:
    raise RuntimeError("emoji delete key already exists")
trimmed = category_block.rstrip()
if not trimmed.endswith("}"):
    raise RuntimeError("emoji category row did not end as expected")
category_block = trimmed[:-1] + """            addView(
                makeBackspaceKey(),
                LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    setMargins(dp(3), dp(1), dp(3), dp(1))
                }
            )
        }"""
text = text[:category_start] + category_block + text[category_end:]

KOTLIN.write_text(text, encoding="utf-8")

journal = JOURNAL.read_text(encoding="utf-8")
old_final = """- `Patch 30 — Compose Editor Polish` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-02.
- تدمج Patch 30 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #9 هو مصدر البناء الوحيد.
- `Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم."""
new_final = """- `Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-03.
- تدمج Patch 31 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #10 هو مصدر البناء الوحيد.
- `Patch 32 — Emoji Delete & Compact Key Rows` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم."""
journal = replace_once(journal, old_final, new_final, "journal final base")

old_stage = """- اعتُمدت Patch 30 كقاعدة البناء الحالية بعد نجاح مسح المسودة وإدراج الاقتراح عند المؤشر وترتيب صف الإجراءات.
- يجري الآن اختبار `Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row`.
- الفرع النشط: `agent/patch-31-performance-emoji-panel-manual-lines`.
- النطاق محصور في تسريع الكتابة، منع الالتفاف التلقائي، إنشاء لوحة إيموجي مع الأخيرة، الحذف الآمن للرموز، تبديل موضعي زر السطر والعصا، وصف أرقام متكيف يتحكم فيه المستخدم.
- لا تغيير في محتوى القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش."""
new_stage = """- اعتُمدت Patch 31 كقاعدة البناء الحالية بعد نجاح تحسين الأداء ولوحة الإيموجي والأسطر اليدوية وصف الأرقام المتكيف.
- يجري الآن اختبار `Patch 32 — Emoji Delete & Compact Key Rows`.
- الفرع النشط: `agent/patch-32-emoji-delete-and-compact-rows`.
- النطاق محصور في إبقاء زر حذف ظاهر داخل لوحة الإيموجي وضغط الارتفاعات والمسافات الرأسية لصفوف الأرقام والحروف والصف السفلي في الاتجاهين.
- لا تغيير في محتوى القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش."""
journal = replace_once(journal, old_stage, new_stage, "journal current stage")

journal = replace_once(
    journal,
    "## Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row (Candidate)",
    "## Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row (Final Base)",
    "Patch 31 heading",
)

marker = """- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.

## ملاحظات التصميم المعتمدة"""
replacement = """- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.
- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #10 في `main`.

## Patch 32 — Emoji Delete & Compact Key Rows (Candidate)

- أضيف زر حذف ثابت إلى صف فئات الإيموجي، فيبقى متاحًا أثناء تصفح الرموز ولا يضطر المستخدم إلى الرجوع إلى لوحة الحروف.
- يستخدم زر الحذف منطق الحذف الآمن نفسه؛ يحذف التحديد أو آخر رمز كامل من حقل التحويل، ويدعم الضغط المطول للحذف المتتابع.
- خُفضت المسافات الرأسية أعلى وأسفل صفوف الأرقام والحروف والصف السفلي في الاتجاهين.
- خُفض ارتفاع المفاتيح ومنطقة المفاتيح بصورة متوازنة، مع ضغط أقوى في الوضع الأفقي وتقليل الحافة الخارجية الرأسية.
- لا تغيير في أحجام الحروف أو عرض المفاتيح أو محتوى القواميس أو التحويل أو الإيموجي المحفوظة أو العملات أو AdMob.

## ملاحظات التصميم المعتمدة"""
journal = replace_once(journal, marker, replacement, "Patch 32 journal section")

JOURNAL.write_text(journal, encoding="utf-8")
print("Patch 32 transformation completed")
