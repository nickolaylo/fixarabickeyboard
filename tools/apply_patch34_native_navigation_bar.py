from pathlib import Path

IME_PATH = Path('android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt')
JOURNAL_PATH = Path('PROJECT_JOURNAL.md')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one match, found {count}')
    return text.replace(old, new, 1)


ime = IME_PATH.read_text(encoding='utf-8')
ime = replace_once(
    ime,
    'import android.view.WindowInsets\n',
    'import android.view.WindowInsets\nimport android.view.WindowInsetsController\n',
    'add WindowInsetsController import',
)
ime = replace_once(
    ime,
    '''        toolsExpanded = prefs.getBoolean("tools_expanded", true)\n        repairExpanded = prefs.getBoolean("repair_expanded", false)\n    }\n\n    override fun onCreateInputView(): View {\n''',
    '''        toolsExpanded = prefs.getBoolean("tools_expanded", true)\n        repairExpanded = prefs.getBoolean("repair_expanded", false)\n        applyNativeNavigationBarAppearance()\n    }\n\n    override fun onWindowShown() {\n        super.onWindowShown()\n        applyNativeNavigationBarAppearance()\n        keyboardRoot?.post { keyboardRoot?.requestApplyInsets() }\n    }\n\n    override fun onConfigurationChanged(newConfig: Configuration) {\n        super.onConfigurationChanged(newConfig)\n        applyNativeNavigationBarAppearance()\n    }\n\n    private fun applyNativeNavigationBarAppearance() {\n        val imeWindow = window?.window ?: return\n        imeWindow.navigationBarColor = KeyboardColors.background\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {\n            imeWindow.navigationBarDividerColor = KeyboardColors.background\n        }\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            imeWindow.isNavigationBarContrastEnforced = false\n        }\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            imeWindow.insetsController?.setSystemBarsAppearance(\n                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,\n                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS\n            )\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {\n            @Suppress("DEPRECATION")\n            imeWindow.decorView.systemUiVisibility =\n                imeWindow.decorView.systemUiVisibility or\n                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR\n        }\n    }\n\n    override fun onCreateInputView(): View {\n''',
    'add native navigation bar styling',
)
ime = replace_once(
    ime,
    '''        root.post { root.requestApplyInsets() }\n\n        updateSuggestions()\n''',
    '''        root.post {\n            applyNativeNavigationBarAppearance()\n            root.requestApplyInsets()\n        }\n\n        updateSuggestions()\n''',
    'style native bar when input view is attached',
)
ime = replace_once(
    ime,
    '''    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {\n        super.onStartInputView(info, restarting)\n        val currentMode = currentNumberRowMode()\n''',
    '''    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {\n        super.onStartInputView(info, restarting)\n        applyNativeNavigationBarAppearance()\n        val currentMode = currentNumberRowMode()\n''',
    'refresh native bar for every input session',
)
IME_PATH.write_text(ime, encoding='utf-8', newline='\n')

journal = JOURNAL_PATH.read_text(encoding='utf-8')
journal = replace_once(
    journal,
    '''- أضيف تصحيح لمساحة النظام السفلية في الهاتف والتابلت: تستعمل اللوحة `WindowInsets` لحجز مساحة شريط الإيماءات أو أزرار التنقل الأصلية أسفل المفاتيح، ثم تضيف فوقها المسافة الاختيارية للمستخدم دون تداخل أو مضاعفة.\n- لا تغيير في القواميس أو التعلم المحلي أو التحويل أو الإيموجي أو العملات أو AdMob.\n''',
    '''- أضيف تصحيح لمساحة النظام السفلية في الهاتف والتابلت: تستعمل اللوحة `WindowInsets` لحجز مساحة شريط الإيماءات أو أزرار التنقل الأصلية أسفل المفاتيح، ثم تضيف فوقها المسافة الاختيارية للمستخدم دون تداخل أو مضاعفة.\n- يُلوَّن شريط تنقل Android الأصلي بلون خلفية اللوحة مع رموز داكنة، ويُعطل تباين Android الأسود الإجباري في الإصدارات المدعومة. لا تنشئ اللوحة أي شريط نظام بديل، ولا ترسم أزرار الإخفاء أو تبديل طريقة الإدخال بنفسها.\n- لا تغيير في القواميس أو التعلم المحلي أو التحويل أو الإيموجي أو العملات أو AdMob.\n''',
    'document native navigation bar policy',
)
JOURNAL_PATH.write_text(journal, encoding='utf-8', newline='\n')

# Trigger the temporary validation workflow after it is present on main.
