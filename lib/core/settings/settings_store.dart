import 'package:flutter/services.dart';

enum NumberRowMode {
  always('always'),
  portraitOnly('portrait_only'),
  hidden('hidden');

  const NumberRowMode(this.platformValue);

  final String platformValue;

  static NumberRowMode fromPlatform(String? value) {
    return NumberRowMode.values.firstWhere(
      (mode) => mode.platformValue == value,
      orElse: () => NumberRowMode.portraitOnly,
    );
  }
}

class SettingsStore {
  SettingsStore._();

  static final SettingsStore instance = SettingsStore._();
  static const MethodChannel _channel = MethodChannel(
    'fix_arabic_keyboard/input_methods',
  );

  bool correctionEnabled = true;
  bool hapticEnabled = true;
  NumberRowMode numberRowMode = NumberRowMode.portraitOnly;

  Future<void> loadNumberRowMode() async {
    try {
      final value = await _channel.invokeMethod<String>('getNumberRowMode');
      numberRowMode = NumberRowMode.fromPlatform(value);
    } on MissingPluginException {
      numberRowMode = NumberRowMode.portraitOnly;
    } on PlatformException {
      numberRowMode = NumberRowMode.portraitOnly;
    }
  }

  Future<void> setNumberRowMode(NumberRowMode mode) async {
    numberRowMode = mode;
    try {
      await _channel.invokeMethod<void>(
        'setNumberRowMode',
        <String, String>{'mode': mode.platformValue},
      );
    } on MissingPluginException {
      // Widget tests and unsupported platforms keep the in-memory value.
    } on PlatformException {
      // Keep the selected value in memory; Android will retry on the next change.
    }
  }
}
