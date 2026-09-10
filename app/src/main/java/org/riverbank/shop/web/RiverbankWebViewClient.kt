package org.riverbank.shop.web

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.riverbank.shop.data.BlockingMode
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.privacy.BlockStats
import org.riverbank.shop.privacy.Blocklist
import org.riverbank.shop.privacy.DomainPolicy

/** Hook for the host activity. */
interface WebViewHost {
    fun openExternally(uri: Uri)
    fun retryLastPage()
    fun onNavigation(url: String?)
    fun onLoadStarted()
    fun onLoadFinished()
    fun showErrorPage(view: WebView, failingUrl: String, title: String, message: String)
}

/** The sentinel host used by the built-in error page's "Try again" button. */
private const val RETRY_HOST = "riverbank.invalid"

/**
 * Schemes whose only purpose is to hand the user off to the native app or to an
 * app store. Riverbank exists so that does not happen, so they are swallowed
 * rather than passed to the system. mailto:, tel: and sms: are not in this set
 * and still open normally.
 */
private val APP_HANDOFF_SCHEMES = setOf(
    "intent",
    "market",
    "amzn",
    "android-app",
    "com.amazon.mobile.shopping",
    "com.amazon.mobile.shopping.web"
)

class RiverbankWebViewClient(
    private val prefs: Prefs,
    private val host: WebViewHost
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        val target = uri.host?.lowercase()

        // The offline page's retry link.
        if (target == RETRY_HOST) {
            host.retryLastPage()
            return true
        }

        // An app-store or native-app hand-off. Do nothing at all: following it
        // would open the very app this one replaces.
        if (scheme != null && scheme in APP_HANDOFF_SCHEMES) return true

        // mailto:, tel:, sms: and anything else non-web is handed to the system
        // rather than being loaded inside the session.
        if (scheme != "http" && scheme != "https") {
            host.openExternally(uri)
            return true
        }

        // Anything on the storefront stays in the app.
        if (DomainPolicy.isStoreHost(target)) return false

        // A navigation the user did not tap is a redirect: sign-in hand-offs,
        // 3-D Secure bank challenges and payment provider round trips all look
        // like this. Booting them to a browser would break checkout, so they
        // stay in the session and the toolbar shows the off-site host.
        if (!request.hasGesture() || request.isRedirect) return false

        return if (prefs.openExternalLinks) {
            host.openExternally(uri)
            true
        } else {
            false
        }
    }

    /**
     * Runs on a background thread for every subresource. Main-frame navigations
     * are never dropped here — that decision belongs to
     * [shouldOverrideUrlLoading] — so blocking can never strand the user on a
     * blank page.
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (request.isForMainFrame) return null

        val target = request.url.host?.lowercase() ?: return null

        val blocked = when (prefs.blockingMode) {
            BlockingMode.OFF -> false
            BlockingMode.STANDARD -> Blocklist.matches(target)
            BlockingMode.STRICT -> Blocklist.matches(target) || !DomainPolicy.isFirstParty(target)
        }

        if (!blocked) return null
        BlockStats.record()
        return Blocklist.emptyResponse()
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        host.onLoadStarted()
        host.onNavigation(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        host.onLoadFinished()
        host.onNavigation(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        host.onNavigation(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (!request.isForMainFrame) return
        val message = when (error.errorCode) {
            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_IO, ERROR_TIMEOUT ->
                "Riverbank could not reach the storefront."
            ERROR_UNSUPPORTED_SCHEME ->
                "That link cannot be opened here."
            else -> error.description?.toString() ?: "The page could not be loaded."
        }
        host.showErrorPage(view, request.url.toString(), "Can't load the page", message)
    }
}
