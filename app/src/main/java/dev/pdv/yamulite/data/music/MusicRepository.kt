package dev.pdv.yamulite.data.music

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdv.yamulite.data.music.dto.AlbumDto
import dev.pdv.yamulite.data.music.dto.AlbumWithTracksDto
import dev.pdv.yamulite.data.music.dto.ArtistBriefDto
import dev.pdv.yamulite.data.music.dto.ArtistDto
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.yt.YtMusicSource
import dev.pdv.yamulite.data.yt.YtSearchKind
import dev.pdv.yamulite.data.yt.YtSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val tracks: List<TrackDto> = emptyList(),
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
)

enum class SearchType { Tracks, Artists, Albums }

/**
 * Single entry point for the UI. Music comes from YouTube Music (no sign-in), favorites are
 * stored locally on the device.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val source: YtMusicSource,
    @ApplicationContext context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val favoritesFile = File(context.filesDir, "favorites.json")

    // Every track the UI has seen, so like(trackId) can store the full track without a network call.
    private val known = object : LinkedHashMap<String, TrackDto>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, TrackDto>) = size > 3000
    }

    private val favorites = MutableStateFlow(loadFavorites())
    private val _likedIds = MutableStateFlow(favorites.value.mapTo(linkedSetOf()) { it.id } as Set<String>)
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private class Cursor(val key: String, val page: YtSearchPage)
    @Volatile private var cursor: Cursor? = null

    // ---- Search (endless: every page has a continuation until YouTube runs out) ------------

    suspend fun search(text: String, type: SearchType, page: Int = 0): SearchResults =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext SearchResults()
            val key = "${type.name}|$text"
            val prev = cursor
            val result: YtSearchPage = when {
                page == 0 || prev == null || prev.key != key -> {
                    if (page > 0) return@withContext SearchResults()
                    source.search(text, kindOf(type))
                }
                else -> source.searchMore(prev.page)
            }
            cursor = Cursor(key, result)

            when (type) {
                SearchType.Tracks -> {
                    val tracks = result.items.mapNotNull { source.trackOf(it) }
                    remember(tracks)
                    SearchResults(tracks = tracks)
                }
                SearchType.Albums -> SearchResults(albums = result.items.mapNotNull { source.albumOf(it) })
                SearchType.Artists -> SearchResults(artists = result.items.mapNotNull { source.artistOf(it) })
            }
        }

    fun hasMoreSearch(): Boolean = cursor?.page?.hasMore == true

    private fun kindOf(type: SearchType) = when (type) {
        SearchType.Tracks -> YtSearchKind.Songs
        SearchType.Albums -> YtSearchKind.Albums
        SearchType.Artists -> YtSearchKind.Artists
    }

    // ---- Details ---------------------------------------------------------------------------

    suspend fun artistBrief(artistId: String): ArtistBriefDto = withContext(Dispatchers.IO) {
        source.artist(artistId).also { remember(it.popularTracks) }
    }

    suspend fun album(albumId: String): AlbumWithTracksDto = withContext(Dispatchers.IO) {
        source.album(albumId).also { dto -> remember(dto.volumes.flatten()) }
    }

    /** More songs "like" [seed], excluding [exclude] — this is what makes playback never end. */
    suspend fun radioAfter(seed: TrackDto, exclude: Set<String>): List<TrackDto> =
        withContext(Dispatchers.IO) {
            source.radio(seed.id).filter { it.id !in exclude }.also { remember(it) }
        }

    fun remember(tracks: List<TrackDto>) {
        synchronized(known) { tracks.forEach { known[it.id] = it } }
    }

    // ---- Favorites (local) -----------------------------------------------------------------

    /** Newest first. */
    fun favorites(): List<TrackDto> = favorites.value

    suspend fun like(trackId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val track = synchronized(known) { known[trackId] }
                ?: error("تعذّر العثور على بيانات المقطع")
            if (favorites.value.none { it.id == trackId }) {
                publish(listOf(track) + favorites.value)
            }
        }
    }

    suspend fun unlike(trackId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { publish(favorites.value.filterNot { it.id == trackId }) }
    }

    private fun publish(list: List<TrackDto>) {
        favorites.value = list
        _likedIds.value = list.mapTo(linkedSetOf()) { it.id }
        runCatching { favoritesFile.writeText(json.encodeToString(list)) }
    }

    private fun loadFavorites(): List<TrackDto> = runCatching {
        if (favoritesFile.exists()) json.decodeFromString<List<TrackDto>>(favoritesFile.readText())
        else emptyList()
    }.getOrDefault(emptyList()).also { list -> list.forEach { known[it.id] = it } }
}
