package org.riverbank.shop

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.databinding.ActivityMainBinding
import org.riverbank.shop.privacy.BlockStats
import org.riverbank.shop.privacy.DomainPolicy
import org.riverbank.shop.ui.Destination
import org.riverbank.shop.util.Downloads
import org.riverbank.shop.web.BannerFilter
import org.riverbank.shop.web.RiverbankChromeClient
import org.riverbank.shop.web.RiverbankWebViewClient
import org.riverbank.shop.web.WebViewConfigurator
import org.riverbank.shop.web.WebViewHost
import java.net.URLEncoder

class MainActivity : AppCompatActivity(), WebViewHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var lastFailedUrl: String? = null
    private var suppressNavCallback = false
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var errorTemplate: String? = null
    private var pageTitle: String? = null
    private lateinit var bannerFilter: BannerFilter

    /** False while the activity is finishing straight into onboarding. */
    private var viewsReady = false

    /** True while the toolbar is showing the search field instead of the title. */
    private var searchMode = false

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (!prefs.hasChosenStorefront) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewsReady = true

        applySecureFlag()
        setUpWebView()
        setUpToolbar()
        setUpBottomNav()
        setUpSearch()
        setUpBackHandling()

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            val fromIntent = urlFromIntent(intent)
            loadUrl(fromIntent ?: prefs.storefront.origin)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!viewsReady) return
        setIntent(intent)
        urlFromIntent(intent)?.let { loadUrl(it) }
    }

    override fun onStart() {
        super.onStart()
        if (!viewsReady) return
        if (prefs.appLock && AppLock.needsUnlock(prefs.lockTimeoutSeconds)) {
            binding.root.visibility = View.INVISIBLE
            startActivity(Intent(this, LockActivity::class.java))
        } else {
            binding.root.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (!viewsReady) return
        applySecureFlag()
        binding.webView.onResume()
        WebViewConfigurator.applyCookiePolicy(binding.webView, prefs)
        bannerFilter.sync(binding.webView, prefs.hideAppBanners)
        syncDesktopMenuState()
    }

    override fun onPause() {
        if (!viewsReady) {
            super.onPause()
            return
        }
        binding.webView.onPause()
        BlockStats.persist(prefs)
        AppLock.markBackgrounded()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (viewsReady) binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        if (!viewsReady) {
            super.onDestroy()
            return
        }
        if (isFinishing) {
            WebViewConfigurator.clearBrowsingData(
                binding.webView,
                cookies = prefs.clearCookiesOnExit,
                cache = prefs.clearCacheOnExit
            )
            AppLock.lock()
        }
        bannerFilter.release()
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun applySecureFlag() {
        if (prefs.appLock) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // ------------------------------------------------------------------- setup

    private fun setUpWebView() {
        WebViewConfigurator.apply(binding.webView, prefs)
        bannerFilter = BannerFilter(this)
        bannerFilter.sync(binding.webView, prefs.hideAppBanners)

        binding.webView.webViewClient = RiverbankWebViewClient(prefs, this)
        binding.webView.webChromeClient = RiverbankChromeClient(
            onProgress = { progress ->
                binding.progress.progress = progress
                binding.progress.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
            },
            onTitle = { title ->
                pageTitle = title?.trim()?.takeIf { it.isNotEmpty() }
                updateToolbarTitle()
            },
            onFileChooser = { callback, params ->
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    pendingFileCallback = null
                    false
                }
            }
        )

        binding.webView.setDownloadListener { url, userAgent, disposition, mime, _ ->
            val started = Downloads.enqueue(this, url, userAgent, disposition, mime)
            toast(if (started) R.string.download_started else R.string.download_failed)
        }

        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }
        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.swipeRefresh.isEnabled = scrollY == 0
        }
    }

    private fun setUpToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (searchMode) exitSearchMode() else navigateBack()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> { enterSearchMode(); true }
                R.id.action_refresh -> { binding.webView.reload(); true }
                R.id.action_share -> { shareCurrentPage(); true }
                R.id.action_open_external -> {
                    binding.webView.url?.let { openExternally(it.toUri()) }
                    true
                }
                R.id.action_desktop_site -> {
                    prefs.desktopSite = !prefs.desktopSite
                    syncDesktopMenuState()
                    WebViewConfigurator.apply(binding.webView, prefs)
                    binding.webView.reload()
                    true
                }
                R.id.action_end_session -> { endSession(); true }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        syncDesktopMenuState()
    }

    private fun syncDesktopMenuState() {
        binding.toolbar.menu.findItem(R.id.action_desktop_site)?.isChecked = prefs.desktopSite
    }

    private fun setUpBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavCallback) return@setOnItemSelectedListener true
            val destination = Destination.forMenuId(item.itemId)
            if (destination != null) {
                loadUrl(destination.url(prefs.storefront))
                true
            } else {
                false
            }
        }
        binding.bottomNav.setOnItemReselectedListener {
            binding.webView.scrollTo(0, 0)
        }
    }

    private fun setUpSearch() {
        binding.searchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = view.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    loadUrl("${prefs.storefront.origin}/s?k=$encoded")
                }
                exitSearchMode()
                true
            } else {
                false
            }
        }
    }

    private fun enterSearchMode() {
        if (searchMode) return
        searchMode = true
        binding.searchBar.visibility = View.VISIBLE
        binding.toolbar.title = null
        binding.toolbar.subtitle = null
        binding.toolbar.setNavigationIcon(R.drawable.ic_close)
        binding.toolbar.navigationContentDescription = getString(R.string.action_close_search)
        binding.searchInput.setSelection(binding.searchInput.text?.length ?: 0)
        binding.searchInput.post {
            binding.searchInput.requestFocus()
            getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun exitSearchMode() {
        if (!searchMode) return
        searchMode = false
        binding.searchBar.visibility = View.GONE
        binding.searchInput.clearFocus()
        hideKeyboard()
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        binding.toolbar.navigationContentDescription = getString(R.string.action_back)
        updateToolbarTitle()
    }

    /**
     * The toolbar shows the page title, and — only when the page is not on the
     * storefront — the host underneath it, so an off-site payment or sign-in
     * redirect is never disguised as the store.
     */
    private fun updateToolbarTitle() {
        if (searchMode) return
        binding.toolbar.title = pageTitle ?: getString(R.string.title_loading)
        val host = try {
            binding.webView.url?.toUri()?.host
        } catch (_: Exception) {
            null
        }
        binding.toolbar.subtitle =
            if (host != null && !DomainPolicy.isStoreHost(host)) host else null
    }

    /** One implementation for the toolbar arrow, the gesture and the key. */
    private fun navigateBack() {
        when {
            searchMode -> exitSearchMode()
            binding.webView.canGoBack() -> binding.webView.goBack()
            else -> finish()
        }
    }

    private fun setUpBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    searchMode -> exitSearchMode()
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // -------------------------------------------------------------- navigation

    private fun loadUrl(url: String) {
        lastFailedUrl = null
        binding.webView.loadUrl(url, WebViewConfigurator.requestHeaders(prefs))
    }

    private fun urlFromIntent(intent: Intent?): String? {
        if (intent == null) return null

        val destinationId = intent.getStringExtra("destination")
        if (destinationId == "search") {
            enterSearchMode()
            return prefs.storefront.origin
        }
        val shortcutDestination = Destination.forId(destinationId)
        if (shortcutDestination != null) return shortcutDestination.url(prefs.storefront)

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null && DomainPolicy.isStoreHost(uri.host)) return uri.toString()
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val match = Regex("https?://\\S+").find(text)?.value
                if (match != null && DomainPolicy.isStoreHost(match.toUri().host)) return match
                if (text.isNotBlank()) {
                    val encoded = URLEncoder.encode(text.trim().take(120), "UTF-8")
                    return "${prefs.storefront.origin}/s?k=$encoded"
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------ WebViewHost

    override fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(
                this,
                getString(R.string.external_blocked, uri.host ?: uri.toString()),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: ActivityNotFoundException) {
            toast(R.string.no_browser)
        }
    }

    override fun retryLastPage() {
        val url = lastFailedUrl ?: prefs.storefront.origin
        loadUrl(url)
    }

    override fun onNavigation(url: String?) {
        val path = try {
            url?.toUri()?.path
        } catch (_: Exception) {
            null
        }
        val destination = Destination.forUrl(path)
        if (destination != null && binding.bottomNav.selectedItemId != destination.menuId) {
            suppressNavCallback = true
            binding.bottomNav.selectedItemId = destination.menuId
            suppressNavCallback = false
        }
        // Reflect the search terms of a results page back into the search box.
        val query = try {
            url?.toUri()?.getQueryParameter("k")
        } catch (_: Exception) {
            null
        }
        if (query != null && !binding.searchInput.hasFocus()) {
            binding.searchInput.setText(query)
        }
        updateToolbarTitle()
    }

    override fun onLoadStarted() {
        binding.progress.visibility = View.VISIBLE
        // Drop the previous page's title so the bar never mislabels what is
        // on screen while the next page loads.
        pageTitle = null
        updateToolbarTitle()
        bannerFilter.onPageEvent(binding.webView, prefs.hideAppBanners)
    }

    override fun onLoadFinished() {
        binding.progress.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        updateToolbarTitle()
        bannerFilter.onPageEvent(binding.webView, prefs.hideAppBanners)
    }

    override fun showErrorPage(view: WebView, failingUrl: String, title: String, message: String) {
        lastFailedUrl = failingUrl
        var template = errorTemplate
        if (template == null) {
            template = assets.open("error.html").bufferedReader().use { reader -> reader.readText() }
            errorTemplate = template
        }
        val html = template
            .replace("__TITLE__", escapeHtml(title))
            .replace("__MESSAGE__", escapeHtml(message))
            .replace("__URL__", escapeHtml(failingUrl))
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    // ------------------------------------------------------------------ misc

    private fun endSession() {
        WebViewConfigurator.clearBrowsingData(binding.webView, cookies = true, cache = true)
        toast(R.string.session_cleared)
        loadUrl(prefs.storefront.origin)
    }

    private fun shareCurrentPage() {
        val url = binding.webView.url ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private fun hideKeyboard() {
        getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
