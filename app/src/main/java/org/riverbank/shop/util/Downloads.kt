package org.riverbank.shop.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.getSystemService

/**
 * Invoices, shipping labels and order PDFs go through the system download
 * manager, which writes into the public Downloads collection. On API 29+ that
 * needs no storage permission at all, so Riverbank asks for none.
 */
object Downloads {

    fun enqueue(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false

        return try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setMimeType(mimeType)
                userAgent?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val manager = context.getSystemService<DownloadManager>() ?: return false
            manager.enqueue(request)
            true
        } catch (_: Exception) {
            false
        }
    }
}
