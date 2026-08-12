package me.sourov.quicksale.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class StripHtmlTest {

    @Test
    fun `decodes the entities a german catalog actually sends`() {
        // Verbatim from a product description that printed raw on the detail screen.
        val raw = "Malen, aquarellieren oder wachsmalen &#x2013; mit diesen 10 Papierdrachen " +
            "3-in-1 Buntstiften sind der Kreativit&auml;t keine Grenzen gesetzt. Perfekt " +
            "f&#xFC;r kleine K&#xFC;nstler."
        assertEquals(
            "Malen, aquarellieren oder wachsmalen – mit diesen 10 Papierdrachen 3-in-1 " +
                "Buntstiften sind der Kreativität keine Grenzen gesetzt. Perfekt für kleine " +
                "Künstler.",
            raw.stripHtml(),
        )
    }

    @Test
    fun `handles named decimal and hex forms alike`() {
        assertEquals("ü ü ü", "&uuml; &#252; &#xFC;".stripHtml())
    }

    @Test
    fun `strips tags and collapses the whitespace they leave behind`() {
        assertEquals("One Two", "<p>One</p>\n\n  <p>Two</p>".stripHtml())
    }

    @Test
    fun `an escaped angle bracket survives as text`() {
        // Decoding runs after tags are removed, so this is content, not markup.
        assertEquals("a < b", "a &lt; b".stripHtml())
    }

    @Test
    fun `a non-breaking space folds into the space beside it`() {
        assertEquals("5 kg", "5&nbsp;&nbsp;kg".stripHtml())
    }

    @Test
    fun `leaves an entity it does not know exactly as it came`() {
        assertEquals("&fake; &; text", "&fake; &; text".stripHtml())
    }
}
