package org.riverbank.shop.data

/**
 * The storefronts Riverbank knows how to open. The host is the only thing the
 * app stores about your choice; nothing is sent anywhere when you pick one.
 */
enum class Storefront(val id: String, val host: String, val label: String) {
    CA("ca", "www.amazon.ca", "Canada — amazon.ca"),
    US("us", "www.amazon.com", "United States — amazon.com"),
    UK("uk", "www.amazon.co.uk", "United Kingdom — amazon.co.uk"),
    DE("de", "www.amazon.de", "Germany — amazon.de"),
    FR("fr", "www.amazon.fr", "France — amazon.fr"),
    IT("it", "www.amazon.it", "Italy — amazon.it"),
    ES("es", "www.amazon.es", "Spain — amazon.es"),
    NL("nl", "www.amazon.nl", "Netherlands — amazon.nl"),
    SE("se", "www.amazon.se", "Sweden — amazon.se"),
    PL("pl", "www.amazon.pl", "Poland — amazon.pl"),
    AU("au", "www.amazon.com.au", "Australia — amazon.com.au"),
    JP("jp", "www.amazon.co.jp", "Japan — amazon.co.jp"),
    IN("in", "www.amazon.in", "India — amazon.in"),
    MX("mx", "www.amazon.com.mx", "Mexico — amazon.com.mx"),
    BR("br", "www.amazon.com.br", "Brazil — amazon.com.br"),
    AE("ae", "www.amazon.ae", "United Arab Emirates — amazon.ae"),
    SG("sg", "www.amazon.sg", "Singapore — amazon.sg");

    val origin: String get() = "https://$host"

    companion object {
        val DEFAULT = CA

        fun fromId(id: String?): Storefront =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        fun fromHost(host: String?): Storefront? {
            if (host == null) return null
            val h = host.lowercase()
            return entries.firstOrNull { h == it.host || h.endsWith(it.host.removePrefix("www.")) }
        }
    }
}
