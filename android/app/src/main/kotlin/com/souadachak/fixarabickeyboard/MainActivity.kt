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
                        keyboardPreferences()
                            .getString(NUMBER_ROW_MODE_KEY, NUMBER_ROW_PORTRAIT_ONLY)
                            ?: NUMBER_ROW_PORTRAIT_ONLY
                    )
                }
                "setNumberRowMode" -> {
                    val mode = call.argument<String>("mode")
                    if (mode == null || mode !in NUMBER_ROW_MODES) {
                        result.error(
                            "invalid_number_row_mode",
                            "Unsupported number-row mode",
                            null
                        )
                    } else {
                        keyboardPreferences()
                            .edit()
                            .putString(NUMBER_ROW_MODE_KEY, mode)
                            .apply()
                        result.success(true)
                    }
                }
                "getKeyboardAppearance" -> {
                    val preferences = keyboardPreferences()
                    result.success(
                        mapOf(
                            "keyboardHeightPercent" to preferences.getInt(
                                KEYBOARD_HEIGHT_PERCENT_KEY,
                                DEFAULT_KEYBOARD_HEIGHT_PERCENT
                            ),
                            "letterSizePercent" to preferences.getInt(
                                LETTER_SIZE_PERCENT_KEY,
                                DEFAULT_LETTER_SIZE_PERCENT
                            ),
                            "bottomSpacingDp" to preferences.getInt(
                                BOTTOM_SPACING_DP_KEY,
                                DEFAULT_BOTTOM_SPACING_DP
                            ),
                            "keyBordersEnabled" to preferences.getBoolean(
                                KEY_BORDERS_ENABLED_KEY,
                                true
                            )
                        )
                    )
                }
                "setKeyboardAppearance" -> {
                    val keyboardHeightPercent = call.argument<Int>("keyboardHeightPercent")
                    val letterSizePercent = call.argument<Int>("letterSizePercent")
                    val bottomSpacingDp = call.argument<Int>("bottomSpacingDp")
                    val keyBordersEnabled = call.argument<Boolean>("keyBordersEnabled")

                    val valid = keyboardHeightPercent != null &&
                        keyboardHeightPercent in MIN_KEYBOARD_HEIGHT_PERCENT..MAX_KEYBOARD_HEIGHT_PERCENT &&
                        letterSizePercent != null &&
                        letterSizePercent in MIN_LETTER_SIZE_PERCENT..MAX_LETTER_SIZE_PERCENT &&
                        bottomSpacingDp != null &&
                        bottomSpacingDp in MIN_BOTTOM_SPACING_DP..MAX_BOTTOM_SPACING_DP &&
                        keyBordersEnabled != null

                    if (!valid) {
                        result.error(
                            "invalid_keyboard_appearance",
                            "Unsupported keyboard appearance values",
                            null
                        )
                    } else {
                        keyboardPreferences()
                            .edit()
                            .putInt(KEYBOARD_HEIGHT_PERCENT_KEY, keyboardHeightPercent!!)
                            .putInt(LETTER_SIZE_PERCENT_KEY, letterSizePercent!!)
                            .putInt(BOTTOM_SPACING_DP_KEY, bottomSpacingDp!!)
                            .putBoolean(KEY_BORDERS_ENABLED_KEY, keyBordersEnabled!!)
                            .apply()
                        result.success(true)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun keyboardPreferences() =
        getSharedPreferences(KEYBOARD_UI_PREFS, Context.MODE_PRIVATE)

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

        private const val KEYBOARD_HEIGHT_PERCENT_KEY = "keyboard_height_percent"
        private const val LETTER_SIZE_PERCENT_KEY = "letter_size_percent"
        private const val BOTTOM_SPACING_DP_KEY = "bottom_spacing_dp"
        private const val KEY_BORDERS_ENABLED_KEY = "key_borders_enabled"
        private const val DEFAULT_KEYBOARD_HEIGHT_PERCENT = 100
        private const val DEFAULT_LETTER_SIZE_PERCENT = 100
        private const val DEFAULT_BOTTOM_SPACING_DP = 0
        private const val MIN_KEYBOARD_HEIGHT_PERCENT = 85
        private const val MAX_KEYBOARD_HEIGHT_PERCENT = 115
        private const val MIN_LETTER_SIZE_PERCENT = 85
        private const val MAX_LETTER_SIZE_PERCENT = 120
        private const val MIN_BOTTOM_SPACING_DP = 0
        private const val MAX_BOTTOM_SPACING_DP = 24
    }
}
