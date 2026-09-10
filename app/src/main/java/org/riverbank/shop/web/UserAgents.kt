package org.riverbank.shop.web

/**
 * Fingerprinting defence. The stock WebView user agent leaks the exact device
 * model, the build fingerprint and the "; wv" token that marks the traffic as
 * coming from an embedded browser. Riverbank replaces it with the same reduced
 * user agent desktop and mobile Chrome send, keeping only the Chrome major
 * version taken from the WebView actually installed (Vanadium on GrapheneOS).
 */
object UserAgents {

    private const val FALLBACK_MAJOR = "140"

    private fun majorVersion(defaultUa: String?): String =
        Regex("Chrome/(\\d+)").find(defaultUa.orEmpty())?.groupValues?.getOrNull(1)
            ?: FALLBACK_MAJOR

    fun mobile(defaultUa: String?): String {
        val major = majorVersion(defaultUa)
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }

    fun desktop(defaultUa: String?): String {
        val major = majorVersion(defaultUa)
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }
}
