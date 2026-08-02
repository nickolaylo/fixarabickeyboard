package com.souadachak.fixarabickeyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "openInputMethodSettings" -> {
                    openInputMethodSettings()
                    result.success(true)
                }
                "showInputMethodPicker" -> {
                    showInputMethodPicker()
                    result.success(true)
                }
                "getNumberRowMode" -> {
                    result.success(
                        getSharedPreferences(KEYBOARD_UI_PREFS, Context.MODE_PRIVATE)
                            .getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
                            ?: NUMBER_ROW_PORTRAIT_ONLY
                    )
                }
                "setNumberRowMode" -> {
                    val mode = call.argument<String>("mode")
                    if (mode !in NUMBER_ROW_MODES) {
                        result.error("invalid_number_row_mode", "Unsupported number-row mode", null)
                    } else {
                        getSharedPreferences(KEYBOARD_UI_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(NUMBER_ROW_MODE_KEY, mode)
                            .apply()
                        result.success(true)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun openInputMethodSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun showInputMethodPicker() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    companion object {
        private const val CHANNEL = "fix_arabic_keyboard/input_methods"
        private const val KEYBOARD_UI_PREFS = "keyboard_ui_state"
        private const val NUMBER_ROW_MODE_KEY = "number_row_mode"
        private const val NUMBER_ROW_ALWAYS = "always"
        private const val NUMBER_ROW_PORTRAIT_ONLY = "portrait_only"
        private const val NUMBER_ROW_HIDDEN = "hidden"
        private val NUMBER_ROW_MODES = setOf(
            NUMBER_ROW_ALWAYS,
            NUMBER_ROW_PORTRAIT_ONLY,
            NUMBER_ROW_HIDDEN
        )
    }
}
