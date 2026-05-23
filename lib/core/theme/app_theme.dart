import 'package:flutter/material.dart';

class AppTheme {
  static const _seed = Color(0xFF6E57E0);

  static ThemeData get light => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: _seed),
        cardTheme: const CardThemeData(margin: EdgeInsets.zero),
      );

  static ThemeData get dark => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: _seed, brightness: Brightness.dark),
        cardTheme: const CardThemeData(margin: EdgeInsets.zero),
      );
}
