package org.riverbank.shop.privacy

/**
 * Decides which hosts belong to the storefront. Suffix matching is exact on
 * label boundaries, so "amazon-adsystem.com" never matches "amazon.com".
 */
object DomainPolicy {

    /** Storefront and short-link domains that stay inside the app. */
    private val STORE_SUFFIXES = setOf(
        "amazon.com", "amazon.ca", "amazon.co.uk", "amazon.de", "amazon.fr",
        "amazon.it", "amazon.es", "amazon.nl", "amazon.se", "amazon.pl",
        "amazon.com.au", "amazon.co.jp", "amazon.in", "amazon.com.mx",
        "amazon.com.br", "amazon.ae", "amazon.sa", "amazon.eg", "amazon.sg",
        "amazon.com.tr", "amazon.com.be", "amazon.ie", "amazon.cn",
        "a.co", "amzn.to", "amzn.eu", "amzn.asia"
    )

    /** Image and script CDNs the storefront cannot render without. */
    private val ASSET_SUFFIXES = setOf(
        "ssl-images-amazon.com",
        "media-amazon.com",
        "images-amazon.com",
        "amazonpay.com",
        "amazonpay.in"
    )

    fun host(url: String?): String? {
        if (url == null) return null
        return try {
            android.net.Uri.parse(url).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    fun isStoreHost(host: String?): Boolean = matchesSuffix(host, STORE_SUFFIXES)

    fun isAssetHost(host: String?): Boolean = matchesSuffix(host, ASSET_SUFFIXES)

    /** True for anything strict mode is willing to load as a subresource. */
    fun isFirstParty(host: String?): Boolean = isStoreHost(host) || isAssetHost(host)

    private fun matchesSuffix(host: String?, suffixes: Set<String>): Boolean {
        val h = host?.lowercase() ?: return false
        for (suffix in suffixes) {
            if (h == suffix || h.endsWith(".$suffix")) return true
        }
        return false
    }
}
