package dev.pdv.yamulite.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdv.yamulite.data.music.MusicRepository
import dev.pdv.yamulite.data.music.coverUrl
import dev.pdv.yamulite.data.music.dto.TrackDto
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class PlaybackUi(
    val track: TrackDto? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueStore: QueueStore,
    private val repo: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controllerReady = CompletableDeferred<MediaController>()

    // Kept for TrackDto lookup by mediaId after Media3 item transitions
    private var queue: List<TrackDto> = emptyList()

    private var positionJob: Job? = null

    // Endless playback: when the queue is about to run out we append YouTube Music "radio" tracks
    private var extending = false

    private val _state = MutableStateFlow(PlaybackUi())
    val state: StateFlow<PlaybackUi> = _state.asStateFlow()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { ctrl ->
                ctrl.addListener(makeListener(ctrl))
                _controllerReady.complete(ctrl)
                restoreQueueIfNeeded(ctrl)
            }.onFailure {
                _controllerReady.completeExceptionally(it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun makeListener(ctrl: MediaController) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                hasNext = ctrl.hasNextMediaItem(),
                hasPrevious = ctrl.hasPreviousMediaItem(),
            )
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                hasNext = ctrl.hasNextMediaItem(),
                hasPrevious = ctrl.hasPreviousMediaItem(),
            )
            if (playbackState == Player.STATE_READY) syncPositionAndDuration(ctrl)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val track = mediaItem?.mediaId?.let { id -> queue.firstOrNull { it.id == id } }
            _state.value = _state.value.copy(
                track = track,
                positionMs = 0L,
                durationMs = 0L,
                hasNext = ctrl.hasNextMediaItem(),
                hasPrevious = ctrl.hasPreviousMediaItem(),
            )
            extendQueueIfNeeded(ctrl)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                isLoading = false,
                isPlaying = false,
                error = error.errorCodeName,
            )
        }
    }

    fun play(tracks: List<TrackDto>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue = tracks
        repo.remember(tracks)
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        _state.value = _state.value.copy(
            track = tracks[safeIndex],
            isLoading = true,
            error = null,
        )
        scope.launch {
            withContext(Dispatchers.IO) { queueStore.save(tracks) }
            val c = _controllerReady.await()
            val items = tracks.map { it.toMediaItem() }
            c.setMediaItems(items, safeIndex, C.TIME_UNSET)
            c.prepare()
            c.play()
            extendQueueIfNeeded(c)
        }
    }

    /** Keeps at least a few tracks queued after the current one, fetching related songs forever. */
    private fun extendQueueIfNeeded(ctrl: MediaController) {
        if (extending || queue.isEmpty()) return
        val remaining = ctrl.mediaItemCount - ctrl.currentMediaItemIndex - 1
        if (remaining >= EXTEND_THRESHOLD) return
        extending = true
        scope.launch {
            try {
                val seed = queue.last()
                val known = queue.mapTo(HashSet()) { it.id }
                val more = runCatching { repo.radioAfter(seed, known) }.getOrDefault(emptyList())
                    .distinctBy { it.id }
                if (more.isNotEmpty()) {
                    queue = queue + more
                    ctrl.addMediaItems(more.map { it.toMediaItem() })
                    _state.value = _state.value.copy(hasNext = ctrl.hasNextMediaItem())
                    withContext(Dispatchers.IO) { queueStore.save(queue) }
                }
            } finally {
                extending = false
            }
        }
    }

    private fun restoreQueueIfNeeded(ctrl: MediaController) {
        scope.launch {
            if (ctrl.mediaItemCount == 0) return@launch
            val saved = withContext(Dispatchers.IO) { queueStore.load() }
            if (saved.isEmpty()) return@launch
            queue = saved
            repo.remember(saved)
            val currentId = ctrl.currentMediaItem?.mediaId
            val track = saved.firstOrNull { it.id == currentId } ?: return@launch
            _state.value = _state.value.copy(
                track = track,
                isPlaying = ctrl.isPlaying,
                hasNext = ctrl.hasNextMediaItem(),
                hasPrevious = ctrl.hasPreviousMediaItem(),
            )
            if (ctrl.isPlaying) startPositionUpdates()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val c = _controllerReady.await()
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun next() {
        scope.launch {
            val c = _controllerReady.await()
            if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        }
    }

    fun previous() {
        scope.launch {
            val c = _controllerReady.await()
            if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val c = _controllerReady.await()
            c.seekTo(positionMs.coerceAtLeast(0L))
            syncPositionAndDuration(c)
        }
    }

    fun stop() {
        scope.launch {
            withContext(Dispatchers.IO) { queueStore.clear() }
            val c = _controllerReady.await()
            c.stop()
            c.clearMediaItems()
            queue = emptyList()
            _state.value = PlaybackUi()
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            val c = _controllerReady.await()
            while (isActive) {
                syncPositionAndDuration(c)
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        scope.launch {
            syncPositionAndDuration(_controllerReady.await())
        }
    }

    private fun syncPositionAndDuration(ctrl: MediaController) {
        val pos = ctrl.currentPosition.coerceAtLeast(0L)
        val dur = ctrl.duration.let { if (it > 0) it else 0L }
        _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
    }

    private fun TrackDto.toMediaItem(): MediaItem {
        val art = coverUrl(coverUri, 400)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse("yamulite://track/$id"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artists.firstOrNull()?.name)
                    .setArtworkUri(art?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private companion object {
        const val EXTEND_THRESHOLD = 4
    }
}
