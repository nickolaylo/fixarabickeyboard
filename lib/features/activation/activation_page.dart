import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/localization/app_strings.dart';
import '../../shared/widgets/app_section_card.dart';

class ActivationPage extends StatelessWidget {
  const ActivationPage({super.key});

  static const MethodChannel _inputMethodChannel = MethodChannel('fix_arabic_keyboard/input_methods');

  Future<void> _openKeyboardSettings() async {
    await _inputMethodChannel.invokeMethod<bool>('openInputMethodSettings');
  }

  Future<void> _chooseKeyboard() async {
    await _inputMethodChannel.invokeMethod<bool>('showInputMethodPicker');
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(strings.activation)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(strings.activationHint, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 20),
          AppSectionCard(
            icon: Icons.keyboard_alt_outlined,
            title: strings.activationStepOneTitle,
            body: strings.activationStepOneBody,
            trailing: const Icon(Icons.settings_outlined),
            onTap: _openKeyboardSettings,
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _openKeyboardSettings,
            child: Text(strings.openKeyboardSettings),
          ),
          const SizedBox(height: 20),
          AppSectionCard(
            icon: Icons.keyboard_command_key_outlined,
            title: strings.activationStepTwoTitle,
            body: strings.activationStepTwoBody,
            trailing: const Icon(Icons.keyboard_arrow_down_outlined),
            onTap: _chooseKeyboard,
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _chooseKeyboard,
            child: Text(strings.chooseKeyboard),
          ),
          const SizedBox(height: 20),
          AppSectionCard(
            icon: Icons.edit_note_outlined,
            title: strings.keyboardTestTitle,
            body: strings.keyboardTestBody,
            trailing: const Icon(Icons.keyboard_outlined),
            onTap: _chooseKeyboard,
          ),
          const SizedBox(height: 12),
          TextField(
            minLines: 3,
            maxLines: 5,
            textAlign: strings.isArabic ? TextAlign.right : TextAlign.left,
            decoration: InputDecoration(
              hintText: strings.keyboardTestHint,
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _chooseKeyboard,
            icon: const Icon(Icons.keyboard_alt_outlined),
            label: Text(strings.chooseKeyboardForTest),
          ),
        ],
      ),
    );
  }
}
