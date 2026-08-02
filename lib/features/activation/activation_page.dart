import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/localization/app_strings.dart';
import '../../shared/widgets/app_section_card.dart';

class ActivationPage extends StatelessWidget {
  const ActivationPage({super.key});

  static const MethodChannel _inputMethodChannel = MethodChannel(
    'fix_arabic_keyboard/input_methods',
  );

  Future<void> _openKeyboardSettings() async {
    await _inputMethodChannel.invokeMethod<bool>('openInputMethodSettings');
  }

  Future<void> _chooseKeyboard() async {
    await _inputMethodChannel.invokeMethod<bool>('showInputMethodPicker');
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(title: Text(strings.activation)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: colors.primaryContainer,
              borderRadius: BorderRadius.circular(28),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: colors.surface.withValues(alpha: 0.78),
                    borderRadius: BorderRadius.circular(17),
                  ),
                  alignment: Alignment.center,
                  child: Icon(
                    Icons.keyboard_alt_rounded,
                    color: colors.primary,
                    size: 29,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        strings.openActivation,
                        style: theme.textTheme.titleLarge?.copyWith(
                          color: colors.onPrimaryContainer,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        strings.activationHint,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: colors.onPrimaryContainer,
                          height: 1.4,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          AppSectionCard(
            icon: Icons.shield_outlined,
            title: strings.activationPrivacyTitle,
            body: strings.activationPrivacyBody,
            accent: colors.tertiary,
          ),
          const SizedBox(height: 20),
          _ActivationStepCard(
            number: 1,
            icon: Icons.tune_rounded,
            title: strings.activationStepOneTitle,
            body: strings.activationStepOneBody,
            actionLabel: strings.openKeyboardSettings,
            onPressed: _openKeyboardSettings,
            primary: true,
          ),
          const SizedBox(height: 12),
          _ActivationStepCard(
            number: 2,
            icon: Icons.keyboard_command_key_rounded,
            title: strings.activationStepTwoTitle,
            body: strings.activationStepTwoBody,
            actionLabel: strings.chooseKeyboard,
            onPressed: _chooseKeyboard,
          ),
          const SizedBox(height: 12),
          _ActivationStepCard(
            number: 3,
            icon: Icons.edit_note_rounded,
            title: strings.activationStepThreeTitle,
            body: strings.activationStepThreeBody,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  minLines: 3,
                  maxLines: 5,
                  textAlign: strings.isArabic ? TextAlign.right : TextAlign.left,
                  decoration: InputDecoration(
                    hintText: strings.keyboardTestHint,
                    prefixIcon: const Icon(Icons.keyboard_outlined),
                  ),
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: _chooseKeyboard,
                  icon: const Icon(Icons.keyboard_alt_outlined),
                  label: Text(strings.chooseKeyboardForTest),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ActivationStepCard extends StatelessWidget {
  const _ActivationStepCard({
    required this.number,
    required this.icon,
    required this.title,
    required this.body,
    this.actionLabel,
    this.onPressed,
    this.primary = false,
    this.child,
  });

  final int number;
  final IconData icon;
  final String title;
  final String body;
  final String? actionLabel;
  final VoidCallback? onPressed;
  final bool primary;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: colors.primaryContainer,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      alignment: Alignment.center,
                      child: Icon(icon, color: colors.onPrimaryContainer),
                    ),
                    PositionedDirectional(
                      top: -7,
                      start: -7,
                      child: Container(
                        width: 22,
                        height: 22,
                        decoration: BoxDecoration(
                          color: colors.primary,
                          shape: BoxShape.circle,
                          border: Border.all(color: theme.cardColor, width: 2),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '$number',
                          style: TextStyle(
                            color: colors.onPrimary,
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        body,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: colors.onSurfaceVariant,
                          height: 1.4,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (actionLabel != null && onPressed != null) ...[
              const SizedBox(height: 16),
              if (primary)
                FilledButton.icon(
                  onPressed: onPressed,
                  icon: const Icon(Icons.open_in_new_rounded),
                  label: Text(actionLabel!),
                )
              else
                OutlinedButton.icon(
                  onPressed: onPressed,
                  icon: const Icon(Icons.keyboard_arrow_down_rounded),
                  label: Text(actionLabel!),
                ),
            ],
            if (child != null) ...[
              const SizedBox(height: 16),
              child!,
            ],
          ],
        ),
      ),
    );
  }
}
