package me.sourov.quicksale.data.settings

const val HTTPS_SITE_URL_PREFIX = "https://"

private val SCHEME_PREFIXES = listOf(HTTPS_SITE_URL_PREFIX, "http://")

/**
 * Reduces anything the operator types or pastes to the bare host, e.g. `shop.example/`.
 *
 * The scheme is never part of the editable text — the settings field shows `https://` as a fixed
 * adornment instead. That is deliberate: when the prefix lived *inside* the field, backspacing it
 * to `https:/` no longer matched the "already has a scheme" check, so a fresh `https://` was
 * prepended on every keystroke and the field filled up with `https://https://https://…`.
 *
 * A pasted URL still works: any number of leading schemes is peeled off, so
 * `https://https://shop.example` and `http://shop.example` both land on `shop.example`.
 */
fun String.toSiteHostInput(): String {
    var host = trim()
    var peeled = true
    while (peeled) {
        peeled = false
        for (scheme in SCHEME_PREFIXES) {
            if (host.startsWith(scheme, ignoreCase = true)) {
                host = host.drop(scheme.length)
                peeled = true
            }
        }
    }
    return host.trimStart('/')
}

/**
 * The canonical `https://host` form to store and call, or null when [raw] has no usable host.
 * Accepts either a bare host or a full URL.
 */
fun normalizeHttpsSiteUrl(raw: String): String? {
    val host = raw.toSiteHostInput().trim().trimEnd('/')
    if (host.isBlank() || host.contains(' ')) return null
    return "$HTTPS_SITE_URL_PREFIX$host"
}

fun hasHttpsSiteUrlHost(raw: String): Boolean = normalizeHttpsSiteUrl(raw) != null
