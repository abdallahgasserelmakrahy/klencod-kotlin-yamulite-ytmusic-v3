package dev.pdv.yamulite.ui.main.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdv.yamulite.data.music.MusicRepository
import dev.pdv.yamulite.data.music.SearchResults
import dev.pdv.yamulite.data.music.SearchType
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.playback.AudioPlayer
import dev.pdv.yamulite.data.playback.DownloadManager
import dev.pdv.yamulite.data.playback.DownloadState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.Tracks,
    val loading: Boolean = false,
    val results: SearchResults = SearchResults(),
    val error: String? = null,
    val likeError: String? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val player: AudioPlayer,
    private val downloads: DownloadManager,
) : ViewModel() {

    val downloadStates = downloads.downloads

    fun play(tracks: List<TrackDto>, startIndex: Int) = player.play(tracks, startIndex)

    fun onDownloadClick(trackId: String) {
        if (downloads.downloads.value[trackId]?.state == DownloadState.Done) {
            downloads.delete(trackId)
        } else {
            downloads.download(trackId)
        }
    }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val likedIds = repo.likedIds

    private val queryFlow = MutableStateFlow("")
    private var inflightJob: Job? = null
    private var searchPage = 0

    init {
        queryFlow
            .debounce(350)
            .distinctUntilChanged()
            .onEach { runSearch() }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        queryFlow.value = text.trim()
    }

    fun onTypeChange(type: SearchType) {
        if (_state.value.type == type) return
        _state.update { it.copy(type = type) }
        runSearch()
    }

    fun toggleLike(trackId: String) = viewModelScope.launch {
        val result = if (trackId in repo.likedIds.value) repo.unlike(trackId) else repo.like(trackId)
        result.onFailure { ex ->
            _state.update { it.copy(likeError = ex.message ?: "خطأ أثناء تعديل الإعجاب") }
        }
    }

    fun clearLikeError() {
        _state.update { it.copy(likeError = null) }
    }

    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.hasMore) return
        val q = _state.value.query.trim()
        val type = _state.value.type
        if (q.isBlank()) return
        _state.update { it.copy(loadingMore = true) }
        val nextPage = searchPage + 1
        viewModelScope.launch {
            runCatching { repo.search(q, type, nextPage) }
                .onSuccess { newResults ->
                    searchPage = nextPage
                    _state.update { s ->
                        s.copy(
                            loadingMore = false,
                            hasMore = repo.hasMoreSearch(),
                            results = SearchResults(
                                tracks = s.results.tracks + newResults.tracks,
                                artists = s.results.artists + newResults.artists,
                                albums = s.results.albums + newResults.albums,
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    private fun runSearch() {
        val q = _state.value.query.trim()
        val type = _state.value.type
        inflightJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(loading = false, results = SearchResults(), error = null, hasMore = false) }
            return
        }
        searchPage = 0
        _state.update { it.copy(loading = true, error = null) }
        inflightJob = viewModelScope.launch {
            runCatching { repo.search(q, type, 0) }
                .onSuccess { results ->
                    _state.update { it.copy(
                        loading = false,
                        results = results,
                        error = null,
                        hasMore = repo.hasMoreSearch(),
                    ) }
                }
                .onFailure { ex ->
                    _state.update { it.copy(
                        loading = false,
                        error = ex.message ?: "خطأ في البحث",
                        hasMore = false,
                    ) }
                }
        }
    }
}
