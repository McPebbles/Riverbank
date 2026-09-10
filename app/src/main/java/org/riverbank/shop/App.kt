package org.riverbank.shop

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.privacy.BlockStats
import org.riverbank.shop.privacy.Blocklist

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        Blocklist.load(this)
        BlockStats.attach(prefs)
        applyTheme(prefs.darkMode)
    }

    companion object {
        fun applyTheme(mode: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
