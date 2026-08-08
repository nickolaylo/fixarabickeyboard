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

  static const int minKeyboardHeightPercent = 85;
  static const int maxKeyboardHeightPercent = 115;
  static const int minLetterSizePercent = 85;
  static const int maxLetterSizePercent = 120;
  static const int minBottomSpacingDp = 0;
  static const int maxBottomSpacingDp = 24;

  bool correctionEnabled = true;
  bool hapticEnabled = true;
  NumberRowMode numberRowMode = NumberRowMode.portraitOnly;
  int keyboardHeightPercent = 100;
  int letterSizePercent = 100;
  int bottomSpacingDp = 0;
  bool keyBordersEnabled = true;

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

  Future<void> loadKeyboardAppearance() async {
    try {
      final values = await _channel.invokeMapMethod<String, dynamic>(
        'getKeyboardAppearance',
      );
      keyboardHeightPercent = _boundedInt(
        values?['keyboardHeightPercent'],
        fallback: 100,
        min: minKeyboardHeightPercent,
        max: maxKeyboardHeightPercent,
      );
      letterSizePercent = _boundedInt(
        values?['letterSizePercent'],
        fallback: 100,
        min: minLetterSizePercent,
        max: maxLetterSizePercent,
      );
      bottomSpacingDp = _boundedInt(
        values?['bottomSpacingDp'],
        fallback: 0,
        min: minBottomSpacingDp,
        max: maxBottomSpacingDp,
      );
      keyBordersEnabled = values?['keyBordersEnabled'] as bool? ?? true;
    } on MissingPluginException {
      _restoreAppearanceDefaults();
    } on PlatformException {
      _restoreAppearanceDefaults();
    }
  }

  Future<void> setKeyboardAppearance({
    required int keyboardHeightPercent,
    required int letterSizePercent,
    required int bottomSpacingDp,
    required bool keyBordersEnabled,
  }) async {
    this.keyboardHeightPercent = keyboardHeightPercent
        .clamp(minKeyboardHeightPercent, maxKeyboardHeightPercent)
        .toInt();
    this.letterSizePercent = letterSizePercent
        .clamp(minLetterSizePercent, maxLetterSizePercent)
        .toInt();
    this.bottomSpacingDp = bottomSpacingDp
        .clamp(minBottomSpacingDp, maxBottomSpacingDp)
        .toInt();
    this.keyBordersEnabled = keyBordersEnabled;

    try {
      await _channel.invokeMethod<void>(
        'setKeyboardAppearance',
        <String, Object>{
          'keyboardHeightPercent': this.keyboardHeightPercent,
          'letterSizePercent': this.letterSizePercent,
          'bottomSpacingDp': this.bottomSpacingDp,
          'keyBordersEnabled': this.keyBordersEnabled,
        },
      );
    } on MissingPluginException {
      // Widget tests and unsupported platforms keep the in-memory value.
    } on PlatformException {
      // Keep the selected values in memory; Android will retry on the next change.
    }
  }

  int _boundedInt(
    Object? value, {
    required int fallback,
    required int min,
    required int max,
  }) {
    final parsed = value is num ? value.round() : fallback;
    return parsed.clamp(min, max).toInt();
  }

  void _restoreAppearanceDefaults() {
    keyboardHeightPercent = 100;
    letterSizePercent = 100;
    bottomSpacingDp = 0;
    keyBordersEnabled = true;
  }
}
