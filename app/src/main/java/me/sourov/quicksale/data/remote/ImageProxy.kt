package me.sourov.quicksale.data.remote

/**
 * The marker that turns a site URL into an image proxy.
 *
 * A store reachable only through a proxy is configured with the proxy's own address as its site
 * URL — `https://example.com/proxy` — and that suffix is the signal, since a WooCommerce install
 * would not normally live under it.
 */
private const val PROXY_PATH_SUFFIX = "/proxy"

/**
 * Rewrites an image URL to travel the same route the API does, or returns it untouched.
 *
 * WordPress writes media URLs against the site's *internal* address, which on a proxied store is a
 * name only the proxy can resolve — `http://testshop.local/wp-content/uploads/…`. The REST calls
 * reach the store through the proxy and work; the image URLs the store hands back do not, so every
 * product renders its placeholder box. Swapping the origin for the configured site URL sends the
 * picture through the same door as the JSON that named it:
 *
 * ```
 * http://testshop.local/wp-content/uploads/2026/08/x.webp
 *   → https://testsite.example.com/proxy/wp-content/uploads/2026/08/x.webp
 * ```
 *
 * Only the scheme and host are replaced — the path, query and fragment are carried across
 * verbatim, so a thumbnail size or a cache-buster still arrives intact.
 *
 * The rewrite is deliberately narrow, and a URL is left exactly as it came when:
 * - the store isn't proxied ([siteUrl] doesn't end in [PROXY_PATH_SUFFIX]), which is every
 *   ordinary install;
 * - it is relative, or carries a scheme this can't route (`data:`, a protocol-relative `//host/…`);
 * - it names only an origin, with no path to carry over;
 * - it already points at the proxy, so re-running a sync over already-rewritten rows is a no-op.
 */
fun String.throughSiteProxy(siteUrl: String): String {
    if (isBlank()) return this

    val proxyBase = siteUrl.trim().trimEnd('/')
    if (!proxyBase.endsWith(PROXY_PATH_SUFFIX, ignoreCase = true)) return this
    if (startsWith("$proxyBase/", ignoreCase = true)) return this

    val schemeEnd = indexOf("://")
    if (schemeEnd <= 0) return this
    val scheme = substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return this

    // Everything from the host's first slash onwards: path, query and fragment together.
    val pathStart = indexOf('/', startIndex = schemeEnd + 3)
    if (pathStart < 0) return this

    return proxyBase + substring(pathStart)
}
