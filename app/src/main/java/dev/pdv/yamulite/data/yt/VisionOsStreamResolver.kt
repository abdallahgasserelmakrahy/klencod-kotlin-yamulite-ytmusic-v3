package dev.pdv.yamulite.data.yt

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.TimeZone

/**
 * Fast anonymous path to a direct audio URL: one call to YouTube's `youtubei/v1/player` endpoint
 * using the VisionOS client (the same approach the Umihi Music app uses). If it fails for any
 * reason the caller falls back to NewPipeExtractor.
 */
internal class VisionOsStreamResolver(baseClient: OkHttpClient) {

    class Stream(val url: String, val extension: String, val kbps: Int)

    private val http = baseClient.newBuilder().cache(null).build()

    @Volatile private var visitorData: String? = null

    fun resolve(videoId: String, maxKbps: Int): Stream? {
        repeat(MAX_TRIES) {
            val before = visitorData
            val root = runCatching { request(videoId) }.getOrNull()
            if (root != null) {
                root.optJSONObject("responseContext")?.optString("visitorData")
                    ?.takeIf { it.isNotBlank() }?.let { visitorData = it }
                pick(root, maxKbps)?.let { if (isValid(it.url)) return it }
            }
            // Nothing new to retry with (no fresh visitor data) -> give up and let the caller fall back.
            if (visitorData == before) return null
        }
        return null
    }

    private fun request(videoId: String): JSONObject {
        val client = JSONObject()
            .put("clientName", "VISIONOS")
            .put("clientVersion", "0.1")
            .put("xClientName", "101")
            .put("userAgent", USER_AGENT)
            .put("osName", "visionOS")
            .put("osVersion", "1.3.21O771")
            .put("deviceMake", "Apple")
            .put("deviceModel", "RealityDevice14,1")
            .put("platform", "DESKTOP")
        val context = JSONObject()
            .put("client", client)
            .put("user", JSONObject().put("lockedSafetyMode", false))
        visitorData?.let { context.put("visitorData", it) }
        val body = JSONObject().put("context", context).put("videoId", videoId)

        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val builder = Request.Builder()
            .url(PLAYER_URL)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Origin", ORIGIN)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-Goog-Event-Time", now.toString())
            .header("X-Goog-Request-Time", now.toString())
            .header("X-YouTube-Utc-Offset", (tz.getOffset(now) / 60000).toString())
            .header("X-YouTube-Time-Zone", tz.id)
            .header("X-YouTube-Client-Name", "101")
            .header("X-YouTube-Client-Version", "0.1")
            .header("User-Agent", USER_AGENT)
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }

        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return JSONObject(resp.body?.string().orEmpty())
        }
    }

    private fun pick(root: JSONObject, maxKbps: Int): Stream? {
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        val audio = buildList {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                val mime = f.optString("mimeType")
                if (!mime.startsWith("audio/", ignoreCase = true)) continue
                val bps = f.optInt("averageBitrate", 0).takeIf { it > 0 } ?: f.optInt("bitrate", 0)
                val kbps = if (bps > 0) bps / 1000 else 128
                add(Stream(url, if (mime.contains("webm", true)) "webm" else "m4a", kbps))
            }
        }
        if (audio.isEmpty()) return null
        val within = audio.filter { it.kbps <= maxKbps }
        return if (within.isNotEmpty()) within.maxByOrNull { it.kbps } else audio.minByOrNull { it.kbps }
    }

    private fun isValid(url: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(url).head().header("User-Agent", USER_AGENT).build())
            .execute().use { it.code in 200..399 }
    }.getOrDefault(false)

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
        private const val ORIGIN = "https://www.youtube.com"
        private const val PLAYER_URL = "$ORIGIN/youtubei/v1/player?prettyPrint=false"
        private const val MAX_TRIES = 3
    }
}
