import 'package:flutter_test/flutter_test.dart';
import 'package:fixarabickeyboard/app/fix_arabic_keyboard_app.dart';

void main() {
  testWidgets('App starts without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const FixArabicKeyboardApp());
    await tester.pump();

    expect(find.text('Fix Arabic Keyboard'), findsWidgets);
  });
}
