package me.sourov.quicksale.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(
    private val releasesApiUrl: String = BuildConfig.GITHUB_RELEASES_API_URL,
) {

    suspend fun fetchReleases(): List<AppRelease> = withContext(Dispatchers.IO) {
        if (releasesApiUrl.isBlank()) return@withContext emptyList()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(releasesApiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "QuickSale/${BuildConfig.VERSION_NAME}")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP $code")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue
                    add(release.toAppRelease())
                }
            }.sortedWith { left, right ->
                val versionOrder = compareVersionNames(right.versionName, left.versionName)
                if (versionOrder != 0) versionOrder else right.publishedAt.compareTo(left.publishedAt)
            }
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchLatestStable(): AppRelease? =
        fetchReleases().firstOrNull { !it.isPrerelease }

    private fun JSONObject.toAppRelease(): AppRelease {
        val tagName = optString("tag_name").ifBlank { optString("name") }
        val fallbackName = tagName.ifBlank { "Release" }
        return AppRelease(
            tagName = tagName,
            versionName = normalizedVersionName(tagName.ifBlank { optString("name") }),
            name = optString("name").ifBlank { fallbackName },
            htmlUrl = optString("html_url"),
            apkDownloadUrl = findApkDownloadUrl(optJSONArray("assets")),
            body = optString("body"),
            publishedAt = optString("published_at"),
            isPrerelease = optBoolean("prerelease"),
        )
    }

    private fun findApkDownloadUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

}
