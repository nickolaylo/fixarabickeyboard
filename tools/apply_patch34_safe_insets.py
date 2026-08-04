from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


ime_path = Path(
    "android/app/src/main/kotlin/com/souadachak/"
    "fixarabickeyboard/keyboard/KeyboardImeService.kt"
)
ime = ime_path.read_text(encoding="utf-8")

ime = replace_once(
    ime,
    "import android.os.Handler\nimport android.os.Looper\n",
    "import android.os.Build\nimport android.os.Handler\nimport android.os.Looper\n",
    "add Build import",
)
ime = replace_once(
    ime,
    "import android.view.View\nimport android.view.inputmethod.EditorInfo\n",
    "import android.view.View\nimport android.view.WindowInsets\nimport android.view.inputmethod.EditorInfo\n",
    "add WindowInsets import",
)
ime = replace_once(
    ime,
    '''        root.addView(
            keyboardStack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        updateSuggestions()
        return root
''',
    '''        root.addView(
            keyboardStack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        root.setOnApplyWindowInsetsListener { _, insets ->
            val verticalPadding = dp(if (isLandscapeKeyboard()) 3 else 4)
            val userBottomPadding = dp(currentBottomSpacingDp())
            val systemBottomPadding = navigationBottomInset(insets)
            val resolvedBottomPadding =
                verticalPadding + userBottomPadding + systemBottomPadding

            if (keyboardStack.paddingBottom != resolvedBottomPadding) {
                keyboardStack.setPadding(
                    dp(8),
                    verticalPadding,
                    dp(8),
                    resolvedBottomPadding
                )
                keyboardStack.requestLayout()
            }
            insets
        }
        root.post { root.requestApplyInsets() }

        updateSuggestions()
        return root
''',
    "install navigation inset listener",
)
ime = replace_once(
    ime,
    '''    private fun currentBottomSpacingDp(): Int {
        return prefs.getInt(BOTTOM_SPACING_DP_KEY, DEFAULT_BOTTOM_SPACING_DP)
            .coerceIn(MIN_BOTTOM_SPACING_DP, MAX_BOTTOM_SPACING_DP)
    }

    private fun currentKeyBordersEnabled(): Boolean {
''',
    '''    private fun currentBottomSpacingDp(): Int {
        return prefs.getInt(BOTTOM_SPACING_DP_KEY, DEFAULT_BOTTOM_SPACING_DP)
            .coerceIn(MIN_BOTTOM_SPACING_DP, MAX_BOTTOM_SPACING_DP)
    }

    private fun navigationBottomInset(insets: WindowInsets): Int {
        val bottomInset = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val navigationBars = insets.getInsets(
                    WindowInsets.Type.navigationBars()
                ).bottom
                val mandatoryGestures = insets.getInsets(
                    WindowInsets.Type.mandatorySystemGestures()
                ).bottom
                maxOf(navigationBars, mandatoryGestures)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                maxOf(
                    insets.systemWindowInsetBottom,
                    insets.systemGestureInsets.bottom
                )
            }
            else -> insets.systemWindowInsetBottom
        }
        return bottomInset.coerceAtLeast(0)
    }

    private fun currentKeyBordersEnabled(): Boolean {
''',
    "add navigation inset resolver",
)
ime_path.write_text(ime, encoding="utf-8", newline="\n")

journal_path = Path("PROJECT_JOURNAL.md")
journal = journal_path.read_text(encoding="utf-8")
journal = replace_once(
    journal,
    "- النطاق محصور في أول حزمة من إعدادات التخصيص المعتمدة: ارتفاع اللوحة، حجم الحروف، المسافة السفلية، وحدود المفاتيح، مع إصلاح تمرير حقل التحويل متعدد الأسطر داخل نافذة ثابتة بسطرين.\n",
    "- النطاق محصور في أول حزمة من إعدادات التخصيص المعتمدة: ارتفاع اللوحة، حجم الحروف، المسافة السفلية، وحدود المفاتيح، مع إصلاح تمرير حقل التحويل متعدد الأسطر داخل نافذة ثابتة بسطرين وحماية الصف السفلي من أزرار وتنقل Android.\n",
    "update current scope",
)
journal = replace_once(
    journal,
    "- تحفظ الإعدادات مركزيًا في ملف تفضيلات لوحة المفاتيح نفسه وتُطبق عند فتح حقل كتابة أو إعادة استعمال واجهة الإدخال.\n",
    "- تحفظ الإعدادات مركزيًا في ملف تفضيلات لوحة المفاتيح نفسه وتُطبق عند فتح حقل كتابة أو إعادة استعمال واجهة الإدخال.\n- تُحجز مساحة تنقل Android السفلية تلقائيًا تحت المفاتيح، ثم تضاف فوقها المسافة الاختيارية التي يحددها المستخدم؛ فلا تتداخل أيقونة إخفاء اللوحة أو تبديل طريقة الإدخال أو شريط الإيماءات مع الصف السفلي على الهاتف والتابلت.\n",
    "document safe navigation inset",
)
journal_path.write_text(journal, encoding="utf-8", newline="\n")
