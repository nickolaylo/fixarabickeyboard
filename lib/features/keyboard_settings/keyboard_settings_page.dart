import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';
import '../../core/settings/settings_store.dart';
import '../../shared/widgets/keyboard_preview_card.dart';

class KeyboardSettingsPage extends StatefulWidget {
  const KeyboardSettingsPage({
    super.key,
    this.embedded = false,
  });

  final bool embedded;

  @override
  State<KeyboardSettingsPage> createState() => _KeyboardSettingsPageState();
}

class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  final store = SettingsStore.instance;

  @override
  void initState() {
    super.initState();
    Future.wait<void>([
      store.loadNumberRowMode(),
      store.loadKeyboardAppearance(),
    ]).then((_) {
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

  Future<void> _saveAppearance() {
    return store.setKeyboardAppearance(
      keyboardHeightPercent: store.keyboardHeightPercent,
      letterSizePercent: store.letterSizePercent,
      bottomSpacingDp: store.bottomSpacingDp,
      keyBordersEnabled: store.keyBordersEnabled,
    );
  }

  Future<void> _setKeyBorders(bool value) async {
    setState(() => store.keyBordersEnabled = value);
    await _saveAppearance();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    final content = ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        KeyboardPreviewCard(
          title: strings.previewTitle,
          subtitle: strings.previewBody,
        ),
        const SizedBox(height: 22),
        _SectionTitle(strings.typingSection),
        const SizedBox(height: 8),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              SwitchListTile.adaptive(
                value: store.correctionEnabled,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 5,
                ),
                secondary: const Icon(Icons.auto_fix_high_outlined),
                title: Text(strings.correctionSetting),
                subtitle: Text(strings.correctionSettingBody),
                onChanged: (value) {
                  setState(() => store.correctionEnabled = value);
                },
              ),
              const Divider(),
              ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 5,
                ),
                leading: const Icon(Icons.format_list_numbered_rounded),
                title: Text(strings.numberRowSetting),
                subtitle: Text(
                  '${strings.numberRowSettingBody}\n'
                  '${_numberRowModeLabel(strings, store.numberRowMode)}',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => _chooseNumberRowMode(strings),
              ),
              const Divider(),
              SwitchListTile.adaptive(
                value: store.hapticEnabled,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 5,
                ),
                secondary: const Icon(Icons.vibration_rounded),
                title: Text(strings.hapticSetting),
                subtitle: Text(strings.hapticSettingBody),
                onChanged: (value) {
                  setState(() => store.hapticEnabled = value);
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        _SectionTitle(strings.appearanceSection),
        const SizedBox(height: 8),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              _SliderSettingTile(
                icon: Icons.height_rounded,
                title: strings.keyboardHeightSetting,
                subtitle: strings.keyboardHeightSettingBody,
                valueLabel: '${store.keyboardHeightPercent}%',
                value: store.keyboardHeightPercent.toDouble(),
                min: SettingsStore.minKeyboardHeightPercent.toDouble(),
                max: SettingsStore.maxKeyboardHeightPercent.toDouble(),
                divisions: 6,
                onChanged: (value) {
                  setState(() => store.keyboardHeightPercent = value.round());
                },
                onChangeEnd: (_) => _saveAppearance(),
              ),
              const Divider(),
              _SliderSettingTile(
                icon: Icons.text_fields_rounded,
                title: strings.letterSizeSetting,
                subtitle: strings.letterSizeSettingBody,
                valueLabel: '${store.letterSizePercent}%',
                value: store.letterSizePercent.toDouble(),
                min: SettingsStore.minLetterSizePercent.toDouble(),
                max: SettingsStore.maxLetterSizePercent.toDouble(),
                divisions: 7,
                onChanged: (value) {
                  setState(() => store.letterSizePercent = value.round());
                },
                onChangeEnd: (_) => _saveAppearance(),
              ),
              const Divider(),
              _SliderSettingTile(
                icon: Icons.space_bar_rounded,
                title: strings.bottomSpacingSetting,
                subtitle: strings.bottomSpacingSettingBody,
                valueLabel: '${store.bottomSpacingDp} dp',
                value: store.bottomSpacingDp.toDouble(),
                min: SettingsStore.minBottomSpacingDp.toDouble(),
                max: SettingsStore.maxBottomSpacingDp.toDouble(),
                divisions: 6,
                onChanged: (value) {
                  setState(() => store.bottomSpacingDp = value.round());
                },
                onChangeEnd: (_) => _saveAppearance(),
              ),
              const Divider(),
              SwitchListTile.adaptive(
                value: store.keyBordersEnabled,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 5,
                ),
                secondary: const Icon(Icons.border_style_rounded),
                title: Text(strings.keyBordersSetting),
                subtitle: Text(strings.keyBordersSettingBody),
                onChanged: _setKeyBorders,
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        _SectionTitle(strings.accessibilitySection),
        const SizedBox(height: 8),
        Card(
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 8,
            ),
            leading: const Icon(Icons.swipe_outlined),
            title: Text(strings.gestureRoadmapTitle),
            subtitle: Text(strings.gestureRoadmapBody),
            trailing: const Icon(Icons.science_outlined),
          ),
        ),
      ],
    );

    if (widget.embedded) return content;

    return Scaffold(
      appBar: AppBar(title: Text(strings.settings)),
      body: content,
    );
  }
}

class _SliderSettingTile extends StatelessWidget {
  const _SliderSettingTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.valueLabel,
    required this.value,
    required this.min,
    required this.max,
    required this.divisions,
    required this.onChanged,
    required this.onChangeEnd,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String valueLabel;
  final double value;
  final double min;
  final double max;
  final int divisions;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onChangeEnd;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsetsDirectional.only(top: 3),
                child: Icon(icon),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: textTheme.bodyLarge),
                    const SizedBox(height: 2),
                    Text(subtitle, style: textTheme.bodyMedium),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Text(
                valueLabel,
                style: textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
          Slider(
            value: value.clamp(min, max).toDouble(),
            min: min,
            max: max,
            divisions: divisions,
            label: valueLabel,
            semanticFormatterCallback: (_) => '$title، $valueLabel',
            onChanged: onChanged,
            onChangeEnd: onChangeEnd,
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.only(start: 4),
      child: Text(
        label,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.2,
            ),
      ),
    );
  }
}
