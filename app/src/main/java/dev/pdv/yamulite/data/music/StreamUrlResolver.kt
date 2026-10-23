package dev.pdv.yamulite.data.music

import dev.pdv.yamulite.data.yt.YtMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedStream(val url: String, val codec: String, val userAgent: String)

/** Turns a YouTube video id into a direct audio URL. Used for streaming and for downloads. */
@Singleton
class StreamUrlResolver @Inject constructor(
    private val source: YtMusicSource,
) {
    suspend fun resolve(trackId: String, preferredBitrate: Int): ResolvedStream? =
        withContext(Dispatchers.IO) {
            val s = source.audioStream(trackId, preferredBitrate)
            ResolvedStream(url = s.url, codec = s.extension, userAgent = s.userAgent)
        }
}
