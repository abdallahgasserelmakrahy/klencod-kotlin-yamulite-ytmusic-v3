package dev.pdv.yamulite.ui.main.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdv.yamulite.data.music.MusicRepository
import dev.pdv.yamulite.data.playback.AudioPlayer
import dev.pdv.yamulite.data.playback.PlaybackUi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val player: AudioPlayer,
    private val repo: MusicRepository,
) : ViewModel() {
    val state: StateFlow<PlaybackUi> = player.state
    val likedIds = repo.likedIds

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleLike() = viewModelScope.launch {
        val id = state.value.track?.id ?: return@launch
        if (id in repo.likedIds.value) repo.unlike(id) else repo.like(id)
    }
}
