package com.wizzwuz.quicktiles.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED Dark")
}

object ThemeManager {
    private const val PREFS_NAME = "quick_tiles_prefs"
    private const val KEY_THEME = "app_theme_mode"

    private val _themeFlow = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeFlow: StateFlow<AppThemeMode> = _themeFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        _themeFlow.value = try {
            AppThemeMode.valueOf(saved)
        } catch (e: Throwable) {
            AppThemeMode.SYSTEM
        }
        isInitialized = true
    }

    fun setTheme(context: Context, mode: AppThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeFlow.value = mode
    }

    fun getTheme(): AppThemeMode = _themeFlow.value
}
