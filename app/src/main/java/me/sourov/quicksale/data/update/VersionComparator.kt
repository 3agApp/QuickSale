package me.sourov.quicksale.data.update

fun normalizedVersionName(value: String): String =
    value.trim().removePrefix("v").removePrefix("V")

fun compareVersionNames(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    if (leftParts.isEmpty() || rightParts.isEmpty()) {
        return normalizedVersionName(left).compareTo(normalizedVersionName(right), ignoreCase = true)
    }
    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return 0
}

fun isNewerVersion(candidate: String, current: String): Boolean =
    compareVersionNames(candidate, current) > 0

private fun versionParts(value: String): List<Int> =
    normalizedVersionName(value)
        .split(Regex("[^0-9]+"))
        .mapNotNull { it.toIntOrNull() }
