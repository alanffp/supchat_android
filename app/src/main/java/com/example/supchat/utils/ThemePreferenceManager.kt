package com.example.supchat.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Gestionnaire des préférences de thème pour l'application.
 * Permet de sauvegarder et d'appliquer les préférences de thème.
 */
class ThemePreferenceManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "SupChatPrefs"
        private const val KEY_THEME = "theme"

        const val THEME_DARK = "sombre"
        const val THEME_LIGHT = "clair"
    }

    /**
     * Sauvegarde la préférence de thème dans les SharedPreferences
     */
    fun saveThemePreference(theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    /**
     * Récupère la préférence de thème depuis les SharedPreferences
     * @return Le thème sauvegardé ou "sombre" par défaut
     */
    fun getThemePreference(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    /**
     * Applique le thème stocké dans les préférences
     */
    fun applyCurrentTheme() {
        when (getThemePreference()) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    /**
     * Applique un thème spécifique
     */
    fun applyTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        saveThemePreference(theme)
    }
}