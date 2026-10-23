package dev.pdv.yamulite.ui.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.pdv.yamulite.data.playback.DownloadInfo

// Reading likedIds/downloadStates directly inside a LazyColumn item recomposes every
// visible row whenever any track's like or download state changes, since the whole
// Set/Map is a new instance each time. Deriving the per-item value scopes recomposition
// to just the row whose own value actually changed.

@Composable
fun rememberIsLiked(trackId: String, likedIds: State<Set<String>>): Boolean {
    val derived = remember(trackId) { derivedStateOf { trackId in likedIds.value } }
    return derived.value
}

@Composable
fun rememberDownloadInfo(trackId: String, downloadStates: State<Map<String, DownloadInfo>>): DownloadInfo? {
    val derived = remember(trackId) { derivedStateOf { downloadStates.value[trackId] } }
    return derived.value
}
