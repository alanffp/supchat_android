package com.example.supchat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Charger la préférence de thème
        val isDarkMode = getSharedPreferences("SupChatPrefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        // Appliquer le thème
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}