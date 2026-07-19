package com.souadachak.fixarabickeyboard.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Stores only safe local next-word counts. No full sentences are persisted. */
class LocalNextWordStore(context: Context) {
    private val helper = Helper(context.applicationContext)

    /** Legacy single-word API kept for compatibility. */
    fun record(language: DictionaryLanguage, previousWord: String, nextWord: String) {
        recordContext(language, listOf(previousWord), nextWord)
    }

    /**
     * Stores a one- or two-word context and the word that followed it.
     * Only the normalized context key, next word, count and last-used time are saved.
     */
    fun recordContext(
        language: DictionaryLanguage,
        contextWords: List<String>,
        nextWord: String
    ) {
        val contextKeys = normalizedContext(language, contextWords)
        val nextKey = language.normalize(nextWord)
        if (contextKeys.isEmpty() || nextKey.isEmpty()) return

        val contextKey = contextKeys.joinToString(CONTEXT_SEPARATOR)
        val now = System.currentTimeMillis()
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("language", language.code)
                put("context_key", contextKey)
                put("context_size", contextKeys.size)
                put("next_key", nextKey)
                put("next_word", nextWord)
                put("use_count", 1)
                put("last_used", now)
            }
            val inserted = database.insertWithOnConflict(
                CONTEXT_TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
            if (inserted == -1L) {
                database.execSQL(
                    """
                    UPDATE $CONTEXT_TABLE
                    SET use_count = use_count + 1,
                        next_word = ?,
                        last_used = ?
                    WHERE language = ? AND context_key = ? AND next_key = ?
                    """.trimIndent(),
                    arrayOf(nextWord, now, language.code, contextKey, nextKey)
                )
            }
            pruneContextIfNeeded(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** Legacy single-word lookup kept for existing call sites. */
    fun bestNextWord(language: DictionaryLanguage, previousWord: String): String? {
        return bestNextWord(language, listOf(previousWord), prefix = null)
    }

    fun bestNextWord(
        language: DictionaryLanguage,
        contextWords: List<String>
    ): String? {
        return bestNextWord(language, contextWords, prefix = null)
    }

    /**
     * Returns the best learned word for the exact context.
     * When [prefix] is present, ranking happens only among learned words that match it.
     */
    fun bestNextWord(
        language: DictionaryLanguage,
        contextWords: List<String>,
        prefix: String?
    ): String? {
        val contextKeys = normalizedContext(language, contextWords)
        if (contextKeys.isEmpty()) return null

        val prefixKey = prefix?.let(language::normalize).orEmpty()
        val contextKey = contextKeys.joinToString(CONTEXT_SEPARATOR)
        queryBestContextWord(
            language = language,
            contextKey = contextKey,
            prefixKey = prefixKey
        )?.let { return it }

        // Preserve all previously learned one-word pairs after the database upgrade.
        if (contextKeys.size == 1) {
            return bestLegacyNextWord(language, contextKeys.first(), prefixKey)
        }
        return null
    }

    /** Removes only the selected learned relation from its exact context. */
    fun forget(
        language: DictionaryLanguage,
        contextWords: List<String>,
        nextWord: String
    ): Boolean {
        val contextKeys = normalizedContext(language, contextWords)
        val nextKey = language.normalize(nextWord)
        if (contextKeys.isEmpty() || nextKey.isEmpty()) return false

        val contextKey = contextKeys.joinToString(CONTEXT_SEPARATOR)
        val database = helper.writableDatabase
        database.beginTransaction()
        return try {
            var deleted = database.delete(
                CONTEXT_TABLE,
                "language = ? AND context_key = ? AND next_key = ?",
                arrayOf(language.code, contextKey, nextKey)
            )
            if (contextKeys.size == 1) {
                deleted += database.delete(
                    LEGACY_TABLE,
                    "language = ? AND previous_key = ? AND next_key = ?",
                    arrayOf(language.code, contextKeys.first(), nextKey)
                )
            }
            database.setTransactionSuccessful()
            deleted > 0
        } finally {
            database.endTransaction()
        }
    }

    fun clearAll() {
        val database = helper.writableDatabase
        database.delete(CONTEXT_TABLE, null, null)
        database.delete(LEGACY_TABLE, null, null)
    }

    private fun queryBestContextWord(
        language: DictionaryLanguage,
        contextKey: String,
        prefixKey: String
    ): String? {
        val selection = StringBuilder("language = ? AND context_key = ?")
        val arguments = arrayListOf(language.code, contextKey)
        if (prefixKey.isNotEmpty()) {
            selection.append(" AND next_key LIKE ? ESCAPE '\\'")
            arguments.add(escapeLike(prefixKey) + "%")
        }

        helper.readableDatabase.query(
            CONTEXT_TABLE,
            arrayOf("next_word"),
            selection.toString(),
            arguments.toTypedArray(),
            null,
            null,
            "use_count DESC, last_used DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun normalizedContext(
        language: DictionaryLanguage,
        contextWords: List<String>
    ): List<String> {
        if (contextWords.isEmpty()) return emptyList()
        return contextWords
            .takeLast(MAX_CONTEXT_WORDS)
            .map(language::normalize)
            .filter(String::isNotEmpty)
    }

    private fun bestLegacyNextWord(
        language: DictionaryLanguage,
        previousKey: String,
        prefixKey: String
    ): String? {
        val selection = StringBuilder("language = ? AND previous_key = ?")
        val arguments = arrayListOf(language.code, previousKey)
        if (prefixKey.isNotEmpty()) {
            selection.append(" AND next_key LIKE ? ESCAPE '\\'")
            arguments.add(escapeLike(prefixKey) + "%")
        }

        helper.readableDatabase.query(
            LEGACY_TABLE,
            arrayOf("next_word"),
            selection.toString(),
            arguments.toTypedArray(),
            null,
            null,
            "use_count DESC, last_used DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun escapeLike(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '\\', '%', '_' -> append('\\').append(char)
                    else -> append(char)
                }
            }
        }
    }

    private fun pruneContextIfNeeded(database: SQLiteDatabase) {
        val count = database.rawQuery("SELECT COUNT(*) FROM $CONTEXT_TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (count <= MAX_CONTEXT_ROWS) return
        database.execSQL(
            """
            DELETE FROM $CONTEXT_TABLE
            WHERE rowid IN (
                SELECT rowid FROM $CONTEXT_TABLE
                ORDER BY use_count ASC, last_used ASC
                LIMIT ${count - TARGET_CONTEXT_ROWS}
            )
            """.trimIndent()
        )
    }

    private class Helper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            createLegacyTable(db)
            createContextTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createContextTable(db)
        }

        private fun createLegacyTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $LEGACY_TABLE (
                    language TEXT NOT NULL,
                    previous_key TEXT NOT NULL,
                    next_key TEXT NOT NULL,
                    next_word TEXT NOT NULL,
                    use_count INTEGER NOT NULL DEFAULT 1,
                    last_used INTEGER NOT NULL,
                    PRIMARY KEY (language, previous_key, next_key)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS next_word_lookup " +
                    "ON $LEGACY_TABLE(language, previous_key, use_count DESC)"
            )
        }

        private fun createContextTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $CONTEXT_TABLE (
                    language TEXT NOT NULL,
                    context_key TEXT NOT NULL,
                    context_size INTEGER NOT NULL,
                    next_key TEXT NOT NULL,
                    next_word TEXT NOT NULL,
                    use_count INTEGER NOT NULL DEFAULT 1,
                    last_used INTEGER NOT NULL,
                    PRIMARY KEY (language, context_key, next_key)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS context_next_lookup " +
                    "ON $CONTEXT_TABLE(language, context_key, use_count DESC)"
            )
        }
    }

    companion object {
        private const val DATABASE = "keyboard_local_suggestions.db"
        private const val DATABASE_VERSION = 2
        private const val LEGACY_TABLE = "next_words"
        private const val CONTEXT_TABLE = "context_predictions"
        private const val CONTEXT_SEPARATOR = "\u001F"
        private const val MAX_CONTEXT_WORDS = 2
        private const val MAX_CONTEXT_ROWS = 8_200
        private const val TARGET_CONTEXT_ROWS = 8_000
    }
}
