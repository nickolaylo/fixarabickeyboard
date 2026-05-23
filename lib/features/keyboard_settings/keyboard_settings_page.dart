import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';
import '../../core/settings/settings_store.dart';

class KeyboardSettingsPage extends StatefulWidget {
  const KeyboardSettingsPage({super.key});

  @override
  State<KeyboardSettingsPage> createState() => _KeyboardSettingsPageState();
}

class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  final store = SettingsStore.instance;

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(strings.settings)),
      body: ListView(
        children: [
          SwitchListTile(
            value: store.correctionEnabled,
            title: const Text('Correction'),
            onChanged: (value) => setState(() => store.correctionEnabled = value),
          ),
          SwitchListTile(
            value: store.hapticEnabled,
            title: const Text('Haptic feedback'),
            onChanged: (value) => setState(() => store.hapticEnabled = value),
          ),
        ],
      ),
    );
  }
}
