from pathlib import Path

# Patch 34 runtime native-navigation correction.
IME = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt')
JOURNAL = Path('PROJECT_JOURNAL.md')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one match, found {count}')
    return text.replace(old, new, 1)


ime = IME.read_text(encoding='utf-8')
ime = replace_once(
    ime,
    'import android.view.WindowInsets\n',
    'import android.view.WindowInsets\nimport android.view.WindowInsetsController\nimport android.view.WindowManager\n',
    'navigation imports',
)
ime = replace_once(
    ime,
    '''        toolsExpanded = prefs.getBoolean("tools_expanded", true)\n        repairExpanded = prefs.getBoolean("repair_expanded", false)\n    }\n\n    override fun onCreateInputView(): View {\n''',
    '''        toolsExpanded = prefs.getBoolean("tools_expanded", true)\n        repairExpanded = prefs.getBoolean("repair_expanded", false)\n        applyNativeNavigationBarAppearance()\n    }\n\n    override fun onWindowShown() {\n        super.onWindowShown()\n        applyNativeNavigationBarAppearance()\n        keyboardRoot?.post { keyboardRoot?.requestApplyInsets() }\n    }\n\n    override fun onConfigurationChanged(newConfig: Configuration) {\n        super.onConfigurationChanged(newConfig)\n        applyNativeNavigationBarAppearance()\n    }\n\n    private fun applyNativeNavigationBarAppearance() {\n        val imeWindow = window?.window ?: return\n        imeWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)\n        imeWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)\n        imeWindow.navigationBarColor = KeyboardColors.background\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {\n            imeWindow.navigationBarDividerColor = KeyboardColors.background\n        }\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            imeWindow.isNavigationBarContrastEnforced = false\n        }\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            imeWindow.insetsController?.setSystemBarsAppearance(\n                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,\n                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS\n            )\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {\n            @Suppress("DEPRECATION")\n            imeWindow.decorView.systemUiVisibility =\n                imeWindow.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR\n        }\n    }\n\n    override fun onCreateInputView(): View {\n''',
    'runtime navigation appearance method',
)
ime = replace_once(
    ime,
    '''        root.post { root.requestApplyInsets() }\n\n        updateSuggestions()\n''',
    '''        root.post {\n            applyNativeNavigationBarAppearance()\n            root.requestApplyInsets()\n        }\n\n        updateSuggestions()\n''',
    'runtime appearance after view attach',
)
ime = replace_once(
    ime,
    '''    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {\n        super.onStartInputView(info, restarting)\n        val currentMode = currentNumberRowMode()\n''',
    '''    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {\n        super.onStartInputView(info, restarting)\n        applyNativeNavigationBarAppearance()\n        val currentMode = currentNumberRowMode()\n''',
    'runtime appearance each input session',
)
IME.write_text(ime, encoding='utf-8', newline='\n')

journal = JOURNAL.read_text(encoding='utf-8')
anchor = '- تُحجز مساحة تنقل Android السفلية تلقائيًا تحت المفاتيح، ثم تضاف فوقها المسافة الاختيارية التي يحددها المستخدم؛ فلا تتداخل أيقونة إخفاء اللوحة أو تبديل طريقة الإدخال أو شريط الإيماءات مع الصف السفلي على الهاتف والتابلت.\n'
addition = '- بعد أن ثبت أن ثيم الـIME وحده لا يغيّر لون شريط النظام على بعض الأجهزة/المحاكيات، تضبط خدمة الإدخال نافذة Android الأصلية وقت التشغيل أيضًا: لون شريط التنقل وخطه الفاصل يطابقان خلفية اللوحة، تُستخدم رموز داكنة، ويُعطل تباين شريط التنقل الإجباري عند دعمه. لا تُنشأ أي View أو أزرار نظام بديلة داخل اللوحة.\n'
if addition not in journal:
    if anchor not in journal:
        raise RuntimeError('journal navigation anchor not found')
    journal = journal.replace(anchor, anchor + addition, 1)
JOURNAL.write_text(journal, encoding='utf-8', newline='\n')
