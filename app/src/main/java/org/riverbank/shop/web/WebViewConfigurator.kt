package org.riverbank.shop.web

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.riverbank.shop.BuildConfig
import org.riverbank.shop.data.Prefs

/**
 * One place that decides everything the embedded browser is allowed to do.
 *
 * On GrapheneOS the WebView provider is Vanadium, so these settings are applied
 * to a Chromium build that already ships with Google services, Safe Browsing
 * reporting and metrics removed. Nothing here depends on Vanadium being the
 * provider — on any other Android build the same hardening applies to whatever
 * WebView is installed — but Vanadium is what makes the result airtight.
 */
object WebViewConfigurator {

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, prefs: Prefs) {
        val s = webView.settings

        // The storefront is a JavaScript application; without this there is no
        // cart, no search suggestions and no checkout.
        s.javaScriptEnabled = true
        s.domStorageEnabled = true

        // Layout and zoom, matching what a normal mobile browser does.
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.textZoom = 100

        // --- Attack surface removal -------------------------------------
        // No access to the app's own files or content providers from web code.
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.allowFileAccessFromFileURLs = false
        s.allowUniversalAccessFromFileURLs = false
        // Pop-ups and extra windows are refused outright.
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        // Never downgrade a secure page to plaintext subresources.
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Media cannot start on its own, so no autoplaying ad video.
        s.mediaPlaybackRequiresUserGesture = true
        // Geolocation is denied at the settings level as well as in the
        // chrome client, and the app holds no location permission anyway.
        @Suppress("DEPRECATION")
        s.setGeolocationEnabled(false)
        // Safe Browsing would hash and send visited URLs to a remote service.
        s.safeBrowsingEnabled = false
        // Saving form data is the browser's own autofill store; keep it off.
        @Suppress("DEPRECATION")
        s.saveFormData = false

        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = if (prefs.desktopSite) {
            UserAgents.desktop(s.userAgentString)
        } else {
            UserAgents.mobile(s.userAgentString)
        }

        // Honour the system dark theme inside the page where the WebView
        // implementation supports it.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, true)
        }

        // Remote debugging is a full remote-control channel. Debug builds only.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER

        applyCookiePolicy(webView, prefs)
    }

    fun applyCookiePolicy(webView: WebView, prefs: Prefs) {
        val cm = CookieManager.getInstance()
        // First-party cookies are what keeps you signed in and your cart alive.
        cm.setAcceptCookie(true)
        // Third-party cookies are the cross-site tracking channel. Off by default.
        cm.setAcceptThirdPartyCookies(webView, !prefs.blockThirdPartyCookies)
    }

    /** Headers added to every top-level page load. */
    fun requestHeaders(prefs: Prefs): Map<String, String> =
        if (prefs.sendGpc) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    fun clearBrowsingData(webView: WebView?, cookies: Boolean, cache: Boolean) {
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
        if (cache) {
            webView?.clearCache(true)
            webView?.clearFormData()
            webView?.clearHistory()
        }
    }
}
