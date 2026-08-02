import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';
import '../../shared/widgets/app_section_card.dart';

class RewardsPage extends StatelessWidget {
  const RewardsPage({
    super.key,
    this.embedded = false,
  });

  final bool embedded;

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    final content = ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _RewardHero(strings: strings),
        const SizedBox(height: 16),
        AppSectionCard(
          icon: Icons.verified_outlined,
          title: strings.rewardPrincipleTitle,
          body: strings.rewardPrincipleBody,
          accent: Theme.of(context).colorScheme.tertiary,
        ),
        const SizedBox(height: 12),
        AppSectionCard(
          icon: Icons.construction_rounded,
          title: strings.rewardStatusTitle,
          body: strings.rewardStatusBody,
        ),
      ],
    );

    if (embedded) return content;

    return Scaffold(
      appBar: AppBar(title: Text(strings.rewards)),
      body: content,
    );
  }
}

class _RewardHero extends StatelessWidget {
  const _RewardHero({required this.strings});

  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            colors.primaryContainer,
            colors.tertiaryContainer.withValues(alpha: 0.86),
          ],
        ),
        borderRadius: BorderRadius.circular(28),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: colors.surface.withValues(alpha: 0.82),
                borderRadius: BorderRadius.circular(18),
              ),
              alignment: Alignment.center,
              child: Icon(
                Icons.auto_fix_high_rounded,
                size: 30,
                color: colors.primary,
              ),
            ),
          ),
          const SizedBox(height: 18),
          Text(
            strings.rewardCenterTitle,
            style: theme.textTheme.headlineSmall?.copyWith(
              color: colors.onPrimaryContainer,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            strings.rewardCenterBody,
            style: theme.textTheme.bodyLarge?.copyWith(
              color: colors.onPrimaryContainer,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 20),
          FilledButton.tonalIcon(
            onPressed: null,
            icon: const Icon(Icons.ondemand_video_outlined),
            label: Text(strings.rewardAdsComing),
          ),
        ],
      ),
    );
  }
}
