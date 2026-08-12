package me.sourov.quicksale.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Store refusals drive behaviour through their `code` and are shown through their `message`; a
 * validation refusal also names the fields it rejected, which is what lets the branch form mark the
 * offending inputs instead of showing one banner over a fourteen-field address.
 */
class StoreErrorTest {

    @Test
    fun `a validation refusal carries the fields it named`() {
        val error = parseError(
            status = 400,
            body = """
            {
              "code": "woap_rest_invalid_location",
              "message": "Invalid branch.",
              "data": { "status": 400, "params": {
                "postcode": "Please enter a valid postcode.",
                "city": "Town / City is a required field."
              } }
            }
            """.trimIndent(),
        )

        assertEquals("woap_rest_invalid_location", error.code)
        assertEquals(400, error.status)
        assertEquals(setOf("postcode", "city"), error.params.keys)
        assertEquals("Please enter a valid postcode.", error.params["postcode"])
    }

    @Test
    fun `a refusal with no params is not treated as a field error`() {
        val error = parseError(
            status = 403,
            body = """{"code":"woap_rest_cannot_purchase","message":"Still awaiting approval."}""",
        )

        assertEquals("woap_rest_cannot_purchase", error.code)
        assertEquals("Still awaiting approval.", error.message)
        assertTrue(error.params.isEmpty())
    }

    /** WordPress wraps some messages in markup; the counter should never read a tag. */
    @Test
    fun `markup is stripped from the message`() {
        val error = parseError(
            status = 400,
            body = """{"code":"x","message":"<strong>Postcode</strong> is required."}""",
        )

        assertEquals("Postcode is required.", error.message)
    }

    /** A response that isn't JSON at all — a proxy's HTML error page — still has to say something. */
    @Test
    fun `an unparsable body falls back to a code and a readable message`() {
        val error = parseError(status = 502, body = "<html>Bad gateway</html>")

        assertEquals("quicksale_http_502", error.code)
        assertTrue(error.message.contains("502"))
    }
}
