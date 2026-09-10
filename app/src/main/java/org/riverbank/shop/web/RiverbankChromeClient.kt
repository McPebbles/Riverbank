package org.riverbank.shop.web

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Everything the page asks the "browser chrome" for. Every capability request —
 * camera, microphone, MIDI, protected media, location — is refused without
 * prompting, because the app holds no such permission to hand over in the first
 * place.
 */
class RiverbankChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String?) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    override fun onReceivedTitle(view: WebView, title: String?) = onTitle(title)

    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onPermissionRequestCanceled(request: PermissionRequest) = Unit

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        // Deny, do not remember, do not retain.
        callback?.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean = false

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        // Swallow page console output so nothing from the storefront ends up in
        // logcat, where other apps with log access could read it.
        return true
    }
}
