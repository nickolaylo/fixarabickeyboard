class SettingsStore {
  SettingsStore._();

  static final SettingsStore instance = SettingsStore._();

  bool correctionEnabled = true;
  bool hapticEnabled = true;
}
