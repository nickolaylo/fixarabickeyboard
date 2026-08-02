import 'package:flutter/material.dart';

class KeyboardPreviewCard extends StatelessWidget {
  const KeyboardPreviewCard({
    super.key,
    required this.title,
    required this.subtitle,
  });

  final String title;
  final String subtitle;

  static const _rows = [
    ['ض', 'ص', 'ث', 'ق', 'ف', 'غ', 'ع', 'ه', 'خ', 'ح'],
    ['ش', 'س', 'ي', 'ب', 'ل', 'ا', 'ت', 'ن', 'م', 'ك'],
    ['ئ', 'ء', 'ؤ', 'ر', 'ى', 'ة', 'و', 'ز', 'ظ'],
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    color: colors.primaryContainer,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  alignment: Alignment.center,
                  child: Icon(
                    Icons.keyboard_alt_outlined,
                    color: colors.onPrimaryContainer,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        subtitle,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: colors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 10),
              decoration: BoxDecoration(
                color: colors.surfaceContainerHighest.withValues(alpha: 0.72),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: colors.outlineVariant),
              ),
              child: Column(
                children: [
                  Container(
                    height: 38,
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    decoration: BoxDecoration(
                      color: colors.surface,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.chevron_left, size: 20, color: colors.onSurfaceVariant),
                        const Spacer(),
                        Icon(Icons.content_paste_outlined, size: 19, color: colors.onSurfaceVariant),
                        const SizedBox(width: 18),
                        Icon(Icons.emoji_emotions_outlined, size: 19, color: colors.onSurfaceVariant),
                        const SizedBox(width: 18),
                        Icon(Icons.settings_outlined, size: 19, color: colors.onSurfaceVariant),
                        const Spacer(),
                        Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: colors.primaryContainer,
                            shape: BoxShape.circle,
                          ),
                          alignment: Alignment.center,
                          child: Icon(
                            Icons.auto_fix_high_rounded,
                            size: 18,
                            color: colors.onPrimaryContainer,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 7),
                  for (final row in _rows) ...[
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        for (final label in row)
                          Expanded(
                            child: _PreviewKey(label: label),
                          ),
                      ],
                    ),
                    const SizedBox(height: 5),
                  ],
                  Row(
                    children: [
                      const Expanded(flex: 2, child: _PreviewKey(icon: Icons.language_rounded)),
                      const Expanded(flex: 6, child: _PreviewKey(label: 'العربية')),
                      Expanded(
                        flex: 2,
                        child: _PreviewKey(
                          icon: Icons.keyboard_return_rounded,
                          emphasisColor: colors.primary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PreviewKey extends StatelessWidget {
  const _PreviewKey({
    this.label,
    this.icon,
    this.emphasisColor,
  }) : assert(label != null || icon != null);

  final String? label;
  final IconData? icon;
  final Color? emphasisColor;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final emphasized = emphasisColor != null;

    return Container(
      height: 33,
      margin: const EdgeInsets.symmetric(horizontal: 2),
      decoration: BoxDecoration(
        color: emphasized ? emphasisColor : colors.surface,
        borderRadius: BorderRadius.circular(9),
        border: emphasized ? null : Border.all(color: colors.outlineVariant.withValues(alpha: 0.75)),
      ),
      alignment: Alignment.center,
      child: icon != null
          ? Icon(icon, size: 17, color: emphasized ? colors.onPrimary : colors.onSurface)
          : FittedBox(
              fit: BoxFit.scaleDown,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  label!,
                  style: TextStyle(
                    color: emphasized ? colors.onPrimary : colors.onSurface,
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
    );
  }
}
