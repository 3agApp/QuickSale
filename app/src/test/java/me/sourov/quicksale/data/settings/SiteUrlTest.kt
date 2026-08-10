package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteUrlTest {

    @Test
    fun `a bare host is left alone`() {
        assertEquals("shop.example", "shop.example".toSiteHostInput())
    }

    @Test
    fun `a pasted url loses its scheme`() {
        assertEquals("shop.example", "https://shop.example".toSiteHostInput())
        assertEquals("shop.example", "http://shop.example".toSiteHostInput())
        assertEquals("shop.example", "HTTPS://shop.example".toSiteHostInput())
    }

    /**
     * The regression this function exists for. The scheme used to live inside the text field, and
     * backspacing it to "https:/" no longer matched the "already has a scheme" test — so a fresh
     * "https://" was prepended on every keystroke and the field filled with repeated prefixes.
     */
    @Test
    fun `repeated schemes are all peeled off`() {
        assertEquals(
            "shop.example",
            "https://https://https://shop.example".toSiteHostInput(),
        )
        assertEquals("", "https://https://".toSiteHostInput())
    }

    @Test
    fun `a path separator after the scheme is not mistaken for the host`() {
        assertEquals("shop.example", "https:///shop.example".toSiteHostInput())
    }

    @Test
    fun `normalizing produces the canonical https form`() {
        assertEquals("https://shop.example", normalizeHttpsSiteUrl("shop.example"))
        assertEquals("https://shop.example", normalizeHttpsSiteUrl("https://shop.example/"))
        assertEquals("https://shop.example", normalizeHttpsSiteUrl("  http://shop.example  "))
    }

    @Test
    fun `a subdirectory install keeps its path`() {
        assertEquals("https://example.com/shop", normalizeHttpsSiteUrl("example.com/shop"))
    }

    @Test
    fun `an empty or spaced host has no usable url`() {
        assertNull(normalizeHttpsSiteUrl(""))
        assertNull(normalizeHttpsSiteUrl("https://"))
        assertNull(normalizeHttpsSiteUrl("shop example.com"))
    }

    @Test
    fun `hasHttpsSiteUrlHost follows normalization`() {
        assertTrue(hasHttpsSiteUrlHost("shop.example"))
        assertFalse(hasHttpsSiteUrlHost(""))
        assertFalse(hasHttpsSiteUrlHost("https://"))
    }
}
