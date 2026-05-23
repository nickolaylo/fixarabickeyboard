import 'package:flutter/material.dart';

import '../../core/coins/coins_wallet.dart';
import '../../core/localization/app_strings.dart';
import '../../shared/widgets/app_section_card.dart';
import '../activation/activation_page.dart';
import '../keyboard_settings/keyboard_settings_page.dart';
import '../premium/premium_page.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    return Directionality(
      textDirection: strings.isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(title: Text(strings.appName)),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(strings.appSubtitle, style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 18),
            AppSectionCard(
              icon: Icons.keyboard_alt_outlined,
              title: strings.activation,
              body: strings.activationHint,
              trailing: const Icon(Icons.chevron_right),
            ),
            const SizedBox(height: 12),
            AppSectionCard(
              icon: Icons.toll_outlined,
              title: strings.coins,
              body: '${strings.dailyCoins}: ${CoinsWallet.instance.coins}',
            ),
            const SizedBox(height: 12),
            AppSectionCard(
              icon: Icons.workspace_premium_outlined,
              title: strings.premium,
              body: strings.unlimitedPremium,
            ),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: 0,
          destinations: [
            NavigationDestination(icon: const Icon(Icons.home_outlined), label: strings.appName),
            NavigationDestination(icon: const Icon(Icons.keyboard_alt_outlined), label: strings.activation),
            NavigationDestination(icon: const Icon(Icons.settings_outlined), label: strings.settings),
            NavigationDestination(icon: const Icon(Icons.workspace_premium_outlined), label: strings.premium),
          ],
          onDestinationSelected: (index) {
            if (index == 1) Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ActivationPage()));
            if (index == 2) Navigator.of(context).push(MaterialPageRoute(builder: (_) => const KeyboardSettingsPage()));
            if (index == 3) Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PremiumPage()));
          },
        ),
      ),
    );
  }
}
