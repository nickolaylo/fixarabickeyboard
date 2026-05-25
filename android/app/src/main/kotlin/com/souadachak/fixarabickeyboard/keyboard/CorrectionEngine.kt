package com.souadachak.fixarabickeyboard.keyboard

object CorrectionEngine {
    private val arabicWords = listOf(
        "السلام", "عليكم", "ورحمة", "الله", "وبركاته", "مرحبا", "أهلا", "شكرا", "عفوا", "تمام",
        "نعم", "لا", "حسنا", "جميل", "ممتاز", "اليوم", "غدا", "الآن", "صباح", "مساء",
        "الخير", "اللهم", "اختيار", "كتابة", "رسالة", "لوحة", "مفاتيح", "تصحيح", "العربية", "التطبيق",
        "ممكن", "من", "فضلك", "أريد", "هذا", "هذه", "هناك", "كيف", "لماذا", "أين"
    )

    private val englishWords = listOf(
        "hello", "thanks", "please", "good", "morning", "night", "today", "tomorrow", "yes", "no",
        "okay", "great", "message", "keyboard", "fix", "arabic", "game", "settings", "test", "write"
    )

    private val frenchWords = listOf(
        "bonjour", "merci", "s'il", "vous", "plaît", "salut", "oui", "non", "bien", "très",
        "clavier", "message", "écrire", "aujourd'hui", "demain", "test", "paramètres", "arabe", "corriger", "choix"
    )

    fun fix(input: String): String {
        return input.trim()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace("اا", "ا")
    }

    fun suggestions(input: String): List<String> {
        val token = lastToken(input)
        val source = dictionaryFor(token, input)
        if (token.isBlank()) return source.take(3)

        val normalizedToken = normalizeForSearch(token)
        val startsWithMatches = source.filter { normalizeForSearch(it).startsWith(normalizedToken, ignoreCase = true) }
        val containsMatches = source.filter { normalizeForSearch(it).contains(normalizedToken, ignoreCase = true) }

        return (startsWithMatches + containsMatches + source).distinct().take(3)
    }

    private fun lastToken(input: String): String {
        if (input.isBlank()) return ""
        val lastChar = input.last()
        if (lastChar.isWhitespace() || isSeparator(lastChar)) return ""
        return input.split(Regex("\\s+|(?=[،,.؟!؛:])|(?<=[،,.؟!؛:])"))
            .lastOrNull { it.isNotBlank() && it.none { char -> isSeparator(char) } }
            .orEmpty()
    }

    private fun isSeparator(char: Char): Boolean {
        return char in listOf('،', ',', '.', '؟', '?', '!', '؛', ':', ';', '\n', '\t')
    }

    private fun dictionaryFor(token: String, input: String): List<String> {
        val text = token.ifBlank { input }
        return when {
            text.any { it in '\u0600'..'\u06FF' } -> arabicWords
            text.any { it in "éèêëàâîïôùûçœ" } -> frenchWords
            else -> englishWords
        }
    }

    private fun normalizeForSearch(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
    }
}
