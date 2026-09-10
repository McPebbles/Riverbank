package org.riverbank.shop.privacy

import android.content.Context
import org.riverbank.shop.R
import java.io.ByteArrayInputStream

/**
 * A static host blocklist compiled into the APK. It is read once from
 * res/raw/blocklist.txt and never updated over the network, so the app makes no
 * requests the user did not ask for.
 */
object Blocklist {

    @Volatile
    private var hosts: Set<String> = emptySet()

    fun load(context: Context) {
        if (hosts.isNotEmpty()) return
        val parsed = HashSet<String>(256)
        context.resources.openRawResource(R.raw.blocklist).bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                parsed.add(line.lowercase())
            }
        }
        hosts = parsed
    }

    /** Matches the host itself and every subdomain of a listed host. */
    fun matches(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        val set = hosts
        if (set.isEmpty()) return false
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false
        }
    }

    /**
     * The response handed back for a blocked request: HTTP 204, no body, no
     * headers. Chromium treats it as "nothing here" and moves on quietly.
     */
    fun emptyResponse(): android.webkit.WebResourceResponse =
        android.webkit.WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
}
