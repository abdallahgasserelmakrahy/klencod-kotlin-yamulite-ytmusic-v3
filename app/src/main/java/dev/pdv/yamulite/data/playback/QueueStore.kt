package dev.pdv.yamulite.data.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdv.yamulite.data.music.dto.TrackDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "queue.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun save(tracks: List<TrackDto>) {
        runCatching { file.writeText(json.encodeToString(tracks)) }
    }

    fun load(): List<TrackDto> =
        runCatching {
            if (file.exists()) json.decodeFromString<List<TrackDto>>(file.readText())
            else emptyList()
        }.getOrDefault(emptyList())

    fun clear() {
        runCatching { file.delete() }
    }
}
