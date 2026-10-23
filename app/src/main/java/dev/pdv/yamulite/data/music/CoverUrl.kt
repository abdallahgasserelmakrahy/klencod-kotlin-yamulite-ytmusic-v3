package dev.pdv.yamulite.data.music

private val googleSizeParam = Regex("=w\\d+-h\\d+")
private val googleSizeParam2 = Regex("=s\\d+")

/**
 * Cover art comes straight from YouTube Music. Google-hosted thumbnails accept a size
 * suffix (`=w544-h544`), so we ask for the size the screen actually needs.
 */
fun coverUrl(uri: String?, size: Int = 200): String? {
    val url = uri?.takeIf { it.isNotBlank() } ?: return null
    val isGoogleImage = "googleusercontent.com" in url || "ggpht.com" in url
    return when {
        !isGoogleImage -> url
        googleSizeParam.containsMatchIn(url) -> googleSizeParam.replace(url, "=w$size-h$size")
        googleSizeParam2.containsMatchIn(url) -> googleSizeParam2.replace(url, "=s$size")
        else -> url
    }
}
