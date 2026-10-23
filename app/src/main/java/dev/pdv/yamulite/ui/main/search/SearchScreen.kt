package dev.pdv.yamulite.ui.main.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pdv.yamulite.data.music.SearchType
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.playback.DownloadInfo
import dev.pdv.yamulite.ui.main.components.AlbumRow
import dev.pdv.yamulite.ui.main.components.ArtistRow
import dev.pdv.yamulite.ui.main.components.ContentUnavailable
import dev.pdv.yamulite.ui.main.components.GlassSearchField
import dev.pdv.yamulite.ui.main.components.GlassSegmentedControl
import dev.pdv.yamulite.ui.main.components.IosSpinner
import dev.pdv.yamulite.ui.main.components.LiquidScaffold
import dev.pdv.yamulite.ui.main.components.LiquidSnackbarHost
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.ScrollEdge
import dev.pdv.yamulite.ui.main.components.TrackRow
import dev.pdv.yamulite.ui.main.components.rememberDownloadInfo
import dev.pdv.yamulite.ui.main.components.rememberIsLiked
import dev.pdv.yamulite.ui.main.components.statusBarTop

@Composable
fun SearchScreen(
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val likedIds = vm.likedIds.collectAsStateWithLifecycle()
    val downloadStates = vm.downloadStates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()

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

    val onQueryChange: (String) -> Unit = remember(vm) { vm::onQueryChange }
    val onToggleLike: (String) -> Unit = remember(vm) { { vm.toggleLike(it) } }
    val onPlay: (List<TrackDto>, Int) -> Unit = remember(vm) { vm::play }
    val onDownloadClickFn: (String) -> Unit = remember(vm) { vm::onDownloadClick }

    val tabs = listOf(SearchType.Tracks, SearchType.Artists, SearchType.Albums)
    val labels = mapOf(
        SearchType.Tracks to "المقاطع",
        SearchType.Artists to "الفنانون",
        SearchType.Albums to "الألبومات",
    )

    val top = statusBarTop()
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerPx.toDp() }

    LiquidScaffold(
        overlay = { backdrop ->
            // حقل بحث زجاجي + تحكم مقسَّم، فوقهما حافة تمرير تُمَوِّه النتائج المارّة تحتهما
            ScrollEdge(
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerHeight + 18.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { headerPx = it.height }
                    .padding(top = top + 8.dp, bottom = 10.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "بحث",
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassSegmentedControl(
                    items = tabs.map { labels.getValue(it) },
                    selectedIndex = tabs.indexOf(state.type).coerceAtLeast(0),
                    onSelect = { vm.onTypeChange(tabs[it]) },
                )
            }
            LiquidSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomBarInset.current),
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val centered = Modifier
                .align(Alignment.Center)
                .padding(top = headerHeight, bottom = LocalBottomBarInset.current)
            when {
                state.loading -> IosSpinner(modifier = centered, size = 28.dp)
                state.error != null -> ContentUnavailable(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "حدث خطأ",
                    message = "خطأ: ${state.error}",
                    modifier = centered,
                )
                state.query.isBlank() -> ContentUnavailable(
                    icon = Icons.Rounded.Search,
                    title = "ابحث في الموسيقى",
                    message = "ابدأ بكتابة كلمة البحث",
                    modifier = centered,
                )
                else -> Results(
                    state, likedIds, downloadStates, lazyListState, headerHeight,
                    onToggleLike, onPlay, onDownloadClickFn, onArtistClick, onAlbumClick,
                )
            }
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    likedIds: State<Set<String>>,
    downloadStates: State<Map<String, DownloadInfo>>,
    lazyListState: LazyListState,
    headerHeight: Dp,
    onToggleLike: (String) -> Unit,
    onPlay: (List<TrackDto>, Int) -> Unit,
    onDownloadClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = headerHeight, bottom = LocalBottomBarInset.current),
    ) {
        when (state.type) {
            SearchType.Tracks -> {
                itemsIndexed(state.results.tracks, key = { _, t -> t.id }) { idx, track ->
                    TrackRow(
                        track = track,
                        isLiked = rememberIsLiked(track.id, likedIds),
                        download = rememberDownloadInfo(track.id, downloadStates),
                        onClick = { onPlay(state.results.tracks, idx) },
                        onLikeToggle = { onToggleLike(track.id) },
                        onDownloadClick = { onDownloadClick(track.id) },
                    )
                }
                if (state.results.tracks.isEmpty()) item { EmptyHint() }
            }
            SearchType.Artists -> {
                items(state.results.artists, key = { it.id }) { artist ->
                    ArtistRow(artist, onClick = { onArtistClick(artist.id) })
                }
                if (state.results.artists.isEmpty()) item { EmptyHint() }
            }
            SearchType.Albums -> {
                items(state.results.albums, key = { it.id }) { album ->
                    AlbumRow(album, onClick = { onAlbumClick(album.id) })
                }
                if (state.results.albums.isEmpty()) item { EmptyHint() }
            }
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

@Composable
private fun EmptyHint() {
    ContentUnavailable(
        icon = Icons.Rounded.Search,
        title = "لم يتم العثور على نتائج",
        modifier = Modifier.padding(top = 48.dp),
    )
}
