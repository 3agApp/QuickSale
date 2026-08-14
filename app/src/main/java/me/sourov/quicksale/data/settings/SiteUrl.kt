package me.sourov.quicksale.data.settings

const val HTTPS_SITE_URL_PREFIX = "https://"
const val HTTP_SITE_URL_PREFIX = "http://"

/** Longest first, so `https://` is never mistaken for `http://` followed by junk. */
private val SCHEME_PREFIXES = listOf(HTTPS_SITE_URL_PREFIX, HTTP_SITE_URL_PREFIX)

/** A site URL taken apart: the scheme it travels over, and the bare host to call. */
data class SiteUrlParts(val scheme: String, val host: String)

/**
 * Splits anything the operator types or pastes into its scheme and its bare host.
 *
 * The scheme is never part of the editable text — the settings field shows it as a fixed adornment
 * with its own control instead. That is deliberate: when the prefix lived *inside* the field,
 * backspacing it to `https:/` no longer matched the "already has a scheme" check, so a fresh
 * `https://` was prepended on every keystroke and the field filled up with `https://https://…`.
 *
 * Any number of leading schemes is peeled off, and the innermost one wins — the one actually
 * touching the host is the one the operator meant. So `https://https://shop.example` is https,
 * `http://testshop.local` is http, and a bare `shop.example` defaults to https.
 */
fun String.toSiteUrlParts(): SiteUrlParts {
    var rest = trim()
    var scheme = HTTPS_SITE_URL_PREFIX
    var peeled = true
    while (peeled) {
        peeled = false
        for (candidate in SCHEME_PREFIXES) {
            if (rest.startsWith(candidate, ignoreCase = true)) {
                scheme = candidate
                rest = rest.drop(candidate.length)
                peeled = true
            }
        }
    }
    return SiteUrlParts(scheme, rest.trimStart('/'))
}

/** Reduces anything typed or pasted to the bare host, e.g. `shop.example`. */
fun String.toSiteHostInput(): String = toSiteUrlParts().host

/**
 * Whether this text spells out a scheme of its own.
 *
 * Separate from [toSiteUrlParts] because the default it applies is indistinguishable from a typed
 * one: the settings field flips its https/http control for a *pasted* URL, and must leave the
 * operator's own choice alone while they type a bare host that defaults to https either way.
 */
fun String.namesSiteScheme(): Boolean =
    SCHEME_PREFIXES.any { trim().startsWith(it, ignoreCase = true) }

/**
 * The canonical `scheme://host` form to store and call, or null when [raw] has no usable host.
 *
 * Accepts a bare host or a full URL, and keeps whichever scheme [raw] names — so a stored
 * `http://testshop.local` survives a round trip instead of being quietly upgraded to https and
 * failing against a store that only serves plain HTTP.
 */
fun normalizeSiteUrl(raw: String): String? {
    val (scheme, host) = raw.toSiteUrlParts()
    val cleaned = host.trim().trimEnd('/')
    if (cleaned.isBlank() || cleaned.contains(' ')) return null
    return "$scheme$cleaned"
}

fun hasSiteUrlHost(raw: String): Boolean = normalizeSiteUrl(raw) != null
