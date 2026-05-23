package com.souadachak.fixarabickeyboard.keyboard

import android.content.Context

class CoinManager(context: Context) {
    private val prefs = context.getSharedPreferences("fix_arabic_keyboard_wallet", Context.MODE_PRIVATE)

    fun coins(): Int = prefs.getInt(KEY_COINS, DAILY_FREE_COINS)

    fun canSpendFixCoin(): Boolean = coins() > 0 || isPremium()

    fun consumeFixCoinIfNeeded() {
        if (isPremium()) return
        val current = coins()
        if (current > 0) prefs.edit().putInt(KEY_COINS, current - 1).apply()
    }

    fun isPremium(): Boolean = prefs.getBoolean(KEY_PREMIUM, false)

    companion object {
        private const val KEY_COINS = "coins"
        private const val KEY_PREMIUM = "premium"
        private const val DAILY_FREE_COINS = 100
    }
}
