package org.riverbank.shop.web

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.riverbank.shop.R
import org.riverbank.shop.data.Storefront

/**
 * Hides the storefront's "get the app" banners and interstitials.
 *
 * The banner is markup the storefront renders itself, not a request that can be
 * blocked by host — so the only thing that removes it is cosmetic filtering
 * inside the page, which is what every content blocker does.
 *
 * This injects a script *into* the page. That is one-directional: the page
 * gains nothing. It is not [android.webkit.WebView.addJavascriptInterface],
 * which is the thing this project refuses to use, because that would expose
 * Android objects *to* the page.
 *
 * Where the WebView supports it — Vanadium does — the script is registered as a
 * document-start script, so it runs before the storefront's own JavaScript and
 * the banner never gets a frame on screen. Otherwise it is evaluated on each
 * page event, which works but can flash briefly.
 */
class BannerFilter(private val context: Context) {

    private var cachedScript: String? = null
    private var handle: ScriptHandler? = null

    private fun script(): String {
        cachedScript?.let { return it }
        val loaded = context.resources.openRawResource(R.raw.banner_filter)
            .bufferedReader()
            .use { reader -> reader.readText() }
        cachedScript = loaded
        return loaded
    }

    /** True once a document-start registration is live. */
    val injectsAtDocumentStart: Boolean get() = handle != null

    /**
     * Bring the registration in line with the current setting. Safe to call
     * repeatedly; it is a no-op when nothing has changed.
     */
    fun sync(webView: WebView, enabled: Boolean) {
        if (!enabled) {
            handle?.let { runCatching { it.remove() } }
            handle = null
            return
        }
        if (handle != null) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        handle = runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script(), ORIGIN_RULES)
        }.getOrNull()
    }

    /**
     * Fallback path for WebView implementations without document-start scripts.
     * The script guards itself against running twice, so calling this on both
     * page-started and page-finished costs nothing.
     */
    fun onPageEvent(webView: WebView, enabled: Boolean) {
        if (!enabled || handle != null) return
        runCatching { webView.evaluateJavascript(script(), null) }
    }

    fun release() {
        handle?.let { runCatching { it.remove() } }
        handle = null
    }

    private companion object {
        /**
         * Only storefront origins get the script. Built from the storefront
         * list so adding a region cannot leave a gap here.
         */
        val ORIGIN_RULES: Set<String> = buildSet {
            for (storefront in Storefront.entries) {
                val apex = storefront.host.removePrefix("www.")
                add("https://$apex")
                add("https://*.$apex")
            }
        }
    }
}
