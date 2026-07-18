package com.souadachak.fixarabickeyboard.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Stores only safe word-to-word counts locally on the device. */
class LocalNextWordStore(context: Context) {
    private val helper = Helper(context.applicationContext)

    fun record(language: DictionaryLanguage, previousWord: String, nextWord: String) {
        val previousKey = language.normalize(previousWord)
        val nextKey = language.normalize(nextWord)
        if (previousKey.isEmpty() || nextKey.isEmpty()) return

        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("language", language.code)
                put("previous_key", previousKey)
                put("next_key", nextKey)
                put("next_word", nextWord)
                put("use_count", 1)
                put("last_used", System.currentTimeMillis())
            }
            val inserted = database.insertWithOnConflict(
                TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
            if (inserted == -1L) {
                database.execSQL(
                    """
                    UPDATE $TABLE
                    SET use_count = use_count + 1,
                        next_word = ?,
                        last_used = ?
                    WHERE language = ? AND previous_key = ? AND next_key = ?
                    """.trimIndent(),
                    arrayOf(nextWord, System.currentTimeMillis(), language.code, previousKey, nextKey)
                )
            }
            pruneIfNeeded(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun bestNextWord(language: DictionaryLanguage, previousWord: String): String? {
        val previousKey = language.normalize(previousWord)
        if (previousKey.isEmpty()) return null
        helper.readableDatabase.query(
            TABLE,
            arrayOf("next_word"),
            "language = ? AND previous_key = ?",
            arrayOf(language.code, previousKey),
            null,
            null,
            "use_count DESC, last_used DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun clearAll() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    private fun pruneIfNeeded(database: SQLiteDatabase) {
        val count = database.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (count <= MAX_ROWS) return
        database.execSQL(
            """
            DELETE FROM $TABLE
            WHERE rowid IN (
                SELECT rowid FROM $TABLE
                ORDER BY use_count ASC, last_used ASC
                LIMIT ${count - TARGET_ROWS}
            )
            """.trimIndent()
        )
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, DATABASE, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
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
            db.execSQL("CREATE INDEX next_word_lookup ON $TABLE(language, previous_key, use_count DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DATABASE = "keyboard_local_suggestions.db"
        private const val TABLE = "next_words"
        private const val MAX_ROWS = 5_200
        private const val TARGET_ROWS = 5_000
    }
}
