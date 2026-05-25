package com.souadachak.fixarabickeyboard.keyboard

object CorrectionEngine {
    private val arabicWords = listOf(
        "السلام", "عليكم", "ورحمة", "الله", "وبركاته", "مرحبا", "أهلا", "شكرا", "عفوا", "تمام",
        "نعم", "لا", "حسنا", "جميل", "ممتاز", "اليوم", "غدا", "الآن", "صباح", "مساء",
        "الخير", "اللهم", "اختيار", "كتابة", "رسالة", "لوحة", "مفاتيح", "تصحيح", "العربية", "التطبيق",
        "ممكن", "من", "فضلك", "أريد", "هذا", "هذه", "هناك", "كيف", "لماذا", "أين",
        "أنا", "أنت", "نحن", "في", "على", "إلى", "مع", "لك", "كان", "يكون", "جدا", "بعد", "قبل"
    )

    private val englishWords = listOf(
        "hello", "thanks", "please", "good", "morning", "night", "today", "tomorrow", "yes", "no",
        "okay", "great", "message", "keyboard", "fix", "arabic", "game", "settings", "test", "write",
        "the", "and", "you", "are", "can", "with", "for", "this", "that", "now"
    )

    private val frenchWords = listOf(
        "bonjour", "merci", "s'il", "vous", "plaît", "salut", "oui", "non", "bien", "très",
        "clavier", "message", "écrire", "aujourd'hui", "demain", "test", "paramètres", "arabe", "corriger", "choix",
        "je", "tu", "nous", "avec", "pour", "dans", "maintenant", "ce", "cette", "plus"
    )

    private val nextArabicWords = mapOf(
        "السلام" to listOf("عليكم", "ورحمة", "وبركاته"),
        "عليكم" to listOf("ورحمة", "الله", "وبركاته"),
        "ورحمة" to listOf("الله", "وبركاته", "عليكم"),
        "صباح" to listOf("الخير", "النور", "جميل"),
        "مساء" to listOf("الخير", "النور", "جميل"),
        "من" to listOf("فضلك", "أجل", "هنا"),
        "أريد" to listOf("أن", "هذا", "كتابة"),
        "لوحة" to listOf("مفاتيح", "العربية", "التطبيق"),
        "مرحبا" to listOf("بك", "كيف", "أهلا"),
        "شكرا" to listOf("لك", "جزيلا", "على"),
        "حسنا" to listOf("سأفعل", "تمام", "نعم"),
        "هذا" to listOf("جيد", "مهم", "صحيح"),
        "في" to listOf("التطبيق", "اللعبة", "الوقت")
    )

    private val nextEnglishWords = mapOf(
        "good" to listOf("morning", "night", "job"),
        "thank" to listOf("you", "you", "thanks"),
        "hello" to listOf("there", "again", "everyone"),
        "how" to listOf("are", "is", "can"),
        "keyboard" to listOf("settings", "test", "message"),
        "thanks" to listOf("for", "you", "again"),
        "please" to listOf("check", "send", "write"),
        "this" to listOf("is", "message", "keyboard"),
        "I" to listOf("am", "can", "will")
    )

    private val nextFrenchWords = mapOf(
        "s'il" to listOf("vous", "te", "plaît"),
        "vous" to listOf("plaît", "pouvez", "êtes"),
        "très" to listOf("bien", "bon", "important"),
        "bonjour" to listOf("madame", "monsieur", "à"),
        "clavier" to listOf("arabe", "français", "test"),
        "merci" to listOf("beaucoup", "pour", "à"),
        "salut" to listOf("ça", "tout", "à"),
        "je" to listOf("suis", "veux", "peux"),
        "ce" to listOf("message", "clavier", "test")
    )

    fun fix(input: String): String {
        return input.trim()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace("اا", "ا")
    }

    fun suggestions(input: String): List<String> {
        val source = dictionaryForInput(input)
        val token = lastToken(input)
        if (input.isBlank()) return emptyList()

        if (token.isBlank()) {
            val previousWord = previousToken(input)
            val nextWords = nextWordsFor(previousWord, input)
            return (nextWords + source).distinct().take(3)
        }

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

    private fun previousToken(input: String): String {
        val tokens = input
            .trim()
            .split(Regex("\\s+|(?=[،,.؟!؛:])|(?<=[،,.؟!؛:])"))
            .filter { it.isNotBlank() && it.none { char -> isSeparator(char) } }
        return tokens.lastOrNull().orEmpty()
    }

    private fun isSeparator(char: Char): Boolean {
        return char in listOf('،', ',', '.', '؟', '?', '!', '؛', ':', ';', '\n', '\t')
    }

    private fun dictionaryForInput(input: String): List<String> {
        return when {
            input.any { it in '\u0600'..'\u06FF' } -> arabicWords
            input.any { it in "éèêëàâîïôùûçœ" } -> frenchWords
            else -> englishWords
        }
    }

    private fun nextWordsFor(previousWord: String, input: String): List<String> {
        val normalizedPrevious = normalizeForSearch(previousWord)
        val nextMap = when {
            input.any { it in '\u0600'..'\u06FF' } -> nextArabicWords
            input.any { it in "éèêëàâîïôùûçœ" } -> nextFrenchWords
            else -> nextEnglishWords
        }
        return nextMap.entries
            .firstOrNull { normalizeForSearch(it.key) == normalizedPrevious }
            ?.value
            .orEmpty()
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
