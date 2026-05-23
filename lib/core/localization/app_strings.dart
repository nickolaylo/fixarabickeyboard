import 'package:flutter/widgets.dart';

class AppStrings {
  const AppStrings(this.locale);

  final Locale locale;

  static const supportedLocales = [Locale('en'), Locale('ar'), Locale('fr')];

  static AppStrings of(BuildContext context) {
    final code = Localizations.localeOf(context).languageCode;
    return AppStrings(Locale(code));
  }

  bool get isArabic => locale.languageCode == 'ar';

  String pick({required String en, required String ar, required String fr}) {
    switch (locale.languageCode) {
      case 'ar':
        return ar;
      case 'fr':
        return fr;
      default:
        return en;
    }
  }

  String get appName => pick(en: 'Fix Arabic Keyboard', ar: 'لوحة Fix Arabic', fr: 'Fix Arabic Keyboard');
  String get appSubtitle => pick(en: 'Write Arabic correctly in apps and games.', ar: 'اكتب العربية بشكل صحيح داخل التطبيقات والألعاب.', fr: 'Écrivez l’arabe correctement dans les apps et jeux.');
  String get activation => pick(en: 'Activation', ar: 'التفعيل', fr: 'Activation');
  String get settings => pick(en: 'Settings', ar: 'الإعدادات', fr: 'Paramètres');
  String get premium => pick(en: 'Premium', ar: 'النسخة الكاملة', fr: 'Premium');
  String get coins => pick(en: 'Coins', ar: 'الرصيد', fr: 'Crédits');
  String get dailyCoins => pick(en: 'Daily free coins', ar: 'رصيد مجاني يومي', fr: 'Crédits gratuits quotidiens');
  String get watchRewardedAd => pick(en: 'Watch ad to refill', ar: 'شاهد إعلانًا للتعبئة', fr: 'Regarder une pub pour recharger');
  String get buyCoins => pick(en: 'Buy coins', ar: 'شراء رصيد', fr: 'Acheter des crédits');
  String get unlimitedPremium => pick(en: 'Premium unlocks unlimited fixes.', ar: 'النسخة الكاملة تفتح الإصلاح بدون حدود.', fr: 'Premium débloque les corrections illimitées.');
  String get activationHint => pick(en: 'Enable the keyboard from Android input settings, then choose it while typing.', ar: 'فعّل لوحة المفاتيح من إعدادات الإدخال في Android، ثم اخترها أثناء الكتابة.', fr: 'Activez le clavier dans les paramètres Android, puis choisissez-le lors de l’écriture.');
  String get activationStepOneTitle => pick(en: 'Step 1', ar: 'الخطوة 1', fr: 'Étape 1');
  String get activationStepOneBody => pick(en: 'Open Android keyboard settings and enable Fix Arabic Keyboard.', ar: 'افتح إعدادات لوحات المفاتيح في Android وفعّل لوحة Fix Arabic.', fr: 'Ouvrez les paramètres du clavier Android et activez Fix Arabic Keyboard.');
  String get activationStepTwoTitle => pick(en: 'Step 2', ar: 'الخطوة 2', fr: 'Étape 2');
  String get activationStepTwoBody => pick(en: 'Choose Fix Arabic Keyboard as the current keyboard while typing.', ar: 'اختر لوحة Fix Arabic كلوحة المفاتيح الحالية أثناء الكتابة.', fr: 'Choisissez Fix Arabic Keyboard comme clavier actif lors de l’écriture.');
  String get openKeyboardSettings => pick(en: 'Open keyboard settings', ar: 'فتح إعدادات لوحة المفاتيح', fr: 'Ouvrir les paramètres du clavier');
  String get chooseKeyboard => pick(en: 'Choose keyboard', ar: 'اختيار لوحة المفاتيح', fr: 'Choisir le clavier');
}
