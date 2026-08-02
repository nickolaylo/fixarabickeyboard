from pathlib import Path

ROOT = Path('.')
KOTLIN = ROOT / 'android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt'
ACTIVITY = ROOT / 'android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/MainActivity.kt'
STORE = ROOT / 'lib/core/settings/settings_store.dart'
SETTINGS_PAGE = ROOT / 'lib/features/keyboard_settings/keyboard_settings_page.dart'
STRINGS = ROOT / 'lib/core/localization/app_strings.dart'
JOURNAL = ROOT / 'PROJECT_JOURNAL.md'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


# Flutter-side persistent setting bridge.
STORE.write_text("""import 'package:flutter/services.dart';

enum NumberRowMode {
  always('always'),
  portraitOnly('portrait_only'),
  hidden('hidden');

  const NumberRowMode(this.platformValue);

  final String platformValue;

  static NumberRowMode fromPlatform(String? value) {
    return NumberRowMode.values.firstWhere(
      (mode) => mode.platformValue == value,
      orElse: () => NumberRowMode.portraitOnly,
    );
  }
}

class SettingsStore {
  SettingsStore._();

  static final SettingsStore instance = SettingsStore._();
  static const MethodChannel _channel = MethodChannel(
    'fix_arabic_keyboard/input_methods',
  );

  bool correctionEnabled = true;
  bool hapticEnabled = true;
  NumberRowMode numberRowMode = NumberRowMode.portraitOnly;

  Future<void> loadNumberRowMode() async {
    try {
      final value = await _channel.invokeMethod<String>('getNumberRowMode');
      numberRowMode = NumberRowMode.fromPlatform(value);
    } on MissingPluginException {
      numberRowMode = NumberRowMode.portraitOnly;
    } on PlatformException {
      numberRowMode = NumberRowMode.portraitOnly;
    }
  }

  Future<void> setNumberRowMode(NumberRowMode mode) async {
    numberRowMode = mode;
    try {
      await _channel.invokeMethod<void>(
        'setNumberRowMode',
        <String, String>{'mode': mode.platformValue},
      );
    } on MissingPluginException {
      // Widget tests and unsupported platforms keep the in-memory value.
    } on PlatformException {
      // Keep the selected value in memory; Android will retry on the next change.
    }
  }
}
""", encoding='utf-8')

# Android bridge writes into the same SharedPreferences file used by the IME.
activity = ACTIVITY.read_text(encoding='utf-8')
activity = replace_once(
    activity,
    '''                "showInputMethodPicker" -> {
                    showInputMethodPicker()
                    result.success(true)
                }
                else -> result.notImplemented()''',
    '''                "showInputMethodPicker" -> {
                    showInputMethodPicker()
                    result.success(true)
                }
                "getNumberRowMode" -> {
                    result.success(
                        getSharedPreferences(KEYBOARD_UI_PREFS, Context.MODE_PRIVATE)
                            .getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
                            ?: NUMBER_ROW_PORTRAIT_ONLY
                    )
                }
                "setNumberRowMode" -> {
                    val mode = call.argument<String>("mode")
                    if (mode !in NUMBER_ROW_MODES) {
                        result.error("invalid_number_row_mode", "Unsupported number-row mode", null)
                    } else {
                        getSharedPreferences(KEYBOARD_UI_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(NUMBER_ROW_MODE_KEY, mode)
                            .apply()
                        result.success(true)
                    }
                }
                else -> result.notImplemented()''',
    'MainActivity methods',
)
activity = replace_once(
    activity,
    '''    companion object {
        private const val CHANNEL = "fix_arabic_keyboard/input_methods"
    }''',
    '''    companion object {
        private const val CHANNEL = "fix_arabic_keyboard/input_methods"
        private const val KEYBOARD_UI_PREFS = "keyboard_ui_state"
        private const val NUMBER_ROW_MODE_KEY = "number_row_mode"
        private const val NUMBER_ROW_ALWAYS = "always"
        private const val NUMBER_ROW_PORTRAIT_ONLY = "portrait_only"
        private const val NUMBER_ROW_HIDDEN = "hidden"
        private val NUMBER_ROW_MODES = setOf(
            NUMBER_ROW_ALWAYS,
            NUMBER_ROW_PORTRAIT_ONLY,
            NUMBER_ROW_HIDDEN
        )
    }''',
    'MainActivity constants',
)
ACTIVITY.write_text(activity, encoding='utf-8')

# Localized labels for the real setting.
strings = STRINGS.read_text(encoding='utf-8')
strings = replace_once(
    strings,
    "  String get typingSection => pick(en: 'Typing', ar: 'الكتابة', fr: 'Saisie');\n",
    """  String get typingSection => pick(en: 'Typing', ar: 'الكتابة', fr: 'Saisie');
  String get numberRowSetting => pick(
        en: 'Number row',
        ar: 'صف الأرقام',
        fr: 'Rangée de chiffres',
      );
  String get numberRowSettingBody => pick(
        en: 'Choose when the number row appears above the letters.',
        ar: 'اختر متى يظهر صف الأرقام فوق الحروف.',
        fr: 'Choisissez quand la rangée de chiffres apparaît au-dessus des lettres.',
      );
  String get numberRowAlways => pick(
        en: 'Always',
        ar: 'دائمًا',
        fr: 'Toujours',
      );
  String get numberRowPortraitOnly => pick(
        en: 'Portrait only',
        ar: 'في الوضع العمودي فقط',
        fr: 'En mode portrait uniquement',
      );
  String get numberRowHidden => pick(
        en: 'Hidden',
        ar: 'مخفي',
        fr: 'Masquée',
      );
""",
    'localized number-row strings',
)
STRINGS.write_text(strings, encoding='utf-8')

# Settings UI: load the persisted mode and expose the three choices.
page = SETTINGS_PAGE.read_text(encoding='utf-8')
page = replace_once(
    page,
    '''class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  final store = SettingsStore.instance;

  @override
  Widget build(BuildContext context) {''',
    '''class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  final store = SettingsStore.instance;

  @override
  void initState() {
    super.initState();
    store.loadNumberRowMode().then((_) {
      if (mounted) setState(() {});
    });
  }

  String _numberRowModeLabel(AppStrings strings, NumberRowMode mode) {
    return switch (mode) {
      NumberRowMode.always => strings.numberRowAlways,
      NumberRowMode.portraitOnly => strings.numberRowPortraitOnly,
      NumberRowMode.hidden => strings.numberRowHidden,
    };
  }

  Future<void> _chooseNumberRowMode(AppStrings strings) async {
    final selected = await showModalBottomSheet<NumberRowMode>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.format_list_numbered_rounded),
                title: Text(
                  strings.numberRowSetting,
                  style: Theme.of(sheetContext).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
                subtitle: Text(strings.numberRowSettingBody),
              ),
              for (final mode in NumberRowMode.values)
                ListTile(
                  leading: Icon(
                    mode == store.numberRowMode
                        ? Icons.radio_button_checked_rounded
                        : Icons.radio_button_unchecked_rounded,
                  ),
                  title: Text(_numberRowModeLabel(strings, mode)),
                  onTap: () => Navigator.of(sheetContext).pop(mode),
                ),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );

    if (selected == null || selected == store.numberRowMode) return;
    await store.setNumberRowMode(selected);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {''',
    'settings-page state helpers',
)
page = replace_once(
    page,
    '''              const Divider(),
              SwitchListTile.adaptive(
                value: store.hapticEnabled,''',
    '''              const Divider(),
              ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 5,
                ),
                leading: const Icon(Icons.format_list_numbered_rounded),
                title: Text(strings.numberRowSetting),
                subtitle: Text(
                  '${strings.numberRowSettingBody}\n${_numberRowModeLabel(strings, store.numberRowMode)}',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => _chooseNumberRowMode(strings),
              ),
              const Divider(),
              SwitchListTile.adaptive(
                value: store.hapticEnabled,''',
    'number-row settings tile',
)
SETTINGS_PAGE.write_text(page, encoding='utf-8')

# Native keyboard: orientation-aware visibility and compact dimensions.
kotlin = KOTLIN.read_text(encoding='utf-8')
kotlin = replace_once(
    kotlin,
    'import android.content.Intent\n',
    'import android.content.Intent\nimport android.content.res.Configuration\n',
    'Configuration import',
)
kotlin = replace_once(
    kotlin,
    '''        const val SUGGESTION_CONTEXT_LIMIT = 160
        const val KEY_AREA_HEIGHT_DP = 264
        const val RECENT_EMOJIS_KEY = "recent_emojis"''',
    '''        const val SUGGESTION_CONTEXT_LIMIT = 160
        const val NUMBER_ROW_MODE_KEY = "number_row_mode"
        const val NUMBER_ROW_ALWAYS = "always"
        const val NUMBER_ROW_PORTRAIT_ONLY = "portrait_only"
        const val NUMBER_ROW_HIDDEN = "hidden"
        const val RECENT_EMOJIS_KEY = "recent_emojis"''',
    'IME number-row constants',
)
kotlin = replace_once(
    kotlin,
    '                dp(KEY_AREA_HEIGHT_DP)\n',
    '                dp(currentKeyAreaHeightDp())\n',
    'adaptive key-area height',
)
kotlin = replace_once(
    kotlin,
    '''    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }

    private fun makeKeyboardKeyArea(): LinearLayout {''',
    '''    private fun smartRowMode(sourceText: String): SmartRowMode {
        if (morePanelExpanded || emojiPanelExpanded) return SmartRowMode.TOOLS
        return if (sourceText.isNotBlank()) SmartRowMode.DICTIONARY else SmartRowMode.TOOLS
    }

    private fun isLandscapeKeyboard(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun shouldShowNumberRow(): Boolean {
        return when (
            prefs.getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
                ?: NUMBER_ROW_PORTRAIT_ONLY
        ) {
            NUMBER_ROW_ALWAYS -> true
            NUMBER_ROW_HIDDEN -> false
            else -> !isLandscapeKeyboard()
        }
    }

    private fun currentKeyAreaHeightDp(): Int {
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
    private fun bottomRowTopPadding(): Int = dp(if (isLandscapeKeyboard()) 2 else 4)

    private fun makeKeyboardKeyArea(): LinearLayout {''',
    'IME number-row helpers',
)
kotlin = replace_once(
    kotlin,
    '''        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(makeNumberRow())
            activeRows().forEachIndexed { index, row ->''',
    '''        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            if (shouldShowNumberRow()) addView(makeNumberRow())
            activeRows().forEachIndexed { index, row ->''',
    'conditional number row',
)
kotlin = replace_once(
    kotlin,
    '''            setPadding(0, dp(2), 0, dp(3))
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { key ->
                addView(makeActionKey(key, KeyboardColors.key, 19f) { handleKey(key) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })''',
    '''            setPadding(0, rowTopPadding(), 0, rowBottomPadding())
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { key ->
                addView(makeActionKey(key, KeyboardColors.key, 19f) { handleKey(key) }, LinearLayout.LayoutParams(0, numberKeyHeight(), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })''',
    'compact number row',
)
kotlin = replace_once(
    kotlin,
    '''            setPadding(0, dp(2), 0, dp(3))
            if (showBackspace && isLatinMode()) {
                addView(makeShiftKey(), LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            keys.forEach { key ->
                val label = displayLetter(key)
                addView(makeLetterKey(label) { handleKey(label) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            if (showBackspace) {
                addView(makeBackspaceKey(), LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })''',
    '''            setPadding(0, rowTopPadding(), 0, rowBottomPadding())
            if (showBackspace && isLatinMode()) {
                addView(makeShiftKey(), LinearLayout.LayoutParams(0, functionKeyHeight(), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            keys.forEach { key ->
                val label = displayLetter(key)
                addView(makeLetterKey(label) { handleKey(label) }, LinearLayout.LayoutParams(0, letterKeyHeight(), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            if (showBackspace) {
                addView(makeBackspaceKey(), LinearLayout.LayoutParams(0, functionKeyHeight(), 1.12f).apply { setMargins(dp(2), 0, dp(2), 0) })''',
    'compact letter rows',
)
kotlin = replace_once(
    kotlin,
    '            setPadding(0, dp(4), 0, 0)\n',
    '            setPadding(0, bottomRowTopPadding(), 0, 0)\n',
    'compact bottom padding',
)
space_pattern = 'LinearLayout.LayoutParams(0, dp(52), 1f)'
if kotlin.count(space_pattern) != 3:
    raise RuntimeError(f'space-key heights: expected 3 matches, found {kotlin.count(space_pattern)}')
kotlin = kotlin.replace(space_pattern, 'LinearLayout.LayoutParams(0, bottomKeyHeight(), 1f)')
kotlin = replace_once(
    kotlin,
    '''    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) }''',
    '''    private fun bottomParams(width: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, bottomKeyHeight()).apply { setMargins(dp(2), 0, dp(2), 0) }''',
    'dynamic bottom key height',
)
KOTLIN.write_text(kotlin, encoding='utf-8')

# Document the approved scope inside the existing candidate patch.
journal = JOURNAL.read_text(encoding='utf-8')
journal = replace_once(
    journal,
    '''- أصبح زر السطر الجديد قبل عصا التنفيذ في ترتيب الصف السفلي أثناء وضع الإصلاح.
- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.''',
    '''- أصبح زر السطر الجديد قبل عصا التنفيذ في ترتيب الصف السفلي أثناء وضع الإصلاح.
- أضيف إعداد فعلي لصف الأرقام بثلاث حالات: دائمًا، في الوضع العمودي فقط، أو مخفي.
- القيمة الافتراضية هي «في الوضع العمودي فقط»، ويُحفظ الاختيار محليًا في ملف التفضيلات نفسه الذي تقرؤه خدمة لوحة المفاتيح.
- يتكيف ارتفاع منطقة المفاتيح وارتفاع الصفوف مع الاتجاه وظهور صف الأرقام، لتصبح اللوحة الأفقية أقرب إلى ارتفاع لوحات النظام دون ترك فراغ لصف مخفي.
- لا تغيير في محتوى القواميس أو ترتيبها أو العملات أو AdMob أو نظام المكافآت أو خوارزمية التحويل.''',
    'journal number-row scope',
)
JOURNAL.write_text(journal, encoding='utf-8')

print('Patch 31 adaptive number-row setting applied')
