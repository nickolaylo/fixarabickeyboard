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
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 8,
            ),
            leading: const Icon(Icons.palette_outlined),
            title: Text(strings.appearanceRoadmapTitle),
            subtitle: Text(strings.appearanceRoadmapBody),
            trailing: const Icon(Icons.lock_clock_outlined),
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
