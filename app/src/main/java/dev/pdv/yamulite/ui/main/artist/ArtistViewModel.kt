package dev.pdv.yamulite.ui.main.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdv.yamulite.data.music.MusicRepository
import dev.pdv.yamulite.data.music.dto.AlbumDto
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
data class ArtistUiState(
    val loading: Boolean = false,
    val name: String = "",
    val popularTracks: List<TrackDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MusicRepository,
    private val player: AudioPlayer,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("id").orEmpty()

    val downloadStates = downloads.downloads
    val likedIds = repo.likedIds

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.artistBrief(artistId) }
            .onSuccess {
                _state.value = ArtistUiState(
                    name = it.artist.name,
                    popularTracks = it.popularTracks,
                    albums = it.albums,
                )
            }
            .onFailure { _state.value = ArtistUiState(error = it.message ?: "خطأ في التحميل") }
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
