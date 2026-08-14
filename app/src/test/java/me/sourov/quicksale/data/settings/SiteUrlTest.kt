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
    fun `normalizing defaults to https and keeps the scheme it was given`() {
        assertEquals("https://shop.example", normalizeSiteUrl("shop.example"))
        assertEquals("https://shop.example", normalizeSiteUrl("https://shop.example/"))
        // Not upgraded to https: a store that only serves plain HTTP has to stay on it.
        assertEquals("http://testshop.local", normalizeSiteUrl("  http://testshop.local  "))
    }

    @Test
    fun `the scheme touching the host is the one that wins`() {
        assertEquals(HTTPS_SITE_URL_PREFIX, "shop.example".toSiteUrlParts().scheme)
        assertEquals(HTTP_SITE_URL_PREFIX, "http://shop.example".toSiteUrlParts().scheme)
        assertEquals(HTTPS_SITE_URL_PREFIX, "HTTPS://shop.example".toSiteUrlParts().scheme)
        assertEquals(HTTPS_SITE_URL_PREFIX, "https://https://shop.example".toSiteUrlParts().scheme)
        assertEquals(HTTP_SITE_URL_PREFIX, "https://http://shop.example".toSiteUrlParts().scheme)
    }

    /**
     * The settings field only moves its https/http control for a URL that spells a scheme out —
     * a bare host defaults to https without that being the operator's stated choice.
     */
    @Test
    fun `only an explicit scheme counts as one`() {
        assertTrue("http://shop.example".namesSiteScheme())
        assertTrue("  HTTPS://shop.example".namesSiteScheme())
        assertFalse("shop.example".namesSiteScheme())
        assertFalse("".namesSiteScheme())
    }

    @Test
    fun `a subdirectory install keeps its path`() {
        assertEquals("https://example.com/shop", normalizeSiteUrl("example.com/shop"))
    }

    @Test
    fun `an empty or spaced host has no usable url`() {
        assertNull(normalizeSiteUrl(""))
        assertNull(normalizeSiteUrl("https://"))
        assertNull(normalizeSiteUrl("shop example.com"))
    }

    @Test
    fun `hasSiteUrlHost follows normalization`() {
        assertTrue(hasSiteUrlHost("shop.example"))
        assertTrue(hasSiteUrlHost("http://testshop.local"))
        assertFalse(hasSiteUrlHost(""))
        assertFalse(hasSiteUrlHost("https://"))
        assertFalse(hasSiteUrlHost("http://"))
    }
}
