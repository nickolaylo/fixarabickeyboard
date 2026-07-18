package com.souadachak.fixarabickeyboard.keyboard

/** Text repair stays independent from the centralized suggestion dictionaries. */
object CorrectionEngine {
    fun fix(input: String): String {
        return input.trim()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace("اا", "ا")
    }
}
