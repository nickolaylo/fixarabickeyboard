from pathlib import Path

path = Path("PROJECT_JOURNAL.md")
text = path.read_text(encoding="utf-8")

old_stage = """- اكتمل الأساس البصري للتطبيق المرافق ولوحة Android الفعلية.
- اكتملت طبقة الكتابة السحرية والشريط الذكي بالاستفادة من فيديو Gboard دون تقليد هويته أو وظائفه غير المرتبطة بمهمتنا.
- يجري الآن اختبار `Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys`.
- الفرع النشط: `agent/patch-27-magic-compose-spacing-compact-suggestions`.
- النطاق محصور في فصل الرسائل المتتابعة، تخفيف ارتفاع سطر الإدخال وشريط الأدوات، وتحويل اقتراحات القاموس إلى أزرار أوضح.
- لا تغيير في نظام المكافآت أو AdMob أو الإرسال النهائي إلى التطبيق الهدف ضمن هذا الباتش.
"""
new_stage = """- اكتمل الأساس البصري للتطبيق المرافق ولوحة Android الفعلية.
- اكتملت طبقة الكتابة السحرية والشريط الذكي، واعتمدت Patch 27 كقاعدة البناء الحالية.
- يجري الآن اختبار `Patch 28 — Magic Compose Close & Multiline Input`.
- الفرع النشط: `agent/patch-28-magic-compose-close-and-multiline`.
- النطاق محصور في زر إغلاق مستقل ودعم كتابة عدة أسطر قبل التحويل.
- لا تغيير في القواميس أو نظام المكافآت أو AdMob أو منطق التحويل ضمن هذا الباتش.
"""
if old_stage not in text:
    raise RuntimeError("stage block not found")
text = text.replace(old_stage, new_stage, 1)
text = text.replace(
    "## Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys (Candidate)",
    "## Patch 27 — Magic Compose Spacing, Compact Bars & Suggestion Keys (Final Base)",
    1,
)
needle = "- لا تغيير في القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob.\n\n## Patch 28"
replacement = "- لا تغيير في القواميس أو ترتيبها أو التعلم المحلي أو العملات أو AdMob.\n- نجح اختبار المستخدم واعتمدت النتيجة ودمجت PR #6 في `main`.\n\n## Patch 28"
if needle not in text:
    raise RuntimeError("Patch 27 close anchor not found")
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding="utf-8")
