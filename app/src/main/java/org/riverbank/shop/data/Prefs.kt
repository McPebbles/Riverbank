package org.riverbank.shop.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Blocking strength, chosen in Settings. */
enum class BlockingMode { OFF, STANDARD, STRICT;
    companion object {
        fun fromId(id: String?): BlockingMode = when (id) {
            "off" -> OFF
            "strict" -> STRICT
            else -> STANDARD
        }
    }
}

/**
 * Every user-visible setting lives in one plain SharedPreferences file inside
 * the app's private storage. Nothing is synced and backups are disabled in the
 * manifest, so this file never leaves the device.
 */
class Prefs(context: Context) {

    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var storefront: Storefront
        get() = Storefront.fromId(sp.getString(KEY_STOREFRONT, null))
        set(value) = sp.edit().putString(KEY_STOREFRONT, value.id).apply()

    val hasChosenStorefront: Boolean
        get() = sp.getString(KEY_STOREFRONT, null) != null

    val blockingMode: BlockingMode
        get() = BlockingMode.fromId(sp.getString(KEY_BLOCKING, "standard"))

    val blockThirdPartyCookies: Boolean
        get() = sp.getBoolean(KEY_THIRD_PARTY_COOKIES, true)

    val sendGpc: Boolean
        get() = sp.getBoolean(KEY_GPC, true)

    val hideAppBanners: Boolean
        get() = sp.getBoolean(KEY_HIDE_APP_BANNERS, true)

    val clearCookiesOnExit: Boolean
        get() = sp.getBoolean(KEY_CLEAR_COOKIES, false)

    val clearCacheOnExit: Boolean
        get() = sp.getBoolean(KEY_CLEAR_CACHE, false)

    val appLock: Boolean
        get() = sp.getBoolean(KEY_APP_LOCK, false)

    fun disableAppLock() = sp.edit().putBoolean(KEY_APP_LOCK, false).apply()

    /** Seconds of background time before the app re-locks; -1 means never. */
    val lockTimeoutSeconds: Long
        get() = sp.getString(KEY_LOCK_TIMEOUT, "60")?.toLongOrNull() ?: 60L

    var desktopSite: Boolean
        get() = sp.getBoolean(KEY_DESKTOP, false)
        set(value) = sp.edit().putBoolean(KEY_DESKTOP, value).apply()

    val openExternalLinks: Boolean
        get() = sp.getBoolean(KEY_EXTERNAL_LINKS, true)

    val darkMode: String
        get() = sp.getString(KEY_DARK, "system") ?: "system"

    var blockedCount: Int
        get() = sp.getInt(KEY_BLOCKED_COUNT, 0)
        set(value) = sp.edit().putInt(KEY_BLOCKED_COUNT, value).apply()

    companion object {
        const val KEY_STOREFRONT = "storefront"
        const val KEY_BLOCKING = "blocking_mode"
        const val KEY_THIRD_PARTY_COOKIES = "block_third_party_cookies"
        const val KEY_GPC = "send_gpc"
        const val KEY_HIDE_APP_BANNERS = "hide_app_banners"
        const val KEY_CLEAR_COOKIES = "clear_cookies_on_exit"
        const val KEY_CLEAR_CACHE = "clear_cache_on_exit"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_LOCK_TIMEOUT = "lock_timeout"
        const val KEY_DESKTOP = "desktop_site"
        const val KEY_EXTERNAL_LINKS = "external_links"
        const val KEY_DARK = "dark_mode"
        const val KEY_BLOCKED_COUNT = "blocked_count"
    }
}
