package dev.pdv.yamulite.ui.main.nowplaying

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.pdv.yamulite.data.music.dto.ArtistShortDto
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.playback.PlaybackUi
import dev.pdv.yamulite.ui.main.components.CoverImage
import dev.pdv.yamulite.ui.main.components.FavoriteToggleButton
import dev.pdv.yamulite.ui.main.components.GlassBackdrop
import dev.pdv.yamulite.ui.main.components.GlassSurface
import dev.pdv.yamulite.ui.main.components.IosSpinner
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.glassRimBrush
import dev.pdv.yamulite.data.music.coverUrl

private val SoftWhite = Color.White.copy(alpha = 0.72f)

@Composable
fun NowPlayingScreen(
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    vm: NowPlayingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val track = state.track
    val coverUri = track?.coverUri ?: track?.albums?.firstOrNull()?.coverUri

    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val bgLayer = rememberGraphicsLayer()
    var bgOrigin by remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember(bgLayer, blurOk) {
        if (blurOk) GlassBackdrop(bgLayer) { bgOrigin } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerBackground(coverUri, bgLayer, blurOk) { bgOrigin = it }

        // المشغّل يعرض فوق خلفية فنية داكنة، فنثبّت اللون الأبيض للنصوص والأيقونات
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = LocalBottomBarInset.current),
                contentAlignment = Alignment.Center,
            ) {
                if (track == null) {
                    Text("لا يوجد تشغيل حالياً", style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
                    return@BoxWithConstraints
                }
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    val side = min(maxHeight, 300.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerCover(coverUri, side, state.isPlaying)
                        TrackDetails(
                            track, state, likedIds, vm, backdrop, onArtistClick, onAlbumClick,
                            Modifier.weight(1f),
                        )
                    }
                } else {
                    val side = min(maxWidth, 340.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        PlayerCover(coverUri, side, state.isPlaying)
                        TrackDetails(
                            track, state, likedIds, vm, backdrop, onArtistClick, onAlbumClick,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** خلفية من ألوان الغلاف (مشبعة ومموّهة) تُسجَّل في طبقة ليعكسها الزجاج. */
@Composable
private fun PlayerBackground(
    coverUri: String?,
    bgLayer: GraphicsLayer,
    record: Boolean,
    onOrigin: (Offset) -> Unit,
) {
    val url = remember(coverUri) { coverUrl(coverUri, 200) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { onOrigin(it.positionInWindow()) }
            .drawWithContent {
                if (record) {
                    bgLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(bgLayer)
                } else {
                    drawContent()
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) }),
                modifier = Modifier.fillMaxSize().blur(72.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.58f)))
            )
        )
    }
}

@Composable
private fun PlayerCover(coverUri: String?, side: Dp, playing: Boolean) {
    // يصغر الغلاف قليلاً عند الإيقاف المؤقت ويعود بنبضة عند التشغيل
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )
    CoverImage(
        coverUri = coverUri,
        side = side,
        pixelSize = 600,
        cornerRadius = 30.dp,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black, spotColor = Color.Black),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackDetails(
    track: TrackDto,
    state: PlaybackUi,
    likedIds: Set<String>,
    vm: NowPlayingViewModel,
    backdrop: GlassBackdrop?,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = modifier,
    ) {
        val hasTitle = !track.title.isNullOrBlank()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                when {
                    hasTitle -> {
                        Text(
                            text = track.title!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                        if (track.artists.isNotEmpty()) {
                            ArtistLinks(track.artists, MaterialTheme.typography.titleMedium, onArtistClick)
                        }
                    }
                    track.artists.isNotEmpty() -> ArtistLinks(
                        track.artists,
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        onArtistClick,
                    )
                    else -> Text(
                        text = "(بدون عنوان)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // زر المفضلة داخل دائرة زجاجية
            GlassSurface(backdrop, CircleShape, Modifier.size(50.dp), forceDark = true) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    FavoriteToggleButton(
                        isLiked = track.id in likedIds,
                        onToggle = vm::toggleLike,
                        modifier = Modifier.size(50.dp),
                        iconSize = 26.dp,
                        inactiveTint = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
        state.error?.let {
            Text("خطأ: $it", color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
        }

        // شريط التقدم وأزرار التحكم تبقى من اليسار لليمين
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SeekBar(state = state, onSeek = vm::seekTo)
                GlassControls(state, vm, backdrop)
            }
        }

        // اسم الألبوم في كبسولة زجاجية
        track.albums.firstOrNull()?.let { album ->
            val albumTitle = album.title
            if (!albumTitle.isNullOrBlank()) {
                val clickable = album.id.isNotEmpty()
                GlassSurface(backdrop, CircleShape, forceDark = true) {
                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .let { if (clickable) it.clickable { onAlbumClick(album.id) } else it }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private enum class PlayMode { Loading, Playing, Paused }

/** كبسولة زجاجية كبيرة تحوي السابق / تشغيل / التالي. */
@Composable
private fun GlassControls(state: PlaybackUi, vm: NowPlayingViewModel, backdrop: GlassBackdrop?) {
    GlassSurface(backdrop, CircleShape, Modifier.fillMaxWidth().height(96.dp), forceDark = true) {
        Row(
            modifier = Modifier.matchParentSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableButton(
                description = "السابق",
                enabled = state.hasPrevious,
                onClick = vm::previous,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(38.dp))
            }

            // زر التشغيل: عدسة زجاجية مضيئة
            val mode = when {
                state.isLoading -> PlayMode.Loading
                state.isPlaying -> PlayMode.Playing
                else -> PlayMode.Paused
            }
            PressableButton(
                description = if (mode == PlayMode.Playing) "إيقاف مؤقت" else "تشغيل",
                enabled = true,
                onClick = vm::togglePlayPause,
                modifier = Modifier
                    .size(74.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.18f))
                        )
                    )
                    .border(1.dp, glassRimBrush(0.9f, 0.5f), CircleShape),
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (scaleIn(spring(0.55f, Spring.StiffnessMedium), initialScale = 0.5f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.5f) + fadeOut())
                    },
                    label = "playPause",
                ) { m ->
                    when (m) {
                        PlayMode.Loading -> IosSpinner(size = 34.dp, color = Color.White)
                        PlayMode.Playing -> Icon(Icons.Rounded.Pause, null, Modifier.size(42.dp))
                        PlayMode.Paused -> Icon(Icons.Rounded.PlayArrow, null, Modifier.size(46.dp))
                    }
                }
            }

            PressableButton(
                description = "التالي",
                enabled = state.hasNext,
                onClick = vm::next,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(38.dp))
            }
        }
    }
}

/** زر بحركة ضغط نابضة (يصغر ويُضيء) كما في عناصر iOS. */
@Composable
private fun PressableButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val glow by animateFloatAsState(if (pressed) 1f else 0f, label = "pressGlow")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.35f)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { if (enabled) onClick(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .drawBehind { drawCircle(Color.White.copy(alpha = 0.16f * glow)) },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ArtistLinks(
    artists: List<ArtistShortDto>,
    style: TextStyle,
    onArtistClick: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
        artists.forEachIndexed { index, artist ->
            if (index > 0) Text("، ", style = style, color = SoftWhite)
            val clickable = artist.id.isNotEmpty()
            Text(
                text = artist.name,
                style = style,
                color = SoftWhite,
                modifier = if (clickable) Modifier.clickable { onArtistClick(artist.id) } else Modifier,
            )
        }
    }
}

/** شريط تقدم بنمط Apple Music: كبسولة بدون مؤشر، تسمك عند اللمس. */
@Composable
private fun SeekBar(state: PlaybackUi, onSeek: (Long) -> Unit) {
    val duration = state.durationMs
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        dragging -> dragFraction
        duration > 0 -> (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val displayMs = if (dragging) (dragFraction * duration).toLong() else state.positionMs
    val trackH by animateDpAsState(
        targetValue = if (dragging) 14.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "trackH",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { off ->
                        onSeek((off.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            dragging = true
                            dragFraction = (off.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((dragFraction * duration).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val h = trackH.toPx()
            val cy = size.height / 2
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(0f, cy - h / 2),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2),
            )
            val w = size.width * fraction
            if (w > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(0f, cy - h / 2),
                    size = Size(w.coerceAtLeast(h), h),
                    cornerRadius = CornerRadius(h / 2),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(formatTime(displayMs), style = MaterialTheme.typography.labelMedium, color = SoftWhite)
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = SoftWhite)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(java.util.Locale.US, minutes, seconds)
}
