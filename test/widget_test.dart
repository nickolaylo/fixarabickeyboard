import 'package:fixarabickeyboard/app/fix_arabic_keyboard_app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('redesigned companion shell starts and switches tabs', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const FixArabicKeyboardApp());
    await tester.pumpAndSettle();

    expect(find.text('Magic Arabic mode'), findsOneWidget);
    expect(find.text('Live design preview'), findsOneWidget);

    await tester.tap(find.text('Rewards'));
    await tester.pumpAndSettle();
    expect(find.text('Magic wand rewards'), findsOneWidget);

    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('Typing'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.text('Keyboard height'),
      300,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.pumpAndSettle();

    expect(find.text('Keyboard height'), findsOneWidget);
    expect(find.text('Letter size'), findsOneWidget);
    expect(find.text('Bottom spacing'), findsOneWidget);
    expect(find.text('Key borders'), findsOneWidget);
  });
}
