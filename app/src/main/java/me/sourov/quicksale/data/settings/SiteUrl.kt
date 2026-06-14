package me.sourov.quicksale.data.settings

const val HTTPS_SITE_URL_PREFIX = "https://"

fun String.toHttpsSiteUrlInput(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return HTTPS_SITE_URL_PREFIX
    return when {
        trimmed.startsWith(HTTPS_SITE_URL_PREFIX, ignoreCase = true) -> {
            HTTPS_SITE_URL_PREFIX + trimmed.drop(HTTPS_SITE_URL_PREFIX.length)
        }
        trimmed.startsWith("http://", ignoreCase = true) -> {
            HTTPS_SITE_URL_PREFIX + trimmed.drop("http://".length)
        }
        else -> HTTPS_SITE_URL_PREFIX + trimmed
    }
}

fun normalizeHttpsSiteUrl(raw: String): String? {
    val value = raw.toHttpsSiteUrlInput().trimEnd('/')
    val host = value.removePrefix(HTTPS_SITE_URL_PREFIX).trim()
    if (host.isBlank() || host.contains(' ') || host.startsWith('/')) return null
    return "$HTTPS_SITE_URL_PREFIX${host.trimEnd('/')}"
}

fun hasHttpsSiteUrlHost(raw: String): Boolean = normalizeHttpsSiteUrl(raw) != null
