from pathlib import Path

ROOT = Path('.')
ENGINE = ROOT / 'android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/SuggestionEngine.kt'
SERVICE = ROOT / 'android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt'
JOURNAL = ROOT / 'PROJECT_JOURNAL.md'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


engine = ENGINE.read_text(encoding='utf-8')
engine = replace_once(
    engine,
    '''        if (currentToken.isEmpty()) {
            return arrangeSuggestions(
                learned = learned,
                dictionaryWords = language.defaultWords,
                language = language,
                allowDefaultFallbacks = true
            )
        }
''',
    '''        if (currentToken.isEmpty()) {
            // A completed word with no learned continuation must not fall back to
            // the same generic greetings after every space. Keep only a genuinely
            // learned next-word candidate; otherwise the toolbar is shown.
            return learned?.let { candidate ->
                listOf(
                    SuggestionItem(
                        displayText = candidate.word,
                        commitText = candidate.word,
                        learnedContextWords = candidate.contextWords
                    )
                )
            }.orEmpty()
        }
''',
    'cold-start fallback removal',
)
ENGINE.write_text(engine, encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')
service = replace_once(
    service,
    '''        val sourceText = currentSuggestionSource()
        val smartMode = smartRowMode(sourceText)
        lastDictionaryVisible = sourceText.isNotBlank()
        lastSmartRowMode = smartMode
''',
    '''        val sourceText = currentSuggestionSource()
        val suggestions = dictionarySuggestions(sourceText)
        val smartMode = smartRowMode(suggestions)
        lastDictionaryVisible = suggestions.isNotEmpty()
        lastSmartRowMode = smartMode
''',
    'stable top suggestion snapshot',
)
service = replace_once(
    service,
    '''            val smartContent = makeSmartRow(smartMode, dictionarySuggestions(sourceText))
''',
    '''            val smartContent = makeSmartRow(smartMode, suggestions)
''',
    'stable top content reuse',
)
service = replace_once(
    service,
    '''    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }
''',
    '''    private fun smartRowMode(suggestions: List<SuggestionItem>): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (suggestions.isNotEmpty()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }
''',
    'smart row mode by results',
)
service = replace_once(
    service,
    '''        val auxiliaryPanelOpen = morePanelExpanded || emojiPanelExpanded
        val sourceText = currentSuggestionSource()
        val mode = if (auxiliaryPanelOpen) SmartRowMode.TOOLS else smartRowMode(sourceText)
        replaceSmartRow(mode, if (auxiliaryPanelOpen) "" else sourceText)
''',
    '''        val auxiliaryPanelOpen = morePanelExpanded || emojiPanelExpanded
        val suggestions = if (auxiliaryPanelOpen) {
            emptyList()
        } else {
            dictionarySuggestions(currentSuggestionSource())
        }
        val mode = if (auxiliaryPanelOpen) SmartRowMode.TOOLS else smartRowMode(suggestions)
        replaceSmartRow(mode, suggestions)
''',
    'auxiliary panel smart row',
)
service = replace_once(
    service,
    '''    private fun replaceSmartRow(mode: SmartRowMode, sourceText: String) {
        val stack = topStack ?: return
        smartTopSlotView?.let(stack::removeView)

        val smartContent = makeSmartRow(mode, dictionarySuggestions(sourceText))
''',
    '''    private fun replaceSmartRow(mode: SmartRowMode, suggestions: List<SuggestionItem>) {
        val stack = topStack ?: return
        smartTopSlotView?.let(stack::removeView)

        val smartContent = makeSmartRow(mode, suggestions)
''',
    'replace smart row signature',
)
service = replace_once(
    service,
    '''    private fun refreshStableTopAreaOnly() {
        val sourceText = currentSuggestionSource()
        val mode = smartRowMode(sourceText)
        lastDictionaryVisible = sourceText.isNotBlank()
        replaceSmartRow(mode, sourceText)
    }
''',
    '''    private fun refreshStableTopAreaOnly() {
        val suggestions = dictionarySuggestions(currentSuggestionSource())
        val mode = smartRowMode(suggestions)
        lastDictionaryVisible = suggestions.isNotEmpty()
        replaceSmartRow(mode, suggestions)
    }
''',
    'stable top refresh',
)
service = replace_once(
    service,
    '''    private fun updateSuggestions(source: String = currentSuggestionSource()) {
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
''',
    '''    private fun updateSuggestions(source: String = currentSuggestionSource()) {
        val suggestions = dictionarySuggestions(source)
        val smartMode = smartRowMode(suggestions)
        val shouldShowDictionary = suggestions.isNotEmpty()

        if (smartMode != lastSmartRowMode) {
            replaceSmartRow(smartMode, suggestions)
            lastDictionaryVisible = shouldShowDictionary
            return
        }

        lastDictionaryVisible = shouldShowDictionary
        if (smartMode == SmartRowMode.DICTIONARY) {
            rebuildDictionaryContent(suggestions)
        }
    }
''',
    'single dictionary snapshot update',
)
SERVICE.write_text(service, encoding='utf-8')

journal = JOURNAL.read_text(encoding='utf-8')
journal = replace_once(
    journal,
    '''- `Patch 31 — Performance, Emoji Panel, Manual Lines & Adaptive Number Row` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-03.
- تدمج Patch 31 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #10 هو مصدر البناء الوحيد.
- `Patch 32 — Emoji Delete & Compact Key Rows` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.
''',
    '''- `Patch 32 — Emoji Delete & Compact Key Rows` هي Final Base الحالية والوحيدة بعد تأكيد المستخدم: `تم التطبيق بنجاح` بتاريخ 2026-08-03.
- تدمج Patch 32 جميع الباتشات السابقة المقبولة، و`main` بعد دمج PR #11 هو مصدر البناء الوحيد.
- `Patch 33 — Cold Start Dictionary Quality` مرشحة قيد الاختبار، ولا تصبح Final Base قبل اعتماد المستخدم.
''',
    'journal final base',
)
journal = replace_once(
    journal,
    '''- اعتُمدت Patch 31 كقاعدة البناء الحالية بعد نجاح تحسين الأداء ولوحة الإيموجي والأسطر اليدوية وصف الأرقام المتكيف.
- يجري الآن اختبار `Patch 32 — Emoji Delete & Compact Key Rows`.
- الفرع النشط: `agent/patch-32-emoji-delete-and-compact-rows`.
- النطاق محصور في إبقاء زر حذف ظاهر داخل لوحة الإيموجي وضغط الارتفاعات والمسافات الرأسية لصفوف الأرقام والحروف والصف السفلي في الاتجاهين.
''',
    '''- اعتُمدت Patch 32 كقاعدة البناء الحالية بعد نجاح زر الحذف داخل لوحة الإيموجي وضغط صفوف المفاتيح.
- يجري الآن اختبار `Patch 33 — Cold Start Dictionary Quality`.
- الفرع النشط: `agent/patch-33-cold-start-dictionary-quality`.
- النطاق محصور في منع الاقتراحات العامة المتكررة عند غياب الذاكرة، وإظهار شريط الأدوات عندما لا توجد نتيجة قاموس أو متابعة متعلمة حقيقية.
''',
    'journal active stage',
)
journal = replace_once(
    journal,
    '## Patch 32 — Emoji Delete & Compact Key Rows (Candidate)',
    '## Patch 32 — Emoji Delete & Compact Key Rows (Final Base)',
    'patch 32 heading',
)
journal = replace_once(
    journal,
    '''- لا تغيير في أحجام الحروف أو عرض المفاتيح أو محتوى القواميس أو التحويل أو الإيموجي المحفوظة أو العملات أو AdMob.

## ملاحظات التصميم المعتمدة
''',
    '''- لا تغيير في أحجام الحروف أو عرض المفاتيح أو محتوى القواميس أو التحويل أو الإيموجي المحفوظة أو العملات أو AdMob.
- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #11 في `main`.

## Patch 33 — Cold Start Dictionary Quality (Candidate)

- أزيل الرجوع التلقائي إلى الكلمات العامة الثابتة بعد كل مسافة عند عدم وجود ذاكرة تعلم.
- عند اكتمال كلمة بلا متابعة متعلمة حقيقية يعود شريط الأدوات بدل عرض «مرحبا، السلام، شكرا» بصورة متكررة.
- تبقى اقتراحات البادئة الحقيقية أثناء كتابة الكلمة كما هي، وتبقى المتابعة المتعلمة متاحة عند وجود سياق محفوظ فعليًا.
- أصبحت نتيجة القاموس تُحسب مرة واحدة لكل تحديث ثم تُستخدم لاتخاذ حالة الشريط ورسم الاقتراحات، لمنع العمل المكرر.
- لا تغيير في ملفات القواميس أو ترتيب كلمات البادئة أو التعلم المحلي أو الخصوصية أو التحويل أو العملات أو AdMob.

## ملاحظات التصميم المعتمدة
''',
    'patch 33 journal section',
)
JOURNAL.write_text(journal, encoding='utf-8')
