package dev.pdv.yamulite.ui.main.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdv.yamulite.data.music.MusicRepository
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.playback.AudioPlayer
import dev.pdv.yamulite.data.playback.DownloadManager
import dev.pdv.yamulite.data.playback.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AlbumUiState(
    val loading: Boolean = false,
    val title: String = "",
    val artistsLine: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
    val tracks: List<TrackDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MusicRepository,
    private val player: AudioPlayer,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>("id").orEmpty()

    val downloadStates = downloads.downloads
    val likedIds = repo.likedIds

    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.album(albumId) }
            .onSuccess { dto ->
                _state.value = AlbumUiState(
                    title = dto.title,
                    artistsLine = dto.artists.joinToString(", ") { it.name },
                    year = dto.year,
                    coverUri = dto.coverUri,
                    tracks = dto.volumes.flatten(),
                )
            }
            .onFailure { _state.value = AlbumUiState(error = it.message ?: "خطأ في التحميل") }
    }

    fun play(tracks: List<TrackDto>, startIndex: Int) = player.play(tracks, startIndex)

    fun toggleLike(trackId: String) = viewModelScope.launch {
        if (trackId in repo.likedIds.value) repo.unlike(trackId) else repo.like(trackId)
    }

    fun onDownloadClick(trackId: String) {
        if (downloads.downloads.value[trackId]?.state == DownloadState.Done) {
            downloads.delete(trackId)
        } else {
            downloads.download(trackId)
        }
    }
}
