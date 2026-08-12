package me.sourov.quicksale.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.normalizeHttpsSiteUrl
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A failed store request. Callers should branch on [code] — the machine-readable slug WordPress
 * and the organization plugin return — and show [message], which is already localised to the
 * site's language and phrased for a person to act on.
 *
 * [params] carries WordPress's own `data.params` on a validation refusal: field path → reason, so a
 * form can mark the offending inputs instead of showing one banner over a fourteen-field address.
 */
class WooApiException(
    val code: String,
    override val message: String,
    val status: Int,
    val params: Map<String, String> = emptyMap(),
) : Exception(message)

/** A response, with the few headers the sync path needs. Header names are matched case-insensitively. */
class WooResponse(
    val status: Int,
    val body: String,
    private val headers: Map<String, String>,
) {
    val notModified: Boolean get() = status == 304

    val successful: Boolean get() = status in 200..299

    val etag: String? get() = headers["etag"]?.takeIf { it.isNotBlank() }

    val totalPages: Int get() = headers["x-wp-totalpages"]?.toIntOrNull() ?: 1
}

/**
 * The single place QuickSale opens a connection to the store.
 *
 * Credentials travel in an `Authorization: Basic` header rather than the query string, so keys
 * stay out of URLs, proxy logs and crash reports. A minority of hosts strip that header before
 * PHP sees it; when a Basic request comes back 401/403 the call is retried once with WooCommerce's
 * query-string auth, which those hosts do accept.
 */
class WooHttp(private val settings: StoreSettings) {

    /** `GET https://site/wp-json/[path]`. [path] may already carry a query string. */
    suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
        ifNoneMatch: String? = null,
    ): WooResponse = request("GET", path, query, body = null, ifNoneMatch = ifNoneMatch)

    /** `POST https://site/wp-json/[path]` with a JSON [body]. */
    suspend fun post(
        path: String,
        body: JSONObject,
        query: Map<String, String> = emptyMap(),
    ): WooResponse = request("POST", path, query, body = body.toString(), ifNoneMatch = null)

    /**
     * `PATCH https://site/wp-json/[path]` with a JSON [body].
     *
     * Sent as a POST carrying `X-HTTP-Method-Override`, because `HttpURLConnection` refuses to issue
     * a PATCH at all. `WP_REST_Server::dispatch()` reads that header on POST requests and routes to
     * the PATCH handler, which is the same path WooCommerce's own clients take.
     */
    suspend fun patch(
        path: String,
        body: JSONObject,
        query: Map<String, String> = emptyMap(),
    ): WooResponse = request(
        method = "POST",
        path = path,
        query = query,
        body = body.toString(),
        ifNoneMatch = null,
        methodOverride = "PATCH",
    )

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String?,
        ifNoneMatch: String?,
        methodOverride: String? = null,
    ): WooResponse = withContext(Dispatchers.IO) {
        val response = execute(method, path, query, body, ifNoneMatch, methodOverride, useQueryAuth = false)
        // Retry once with query-string auth for hosts that strip the Authorization header.
        val final = if (response.status == 401 || response.status == 403) {
            execute(method, path, query, body, ifNoneMatch, methodOverride, useQueryAuth = true)
        } else {
            response
        }
        if (!final.successful && !final.notModified) throw parseError(final.status, final.body)
        final
    }

    private fun execute(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String?,
        ifNoneMatch: String?,
        methodOverride: String?,
        useQueryAuth: Boolean,
    ): WooResponse {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw WooApiException("quicksale_invalid_site_url", "Enter a valid store URL in Settings", 0)

        val parameters = buildMap {
            putAll(query)
            if (useQueryAuth) {
                put("consumer_key", settings.consumerKey.trim())
                put("consumer_secret", settings.consumerSecret.trim())
            }
        }
        val separator = if ('?' in path) '&' else '?'
        val queryString = parameters.entries.joinToString("&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
        val endpoint = "$base/wp-json/$path" + if (queryString.isEmpty()) "" else "$separator$queryString"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                methodOverride?.let { setRequestProperty("X-HTTP-Method-Override", it) }
                if (!useQueryAuth) setRequestProperty("Authorization", basicAuthHeader())
                ifNoneMatch?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-None-Match", it) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            body?.let { payload ->
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // Header values must be read before the connection is released.
            val headers = buildMap {
                connection.headerFields.forEach { (name, values) ->
                    if (name != null) put(name.lowercase(), values.firstOrNull().orEmpty())
                }
            }
            return WooResponse(status, text, headers)
        } finally {
            connection?.disconnect()
        }
    }

    private fun basicAuthHeader(): String {
        val credentials = "${settings.consumerKey.trim()}:${settings.consumerSecret.trim()}"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private companion object {
        const val TIMEOUT_MS = 20_000

        fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
    }
}

/**
 * WordPress errors are `{"code": "...", "message": "...", "data": {"params": {…}}}`. Keep all three:
 * the code drives behaviour, the message is what the counter staff reads, and `data.params` names
 * the fields a form should mark.
 */
internal fun parseError(status: Int, body: String): WooApiException {
    val json = runCatching { JSONObject(body) }.getOrNull()
    val code = json?.optString("code")?.takeIf { it.isNotBlank() } ?: fallbackErrorCode(status)
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }?.stripHtml()
        ?: fallbackErrorMessage(status)
    val params = json?.optJSONObject("data")?.optJSONObject("params")
    return WooApiException(
        code = code,
        message = message,
        status = status,
        params = buildMap {
            params?.keys()?.forEach { key -> put(key, params.optString(key).stripHtml()) }
        },
    )
}

private fun fallbackErrorCode(status: Int): String = when (status) {
    401, 403 -> "woocommerce_rest_authentication_error"
    404 -> "rest_no_route"
    else -> "quicksale_http_$status"
}

private fun fallbackErrorMessage(status: Int): String = when (status) {
    401, 403 -> "Your store rejected the API keys — check them in Settings"
    404 -> "That endpoint wasn't found — is the organization accounts plugin active?"
    in 500..599 -> "The store hit an error (HTTP $status) — try again shortly"
    else -> "The store returned HTTP $status"
}

/**
 * Strips the markup WordPress wraps around error messages and product descriptions.
 *
 * Entities go through [decodeHtmlEntities] rather than a handful of hand-written replacements.
 * The old list covered five of them, which was enough for an English error message and not nearly
 * enough for a product description: a German catalog put `Kreativit&auml;t` and `f&#xFC;r kleine
 * K&#xFC;nstler` on the screen verbatim, because neither the named umlauts nor the hex form were
 * in the list. Decoding runs after the tags are removed, so an escaped `&lt;` survives as text
 * instead of being mistaken for markup, and before whitespace is collapsed, so a decoded `&nbsp;`
 * folds into the space beside it.
 */
internal fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), "")
        .decodeHtmlEntities()
        .replace(Regex("\\s+"), " ")
        .trim()

/** Decodes numeric (`&#36;`, `&#x24;`) and the named HTML entities a European catalog uses. */
internal fun String.decodeHtmlEntities(): String {
    if ('&' !in this) return this
    return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(this) { match ->
        val entity = match.groupValues[1]
        val codePoint = when {
            entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> NAMED_ENTITIES[entity]
        }
        codePoint?.let { String(Character.toChars(it)) } ?: match.value
    }
}

/**
 * The named entities worth carrying, which is not all of HTML's several hundred.
 *
 * Punctuation and currency because they turn up in prices and copy; the accented Latin letters
 * because the shop's catalog is German, French and Italian, and `K&uuml;nstler` printed raw on a
 * shelf label is the kind of thing a customer sees before anyone else does. Anything unlisted is
 * left exactly as it came rather than guessed at.
 */
private val NAMED_ENTITIES = mapOf(
    "amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "nbsp" to ' '.code,
    "quot" to '"'.code, "apos" to '\''.code,
    "pound" to '£'.code, "euro" to '€'.code, "yen" to '¥'.code, "cent" to '¢'.code,
    "ndash" to '–'.code, "mdash" to '—'.code, "hellip" to '…'.code,
    "lsquo" to '‘'.code, "rsquo" to '’'.code, "ldquo" to '“'.code, "rdquo" to '”'.code,
    "bull" to '•'.code, "middot" to '·'.code, "times" to '×'.code, "deg" to '°'.code,
    "reg" to '®'.code, "copy" to '©'.code, "trade" to '™'.code,
    "auml" to 'ä'.code, "ouml" to 'ö'.code, "uuml" to 'ü'.code,
    "Auml" to 'Ä'.code, "Ouml" to 'Ö'.code, "Uuml" to 'Ü'.code, "szlig" to 'ß'.code,
    "agrave" to 'à'.code, "aacute" to 'á'.code, "acirc" to 'â'.code, "aring" to 'å'.code,
    "egrave" to 'è'.code, "eacute" to 'é'.code, "ecirc" to 'ê'.code, "euml" to 'ë'.code,
    "igrave" to 'ì'.code, "iacute" to 'í'.code, "icirc" to 'î'.code, "iuml" to 'ï'.code,
    "ograve" to 'ò'.code, "oacute" to 'ó'.code, "ocirc" to 'ô'.code, "oslash" to 'ø'.code,
    "ugrave" to 'ù'.code, "uacute" to 'ú'.code, "ucirc" to 'û'.code,
    "ccedil" to 'ç'.code, "ntilde" to 'ñ'.code,
    "Agrave" to 'À'.code, "Eacute" to 'É'.code, "Ccedil" to 'Ç'.code,
)
