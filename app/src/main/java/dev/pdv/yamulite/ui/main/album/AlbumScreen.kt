package dev.pdv.yamulite.ui.main.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pdv.yamulite.ui.main.components.ContentUnavailable
import dev.pdv.yamulite.ui.main.components.CoverImage
import dev.pdv.yamulite.ui.main.components.GlassButton
import dev.pdv.yamulite.ui.main.components.IosSpinner
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
fun AlbumScreen(
    onBack: () -> Unit,
    vm: AlbumViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val likedIds = vm.likedIds.collectAsStateWithLifecycle()
    val downloadStates = vm.downloadStates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // العنوان المضمَّن يظهر بعد أن يمرّ العنوان الكبير تحت الشريط
    val titleVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 }
    }
    val barTop = statusBarTop() + LiquidTopBarHeight

    LiquidScaffold(
        overlay = { backdrop ->
            LiquidTopBar(
                backdrop = backdrop,
                title = state.title.ifBlank { "ألبوم" },
                titleVisible = titleVisible,
                onBack = onBack,
            )
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
                item { Header(state, onPlay = { vm.play(state.tracks, 0) }) }
                if (state.tracks.isEmpty()) {
                    item {
                        ContentUnavailable(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "لا توجد مقاطع في هذا الألبوم",
                        )
                    }
                } else {
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { idx, track ->
                        TrackRow(
                            track = track,
                            isLiked = rememberIsLiked(track.id, likedIds),
                            download = rememberDownloadInfo(track.id, downloadStates),
                            onClick = { vm.play(state.tracks, idx) },
                            onLikeToggle = { vm.toggleLike(track.id) },
                            onDownloadClick = { vm.onDownloadClick(track.id) },
                        )
                    }
                }
            }
        }
    }
}

/** رأس الألبوم بنمط تطبيق الموسيقى: غلاف كبير بظل ناعم، العنوان، الفنان بلون التمييز، وزر تشغيل. */
@Composable
private fun Header(state: AlbumUiState, onPlay: () -> Unit) {
    val ios = LocalIos.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoverImage(
            coverUri = state.coverUri,
            side = 240.dp,
            pixelSize = 600,
            cornerRadius = 26.dp,
            modifier = Modifier.shadow(
                24.dp, androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            ),
        )
        Text(
            state.title,
            style = MaterialTheme.typography.headlineSmall,
            color = ios.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (state.artistsLine.isNotBlank()) {
            Text(
                state.artistsLine,
                style = MaterialTheme.typography.titleMedium,
                color = ios.tint,
                textAlign = TextAlign.Center,
            )
        }
        state.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
        }
        if (state.tracks.isNotEmpty()) {
            GlassButton(
                text = "تشغيل",
                onClick = onPlay,
                prominent = true,
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}
