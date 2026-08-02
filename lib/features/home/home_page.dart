import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';
import '../../shared/widgets/app_section_card.dart';
import '../../shared/widgets/keyboard_preview_card.dart';
import '../activation/activation_page.dart';
import '../keyboard_settings/keyboard_settings_page.dart';
import '../rewards/rewards_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    final titles = [strings.keyboard, strings.rewards, strings.settings];

    return Directionality(
      textDirection: strings.isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(title: Text(titles[_selectedIndex])),
        body: IndexedStack(
          index: _selectedIndex,
          children: [
            _KeyboardOverviewTab(
              onOpenActivation: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const ActivationPage()),
                );
              },
            ),
            const RewardsPage(embedded: true),
            const KeyboardSettingsPage(embedded: true),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _selectedIndex,
          onDestinationSelected: (index) {
            setState(() => _selectedIndex = index);
          },
          destinations: [
            NavigationDestination(
              icon: const Icon(Icons.keyboard_alt_outlined),
              selectedIcon: const Icon(Icons.keyboard_alt_rounded),
              label: strings.keyboard,
            ),
            NavigationDestination(
              icon: const Icon(Icons.auto_awesome_outlined),
              selectedIcon: const Icon(Icons.auto_awesome_rounded),
              label: strings.rewards,
            ),
            NavigationDestination(
              icon: const Icon(Icons.settings_outlined),
              selectedIcon: const Icon(Icons.settings_rounded),
              label: strings.settings,
            ),
          ],
        ),
      ),
    );
  }
}

class _KeyboardOverviewTab extends StatelessWidget {
  const _KeyboardOverviewTab({required this.onOpenActivation});

  final VoidCallback onOpenActivation;

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _MagicHero(strings: strings),
        const SizedBox(height: 16),
        KeyboardPreviewCard(
          title: strings.previewTitle,
          subtitle: strings.previewBody,
        ),
        const SizedBox(height: 12),
        AppSectionCard(
          icon: Icons.rocket_launch_outlined,
          title: strings.openActivation,
          body: strings.activationHint,
          trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 17),
          onTap: onOpenActivation,
        ),
        const SizedBox(height: 12),
        AppSectionCard(
          icon: Icons.shield_outlined,
          title: strings.localProcessingTitle,
          body: strings.localProcessingBody,
          accent: Theme.of(context).colorScheme.tertiary,
        ),
      ],
    );
  }
}

class _MagicHero extends StatelessWidget {
  const _MagicHero({required this.strings});

  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: AlignmentDirectional.topStart,
          end: AlignmentDirectional.bottomEnd,
          colors: [
            colors.primary,
            Color.lerp(colors.primary, colors.tertiary, 0.48)!,
          ],
        ),
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: colors.primary.withValues(alpha: 0.22),
            blurRadius: 24,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: colors.onPrimary.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(18),
            ),
            alignment: Alignment.center,
            child: Icon(
              Icons.auto_fix_high_rounded,
              color: colors.onPrimary,
              size: 31,
            ),
          ),
          const SizedBox(height: 18),
          Text(
            strings.magicWandTitle,
            style: theme.textTheme.headlineSmall?.copyWith(
              color: colors.onPrimary,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            strings.magicWandBody,
            style: theme.textTheme.bodyLarge?.copyWith(
              color: colors.onPrimary.withValues(alpha: 0.92),
              height: 1.45,
            ),
          ),
          const SizedBox(height: 14),
          Text(
            strings.appSubtitle,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: colors.onPrimary.withValues(alpha: 0.76),
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}
