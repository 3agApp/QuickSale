package me.sourov.quicksale.data.update

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val name: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val body: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
) {
    val updateUrl: String
        get() = apkDownloadUrl ?: htmlUrl
}
