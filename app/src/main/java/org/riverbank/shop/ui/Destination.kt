package org.riverbank.shop.ui

import org.riverbank.shop.R
import org.riverbank.shop.data.Storefront

/**
 * The five places the bottom bar can take you, mapped to the storefront's own
 * mobile URLs. Nothing here is scraped or re-implemented: the app simply
 * navigates to the same pages the site serves a mobile browser.
 */
enum class Destination(val menuId: Int, val path: String, val pathMarkers: List<String>) {
    HOME(R.id.nav_home, "/", emptyList()),
    CART(R.id.nav_cart, "/gp/cart/view.html?ref_=nav_cart", listOf("/gp/cart", "/cart")),
    ORDERS(R.id.nav_orders, "/gp/css/order-history?ref_=nav_orders", listOf("/order-history", "/gp/css/order", "/your-orders")),
    LISTS(R.id.nav_lists, "/hz/wishlist/ls?ref_=nav_wishlist", listOf("/hz/wishlist", "/gp/registry")),
    ACCOUNT(R.id.nav_account, "/gp/css/homepage.html?ref_=nav_youraccount", listOf("/gp/css/homepage", "/gp/your-account", "/gp/help"));

    fun url(storefront: Storefront): String = storefront.origin + path

    companion object {
        fun forMenuId(id: Int): Destination? = entries.firstOrNull { it.menuId == id }

        fun forId(id: String?): Destination? = when (id?.lowercase()) {
            "cart" -> CART
            "orders" -> ORDERS
            "lists" -> LISTS
            "account" -> ACCOUNT
            "home" -> HOME
            else -> null
        }

        /** Best-effort mapping of the current URL back onto a bottom bar item. */
        fun forUrl(path: String?): Destination? {
            val p = path?.lowercase() ?: return null
            for (d in entries) {
                if (d.pathMarkers.any { p.startsWith(it) }) return d
            }
            return if (p == "/" || p.isEmpty()) HOME else null
        }
    }
}
