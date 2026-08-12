package me.sourov.quicksale.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProxyTest {

    private val proxy = "https://testsite.example.com/proxy"

    @Test
    fun `rewrites the origin of an internal media url`() {
        assertEquals(
            "https://testsite.example.com/proxy/wp-content/uploads/2026/08/x.webp",
            "http://testshop.local/wp-content/uploads/2026/08/x.webp".throughSiteProxy(proxy),
        )
    }

    @Test
    fun `carries the query and fragment across`() {
        assertEquals(
            "https://testsite.example.com/proxy/img.png?w=200&h=100#top",
            "http://testshop.local/img.png?w=200&h=100#top".throughSiteProxy(proxy),
        )
    }

    @Test
    fun `leaves urls alone when the site is not proxied`() {
        val url = "http://testshop.local/wp-content/uploads/x.webp"
        assertEquals(url, url.throughSiteProxy("https://shop.example.com"))
    }

    @Test
    fun `is idempotent so a resync does not double up`() {
        val proxied = "https://testsite.example.com/proxy/wp-content/uploads/x.webp"
        assertEquals(proxied, proxied.throughSiteProxy(proxy))
    }

    @Test
    fun `ignores a trailing slash on the site url`() {
        assertEquals(
            "https://testsite.example.com/proxy/a.webp",
            "http://testshop.local/a.webp".throughSiteProxy("$proxy/"),
        )
    }

    @Test
    fun `leaves anything it cannot route untouched`() {
        // Relative, schemeless, an origin with no path, a scheme that isn't http, and blank.
        assertEquals("/wp-content/x.webp", "/wp-content/x.webp".throughSiteProxy(proxy))
        assertEquals("//testshop.local/x.webp", "//testshop.local/x.webp".throughSiteProxy(proxy))
        assertEquals("http://testshop.local", "http://testshop.local".throughSiteProxy(proxy))
        assertEquals("data:image/png;base64,AA", "data:image/png;base64,AA".throughSiteProxy(proxy))
        assertEquals("", "".throughSiteProxy(proxy))
    }
}
