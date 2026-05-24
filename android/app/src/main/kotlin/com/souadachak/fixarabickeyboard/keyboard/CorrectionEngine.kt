package com.souadachak.fixarabickeyboard.keyboard

object CorrectionEngine {
    private val arabicWords = listOf(
        "السلام", "عليكم", "ورحمة", "مرحبا", "شكرا", "صباح", "مساء", "الخير", "الله", "اللهم",
        "جميل", "تمام", "نعم", "لا", "اليوم", "غدا", "ممتاز", "أهلا", "حسنا", "اختيار"
    )

    private val englishWords = listOf(
        "hello", "thanks", "please", "good", "morning", "night", "today", "tomorrow", "yes", "no",
        "okay", "great", "message", "keyboard", "fix", "arabic", "game", "settings", "test", "love"
    )

    private val frenchWords = listOf(
        "bonjour", "merci", "s'il", "vous", "plaît", "salut", "oui", "non", "bien", "très",
        "clavier", "message", "écrire", "aujourd'hui", "demain", "test", "paramètres", "arabe", "corriger", "choix"
    )

    fun fix(input: String): String {
        return input.trim()
            .replace("  ", " ")
            .replace("اا", "ا")
    }

    fun suggestions(input: String): List<String> {
        val token = lastToken(input)
        val source = dictionaryFor(token, input)
        val matches = if (token.isBlank()) {
            source.take(3)
        } else {
            source.filter { it.startsWith(token, ignoreCase = true) }.take(3)
        }
        return (matches + source).distinct().take(3)
    }

    private fun lastToken(input: String): String {
        return input.trim().split(Regex("\\s+")).lastOrNull().orEmpty()
    }

    private fun dictionaryFor(token: String, input: String): List<String> {
        val text = token.ifBlank { input }
        return when {
            text.any { it in '\u0600'..'\u06FF' } -> arabicWords
            text.any { it in "éèêëàâîïôùûçœ" } -> frenchWords
            else -> englishWords
        }
    }
}
