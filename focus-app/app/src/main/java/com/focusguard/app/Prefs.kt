package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences

/** Переключатели на главном экране приложения. */
object Prefs {
    const val BLOCK_REELS_TAB = "block_reels_tab"
    const val SINGLE_REEL = "single_reel"
    const val CUSTOM_NAV = "custom_nav"
    const val DEBUG_BUTTON = "debug_button"

    private val defaults = mapOf(
        BLOCK_REELS_TAB to true,
        SINGLE_REEL to true,
        CUSTOM_NAV to true,
        DEBUG_BUTTON to false,
    )

    fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean =
        sp(context).getBoolean(key, defaults[key] ?: false)

    fun set(context: Context, key: String, value: Boolean) {
        sp(context).edit().putBoolean(key, value).apply()
    }
}
