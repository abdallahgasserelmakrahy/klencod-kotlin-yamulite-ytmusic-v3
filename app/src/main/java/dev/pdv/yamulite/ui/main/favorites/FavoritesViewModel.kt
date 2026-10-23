package dev.pdv.yamulite.ui.main.favorites

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
data class FavoritesUiState(
    val loading: Boolean = false,
    val tracks: List<TrackDto> = emptyList(),
    val error: String? = null,
    val likeError: String? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val player: AudioPlayer,
    private val downloads: DownloadManager,
) : ViewModel() {

    val downloadStates = downloads.downloads
    val likedIds = repo.likedIds

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init { refresh() }

    fun play(tracks: List<TrackDto>, startIndex: Int) = player.play(tracks, startIndex)

    fun onDownloadClick(trackId: String) {
        if (downloads.downloads.value[trackId]?.state == DownloadState.Done) {
            downloads.delete(trackId)
        } else {
            downloads.download(trackId)
        }
    }

    // Favorites live on the device, so "refresh" is just re-reading the local list.
    fun refresh() = viewModelScope.launch {
        _state.value = FavoritesUiState(tracks = repo.favorites())
    }

    fun loadMore() = Unit

    fun syncLikes() = viewModelScope.launch {
        val known = _state.value.tracks.mapTo(mutableSetOf()) { it.id }
        if (repo.likedIds.value != known) refresh()
    }

    fun toggleLike(trackId: String) = viewModelScope.launch {
        val result = if (trackId in repo.likedIds.value) repo.unlike(trackId) else repo.like(trackId)
        result.onFailure { ex ->
            _state.update { it.copy(likeError = ex.message ?: "خطأ أثناء تعديل الإعجاب") }
        }
        refresh()
    }

    fun clearLikeError() {
        _state.update { it.copy(likeError = null) }
    }
}
