package dev.pdv.yamulite.ui.main.favorites

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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pdv.yamulite.ui.main.components.ContentUnavailable
import dev.pdv.yamulite.ui.main.components.GlassButton
import dev.pdv.yamulite.ui.main.components.IosSpinner
import dev.pdv.yamulite.ui.main.components.LargeTitle
import dev.pdv.yamulite.ui.main.components.LiquidScaffold
import dev.pdv.yamulite.ui.main.components.LiquidSnackbarHost
import dev.pdv.yamulite.ui.main.components.LiquidTopBar
import dev.pdv.yamulite.ui.main.components.LiquidTopBarHeight
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.TrackRow
import dev.pdv.yamulite.ui.main.components.rememberDownloadInfo
import dev.pdv.yamulite.ui.main.components.rememberIsLiked
import dev.pdv.yamulite.ui.main.components.statusBarTop

private const val Title = "المفضلة"

@Composable
fun FavoritesScreen(vm: FavoritesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val likedIds = vm.likedIds.collectAsStateWithLifecycle()
    val downloadStates = vm.downloadStates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.syncLikes() }

    LaunchedEffect(state.likeError) {
        state.likeError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearLikeError()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = lazyListState.layoutInfo.totalItemsCount
            lastIndex >= total - 3 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.loadingMore) vm.loadMore()
    }

    // العنوان المضمَّن يظهر في الشريط عندما يختفي العنوان الكبير بالتمرير
    val titleCollapsed by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 70
        }
    }
    val barTop = statusBarTop() + LiquidTopBarHeight

    LiquidScaffold(
        overlay = { backdrop ->
            LiquidTopBar(backdrop, title = Title, titleVisible = titleCollapsed, onBack = null)
            LiquidSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomBarInset.current),
            )
        },
    ) {
        when {
            state.loading && state.tracks.isEmpty() -> Column(Modifier.fillMaxSize().padding(top = barTop)) {
                LargeTitle(Title)
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    IosSpinner(size = 28.dp)
                }
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(top = barTop)) {
                LargeTitle(Title)
                ContentUnavailable(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "حدث خطأ",
                    message = "خطأ: ${state.error}",
                    modifier = Modifier.padding(top = 48.dp),
                    action = { GlassButton("إعادة المحاولة", onClick = vm::refresh, prominent = true) },
                )
            }
            state.tracks.isEmpty() -> Column(Modifier.fillMaxSize().padding(top = barTop)) {
                LargeTitle(Title)
                ContentUnavailable(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = "المفضلة فارغة",
                    message = "المقاطع التي تضع عليها قلباً تظهر هنا.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            else -> LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = barTop, bottom = LocalBottomBarInset.current),
            ) {
                item(key = "title") { LargeTitle(Title) }
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
                if (state.loadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { IosSpinner(size = 24.dp) }
                    }
                }
            }
        }
    }
}
