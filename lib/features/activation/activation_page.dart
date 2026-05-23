import 'package:flutter/material.dart';

import '../../core/localization/app_strings.dart';

class ActivationPage extends StatelessWidget {
  const ActivationPage({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(strings.activation)),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Text(strings.activationHint, style: Theme.of(context).textTheme.titleMedium),
      ),
    );
  }
}
