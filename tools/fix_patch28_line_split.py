from pathlib import Path

# Replace the malformed character literal (a real line break between quotes)
# with Kotlin's platform-safe lineSequence().
path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = path.read_text(encoding="utf-8")
old = """.split('
')
            .sumOf"""
new = ".lineSequence()\n            .sumOf"
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Expected one malformed multiline split expression, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
