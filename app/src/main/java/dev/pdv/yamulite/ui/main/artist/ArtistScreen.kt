package dev.pdv.yamulite.ui.main.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pdv.yamulite.ui.main.components.AlbumRow
import dev.pdv.yamulite.ui.main.components.ContentUnavailable
import dev.pdv.yamulite.ui.main.components.GlassButton
import dev.pdv.yamulite.ui.main.components.IosSpinner
import dev.pdv.yamulite.ui.main.components.LargeTitle
import dev.pdv.yamulite.ui.main.components.LiquidScaffold
import dev.pdv.yamulite.ui.main.components.LiquidTopBar
import dev.pdv.yamulite.ui.main.components.LiquidTopBarHeight
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.TrackRow
import dev.pdv.yamulite.ui.main.components.rememberDownloadInfo
import dev.pdv.yamulite.ui.main.components.rememberIsLiked
import dev.pdv.yamulite.ui.main.components.statusBarTop
import dev.pdv.yamulite.ui.theme.LocalIos

@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    vm: ArtistViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val likedIds = vm.likedIds.collectAsStateWithLifecycle()
    val downloadStates = vm.downloadStates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val titleVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 }
    }
    val barTop = statusBarTop() + LiquidTopBarHeight
    val name = state.name.ifBlank { "فنان" }

    LiquidScaffold(
        overlay = { backdrop ->
            LiquidTopBar(backdrop, title = name, titleVisible = titleVisible, onBack = onBack)
        },
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IosSpinner(size = 28.dp)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "حدث خطأ",
                    message = "خطأ: ${state.error}",
                    action = { GlassButton("إعادة المحاولة", onClick = vm::refresh, prominent = true) },
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = barTop, bottom = LocalBottomBarInset.current),
            ) {
                item(key = "title") { LargeTitle(name) }
                if (state.popularTracks.isNotEmpty()) {
                    item(key = "h-tracks") { SectionHeader("المقاطع الرائجة") }
                    itemsIndexed(state.popularTracks, key = { _, t -> "t-${t.id}" }) { idx, track ->
                        TrackRow(
                            track = track,
                            isLiked = rememberIsLiked(track.id, likedIds),
                            download = rememberDownloadInfo(track.id, downloadStates),
                            onClick = { vm.play(state.popularTracks, idx) },
                            onLikeToggle = { vm.toggleLike(track.id) },
                            onDownloadClick = { vm.onDownloadClick(track.id) },
                        )
                    }
                }
                if (state.albums.isNotEmpty()) {
                    item(key = "h-albums") { SectionHeader("الألبومات") }
                    items(state.albums, key = { "a-${it.id}" }) { album ->
                        AlbumRow(album = album, onClick = { onAlbumClick(album.id) })
                    }
                }
                if (state.popularTracks.isEmpty() && state.albums.isEmpty()) {
                    item {
                        ContentUnavailable(
                            icon = Icons.Rounded.Search,
                            title = "لم يتم العثور على نتائج",
                        )
                    }
                }
            }
        }
    }
}

/** عنوان قسم بنمط iOS: خط عريض بلون النص وليس بلون التمييز. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = LocalIos.current.label,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp),
    )
}
