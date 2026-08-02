from pathlib import Path

KOTLIN = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt')
JOURNAL = Path('PROJECT_JOURNAL.md')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


text = KOTLIN.read_text(encoding='utf-8')
text = replace_once(
    text,
    '''    private var emojiRecentRow: LinearLayout? = null

    private companion object {''',
    '''    private var emojiRecentRow: LinearLayout? = null
    private var appliedNumberRowMode: String? = null
    private var appliedOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private companion object {''',
    'number-row applied state',
)

text = replace_once(
    text,
    '''    override fun onCreateInputView(): View {
        // Patch 04: build the keyboard from the bottom upward.''',
    '''    override fun onCreateInputView(): View {
        appliedNumberRowMode = currentNumberRowMode()
        appliedOrientation = resources.configuration.orientation

        // Patch 04: build the keyboard from the bottom upward.''',
    'capture applied layout state',
)

text = replace_once(
    text,
    '''        updateSuggestions()
    }

    private fun makeStableTopArea(): FrameLayout {''',
    '''        updateSuggestions()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val currentMode = currentNumberRowMode()
        val currentOrientation = resources.configuration.orientation
        if (
            currentMode != appliedNumberRowMode ||
            currentOrientation != appliedOrientation
        ) {
            setInputView(onCreateInputView())
        }
    }

    private fun makeStableTopArea(): FrameLayout {''',
    'refresh layout on input start',
)

text = replace_once(
    text,
    '''    private fun shouldShowNumberRow(): Boolean {
        return when (
            prefs.getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
                ?: NUMBER_ROW_PORTRAIT_ONLY
        ) {''',
    '''    private fun currentNumberRowMode(): String {
        return prefs.getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
            ?: NUMBER_ROW_PORTRAIT_ONLY
    }

    private fun shouldShowNumberRow(): Boolean {
        return when (currentNumberRowMode()) {''',
    'central number-row mode reader',
)

KOTLIN.write_text(text, encoding='utf-8')

journal = JOURNAL.read_text(encoding='utf-8')
journal = replace_once(
    journal,
    '`Patch 31 — Performance, Emoji Panel & Manual Lines` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.',
    '`Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.',
    'journal candidate title',
)
journal = replace_once(
    journal,
    'يجري الآن اختبار `Patch 31 — Performance, Emoji Panel & Manual Lines`.',
    'يجري الآن اختبار `Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row`.',
    'journal stage title',
)
journal = replace_once(
    journal,
    'النطاق محصور في تسريع الكتابة، منع الالتفاف التلقائي، إنشاء لوحة إيموجي مع الأخيرة، والحذف الآمن للرموز وتبديل موضعي زر السطر والعصا.',
    'النطاق محصور في تسريع الكتابة، منع الالتفاف التلقائي، إنشاء لوحة إيموجي مع الأخيرة، الحذف الآمن للرموز، تبديل موضعي زر السطر والعصا، وصف أرقام متكيف يتحكم فيه المستخدم.',
    'journal current scope',
)
journal = replace_once(
    journal,
    '## Patch 31 — Performance, Emoji Panel & Manual Lines (Candidate)',
    '## Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row (Candidate)',
    'Patch 31 heading',
)
journal = replace_once(
    journal,
    '''- يتكيف ارتفاع منطقة المفاتيح وارتفاع الصفوف مع الاتجاه وظهور صف الأرقام، لتصبح اللوحة الأفقية أقرب إلى ارتفاع لوحات النظام دون ترك فراغ لصف مخفي.
- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.''',
    '''- يتكيف ارتفاع منطقة المفاتيح وارتفاع الصفوف مع الاتجاه وظهور صف الأرقام، لتصبح اللوحة الأفقية أقرب إلى ارتفاع لوحات النظام دون ترك فراغ لصف مخفي.
- يعاد تطبيق الاختيار عند بدء الإدخال إذا تغير الإعداد أو اتجاه الشاشة، حتى عند إعادة Android استعمال واجهة اللوحة السابقة.
- اختبار صف الأرقام: تجربة الحالات الثلاث في العمودي والأفقي، ثم الرجوع إلى حقل كتابة دون إعادة تشغيل التطبيق والتأكد من تطبيق الاختيار.
- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.''',
    'journal refresh and test',
)
JOURNAL.write_text(journal, encoding='utf-8')

print('Patch 31 number-row refresh applied')
