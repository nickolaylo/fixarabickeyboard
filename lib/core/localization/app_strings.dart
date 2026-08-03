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

  String get appName => pick(
        en: 'Fix Arabic Keyboard',
        ar: 'لوحة Fix Arabic',
        fr: 'Fix Arabic Keyboard',
      );
  String get appSubtitle => pick(
        en: 'Write natural Arabic, even in apps and games that display it incorrectly.',
        ar: 'اكتب العربية بصورة طبيعية حتى داخل التطبيقات والألعاب التي تعرضها بشكل غير صحيح.',
        fr: 'Écrivez naturellement en arabe, même dans les apps et jeux qui l’affichent mal.',
      );
  String get keyboard => pick(en: 'Keyboard', ar: 'لوحة المفاتيح', fr: 'Clavier');
  String get rewards => pick(en: 'Rewards', ar: 'المكافآت', fr: 'Récompenses');
  String get settings => pick(en: 'Settings', ar: 'الإعدادات', fr: 'Paramètres');
  String get activation => pick(en: 'Activation', ar: 'التفعيل', fr: 'Activation');
  String get activationHint => pick(
        en: 'Enable the keyboard from Android settings, choose it, then test it safely here.',
        ar: 'فعّل اللوحة من إعدادات Android، ثم اخترها واختبرها هنا بأمان.',
        fr: 'Activez le clavier dans Android, choisissez-le, puis testez-le ici en toute sécurité.',
      );
  String get openActivation => pick(
        en: 'Set up the keyboard',
        ar: 'إعداد لوحة المفاتيح',
        fr: 'Configurer le clavier',
      );
  String get magicWandTitle => pick(
        en: 'Magic Arabic mode',
        ar: 'وضع العصا السحرية',
        fr: 'Mode arabe magique',
      );
  String get magicWandBody => pick(
        en: 'Type natural Arabic inside the keyboard. The repair happens locally when you send.',
        ar: 'اكتب العربية الطبيعية داخل اللوحة، ويحدث الإصلاح محليًا عند الإرسال.',
        fr: 'Saisissez l’arabe naturel dans le clavier. La correction locale se fait à l’envoi.',
      );
  String get previewTitle => pick(
        en: 'Live design preview',
        ar: 'معاينة التصميم',
        fr: 'Aperçu du design',
      );
  String get previewBody => pick(
        en: 'A clean, fast keyboard focused on Arabic repair.',
        ar: 'لوحة نظيفة وسريعة تركز على إصلاح العربية.',
        fr: 'Un clavier clair et rapide centré sur la correction de l’arabe.',
      );
  String get localProcessingTitle => pick(
        en: 'Private by design',
        ar: 'خصوصية من أساس التصميم',
        fr: 'Confidentiel par conception',
      );
  String get localProcessingBody => pick(
        en: 'Typed message content stays on the device and is never added to Analytics events.',
        ar: 'يبقى محتوى الرسائل على الجهاز ولا يُضاف أبدًا إلى أحداث Analytics.',
        fr: 'Le contenu saisi reste sur l’appareil et n’est jamais ajouté aux événements Analytics.',
      );

  String get activationStepOneTitle => pick(
        en: 'Enable Fix Arabic Keyboard',
        ar: 'فعّل لوحة Fix Arabic',
        fr: 'Activez Fix Arabic Keyboard',
      );
  String get activationStepOneBody => pick(
        en: 'Open Android keyboard settings and enable Fix Arabic Keyboard.',
        ar: 'افتح إعدادات لوحات المفاتيح في Android وفعّل لوحة Fix Arabic.',
        fr: 'Ouvrez les paramètres Android et activez Fix Arabic Keyboard.',
      );
  String get activationStepTwoTitle => pick(
        en: 'Choose the keyboard',
        ar: 'اختر لوحة المفاتيح',
        fr: 'Choisissez le clavier',
      );
  String get activationStepTwoBody => pick(
        en: 'Choose Fix Arabic Keyboard as the current input method.',
        ar: 'اختر لوحة Fix Arabic كطريقة الإدخال الحالية.',
        fr: 'Choisissez Fix Arabic Keyboard comme méthode de saisie active.',
      );
  String get activationStepThreeTitle => pick(
        en: 'Test before leaving',
        ar: 'اختبرها قبل المغادرة',
        fr: 'Testez avant de quitter',
      );
  String get activationStepThreeBody => pick(
        en: 'Use the safe test box below and make sure typing feels right.',
        ar: 'استعمل مربع الاختبار الآمن أسفل الصفحة وتأكد أن الكتابة مريحة.',
        fr: 'Utilisez la zone de test ci-dessous et vérifiez que la saisie vous convient.',
      );
  String get openKeyboardSettings => pick(
        en: 'Open keyboard settings',
        ar: 'فتح إعدادات لوحات المفاتيح',
        fr: 'Ouvrir les paramètres du clavier',
      );
  String get chooseKeyboard => pick(
        en: 'Choose keyboard',
        ar: 'اختيار لوحة المفاتيح',
        fr: 'Choisir le clavier',
      );
  String get keyboardTestTitle => pick(
        en: 'Keyboard test',
        ar: 'اختبار لوحة المفاتيح',
        fr: 'Test du clavier',
      );
  String get keyboardTestBody => pick(
        en: 'Use this box without opening another app.',
        ar: 'استعمل هذا المربع دون الحاجة إلى فتح تطبيق آخر.',
        fr: 'Utilisez cette zone sans ouvrir une autre application.',
      );
  String get keyboardTestHint => pick(
        en: 'Test typing here...',
        ar: 'جرّب الكتابة هنا...',
        fr: 'Testez l’écriture ici...',
      );
  String get chooseKeyboardForTest => pick(
        en: 'Choose keyboard for test',
        ar: 'اختيار اللوحة للتجربة',
        fr: 'Choisir le clavier pour le test',
      );
  String get activationPrivacyTitle => pick(
        en: 'Why Android shows a warning',
        ar: 'لماذا يعرض Android تحذيرًا؟',
        fr: 'Pourquoi Android affiche un avertissement',
      );
  String get activationPrivacyBody => pick(
        en: 'Android shows the same general warning for every third-party keyboard. Fix Arabic processes repair locally and does not collect message content.',
        ar: 'يعرض Android التحذير العام نفسه لكل لوحة خارجية. تعالج Fix Arabic الإصلاح محليًا ولا تجمع محتوى الرسائل.',
        fr: 'Android affiche le même avertissement général pour tous les claviers tiers. Fix Arabic traite la correction localement et ne collecte pas le contenu des messages.',
      );

  String get rewardCenterTitle => pick(
        en: 'Magic wand rewards',
        ar: 'مكافآت العصا السحرية',
        fr: 'Récompenses de la baguette',
      );
  String get rewardCenterBody => pick(
        en: 'Optional rewarded ads will add more magic-wand uses without placing ads inside the keyboard.',
        ar: 'ستضيف الإعلانات الاختيارية بمكافأة استخدامات إضافية للعصا دون وضع إعلان داخل لوحة المفاتيح.',
        fr: 'Des pubs récompensées facultatives ajouteront des usages sans afficher de pub dans le clavier.',
      );
  String get rewardStatusTitle => pick(
        en: 'Reward system status',
        ar: 'حالة نظام المكافآت',
        fr: 'État du système de récompenses',
      );
  String get rewardStatusBody => pick(
        en: 'The visual foundation is ready. Reward values and AdMob connection will be added in a dedicated patch.',
        ar: 'الأساس البصري جاهز. ستُضاف قيم المكافآت وربط AdMob في باتش مخصص.',
        fr: 'La base visuelle est prête. Les valeurs et AdMob seront ajoutés dans un patch dédié.',
      );
  String get rewardAdsComing => pick(
        en: 'Rewarded ads are not connected yet',
        ar: 'الإعلانات بمكافأة غير مرتبطة بعد',
        fr: 'Les pubs récompensées ne sont pas encore connectées',
      );
  String get rewardPrincipleTitle => pick(
        en: 'Fair use principle',
        ar: 'مبدأ الاستخدام العادل',
        fr: 'Principe d’utilisation équitable',
      );
  String get rewardPrincipleBody => pick(
        en: 'Only a successful magic send may consume one use. Failed insertion or sending must not deduct anything.',
        ar: 'لا يستهلك الرصيد إلا إرسال سحري ناجح، ولا يُخصم شيء عند فشل الإدراج أو الإرسال.',
        fr: 'Seul un envoi magique réussi peut consommer un usage. Aucun débit en cas d’échec.',
      );

  String get typingSection => pick(en: 'Typing', ar: 'الكتابة', fr: 'Saisie');
  String get numberRowSetting => pick(
        en: 'Number row',
        ar: 'صف الأرقام',
        fr: 'Rangée de chiffres',
      );
  String get numberRowSettingBody => pick(
        en: 'Choose when the number row appears above the letters.',
        ar: 'اختر متى يظهر صف الأرقام فوق الحروف.',
        fr: 'Choisissez quand la rangée de chiffres apparaît au-dessus des lettres.',
      );
  String get numberRowAlways => pick(
        en: 'Always',
        ar: 'دائمًا',
        fr: 'Toujours',
      );
  String get numberRowPortraitOnly => pick(
        en: 'Portrait only',
        ar: 'في الوضع العمودي فقط',
        fr: 'En mode portrait uniquement',
      );
  String get numberRowHidden => pick(
        en: 'Hidden',
        ar: 'مخفي',
        fr: 'Masquée',
      );
  String get appearanceSection => pick(en: 'Appearance', ar: 'المظهر', fr: 'Apparence');
  String get accessibilitySection => pick(en: 'Comfort and accessibility', ar: 'الراحة وإمكانية الوصول', fr: 'Confort et accessibilité');
  String get correctionSetting => pick(
        en: 'Arabic repair',
        ar: 'إصلاح العربية',
        fr: 'Correction de l’arabe',
      );
  String get correctionSettingBody => pick(
        en: 'Keep the repair engine available from the magic wand.',
        ar: 'إبقاء محرك الإصلاح متاحًا من العصا السحرية.',
        fr: 'Gardez le moteur de correction accessible depuis la baguette.',
      );
  String get hapticSetting => pick(
        en: 'Haptic feedback',
        ar: 'الاهتزاز عند اللمس',
        fr: 'Retour haptique',
      );
  String get hapticSettingBody => pick(
        en: 'A light response when a keyboard key is pressed.',
        ar: 'استجابة خفيفة عند الضغط على مفاتيح اللوحة.',
        fr: 'Une légère vibration lors de l’appui sur une touche.',
      );
  String get appearanceRoadmapTitle => pick(
        en: 'Keyboard appearance',
        ar: 'مظهر لوحة المفاتيح',
        fr: 'Apparence du clavier',
      );
  String get appearanceRoadmapBody => pick(
        en: 'Themes, key height, letter size, key borders and bottom spacing will be connected to the real keyboard in the next visual phase.',
        ar: 'ستُربط الثيمات وارتفاع المفاتيح وحجم الحروف وحدودها والمسافة السفلية باللوحة الحقيقية في المرحلة البصرية التالية.',
        fr: 'Les thèmes, la hauteur, la taille des lettres, les bordures et l’espace inférieur seront reliés au vrai clavier lors de la prochaine phase.',
      );
  String get gestureRoadmapTitle => pick(
        en: 'Useful gestures',
        ar: 'إيماءات مفيدة',
        fr: 'Gestes utiles',
      );
  String get gestureRoadmapBody => pick(
        en: 'Cursor movement on the space bar and swipe deletion will be studied before activation.',
        ar: 'ستُدرس حركة المؤشر من زر المسافة والحذف بالسحب قبل تفعيلهما.',
        fr: 'Le déplacement du curseur sur la barre espace et la suppression par glissement seront étudiés avant activation.',
      );

  // Historical getters kept temporarily so older screens remain source-compatible.
  String get premium => pick(en: 'Premium', ar: 'النسخة الكاملة', fr: 'Premium');
  String get coins => pick(en: 'Coins', ar: 'الرصيد', fr: 'Crédits');
  String get dailyCoins => pick(en: 'Daily free coins', ar: 'رصيد مجاني يومي', fr: 'Crédits gratuits quotidiens');
  String get watchRewardedAd => pick(en: 'Watch ad to refill', ar: 'شاهد إعلانًا للتعبئة', fr: 'Regarder une pub pour recharger');
  String get buyCoins => pick(en: 'Buy coins', ar: 'شراء رصيد', fr: 'Acheter des crédits');
  String get unlimitedPremium => pick(en: 'Premium unlocks unlimited fixes.', ar: 'النسخة الكاملة تفتح الإصلاح بدون حدود.', fr: 'Premium débloque les corrections illimitées.');
}
