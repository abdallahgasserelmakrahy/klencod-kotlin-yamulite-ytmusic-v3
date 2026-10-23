package dev.pdv.yamulite.data.music.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * App-level music models. They keep the shape the UI was built around, but every id is now a
 * plain String because YouTube Music identifiers (video ids, channel ids, playlist ids) are
 * strings. `coverUri` is a full https URL.
 */

@Immutable
@Serializable
data class TrackDto(
    val id: String,
    val title: String? = null,
    val coverUri: String? = null,
    val durationMs: Long? = null,
    val artists: List<ArtistShortDto> = emptyList(),
    val albums: List<AlbumShortDto> = emptyList(),
    val available: Boolean? = null,
)

@Immutable
@Serializable
data class ArtistShortDto(
    val id: String = "",
    val name: String = "",
)

@Immutable
@Serializable
data class AlbumShortDto(
    val id: String = "",
    val title: String? = null,
    val coverUri: String? = null,
)

@Immutable
@Serializable
data class ArtistDto(
    val id: String = "",
    val name: String = "",
    val cover: CoverDto? = null,
    val ogImage: String? = null,
)

@Immutable
@Serializable
data class AlbumDto(
    val id: String = "",
    val title: String = "",
    val coverUri: String? = null,
    val artists: List<ArtistShortDto> = emptyList(),
    val year: Int? = null,
)

@Immutable
@Serializable
data class CoverDto(val uri: String? = null, val type: String? = null)

@Serializable
data class ArtistBriefDto(
    val artist: ArtistDto = ArtistDto(),
    val popularTracks: List<TrackDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumWithTracksDto(
    val id: String = "",
    val title: String = "",
    val coverUri: String? = null,
    val artists: List<ArtistShortDto> = emptyList(),
    val year: Int? = null,
    val volumes: List<List<TrackDto>> = emptyList(),
)
