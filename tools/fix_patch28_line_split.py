from pathlib import Path

path = Path("android/app/src/main/kotlin/com/souadachak/fixarabickeyboard/keyboard/KeyboardImeService.kt")
text = path.read_text(encoding="utf-8")
old = ".split('\\n')\n            .sumOf"
new = ".lineSequence()\n            .sumOf"
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Expected one multiline split expression, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
