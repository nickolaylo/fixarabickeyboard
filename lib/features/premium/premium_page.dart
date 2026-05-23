import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';

class PremiumPage extends StatelessWidget {
  const PremiumPage({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(strings.premium)),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(strings.unlimitedPremium, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            FilledButton(onPressed: () {}, child: Text(strings.buyCoins)),
            const SizedBox(height: 8),
            OutlinedButton(onPressed: () {}, child: Text(strings.watchRewardedAd)),
          ],
        ),
      ),
    );
  }
}
