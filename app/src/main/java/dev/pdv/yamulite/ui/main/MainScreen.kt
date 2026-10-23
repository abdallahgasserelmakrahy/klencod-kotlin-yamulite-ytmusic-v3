package dev.pdv.yamulite.ui.main

import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.pdv.yamulite.ui.main.album.AlbumScreen
import dev.pdv.yamulite.ui.main.artist.ArtistScreen
import dev.pdv.yamulite.ui.main.components.GlassBackdrop
import dev.pdv.yamulite.ui.main.components.GlassBarHeight
import dev.pdv.yamulite.ui.main.components.GlassTabBar
import dev.pdv.yamulite.ui.main.components.GlassTabItem
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.ScrollEdge
import dev.pdv.yamulite.ui.main.favorites.FavoritesScreen
import dev.pdv.yamulite.ui.main.nowplaying.NowPlayingScreen
import dev.pdv.yamulite.ui.main.search.SearchScreen
import dev.pdv.yamulite.ui.main.settings.SettingsScreen

private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Search("search", "بحث", Icons.Outlined.Search, Icons.Filled.Search),
    Favorites("favorites", "المفضلة", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    NowPlaying("now", "التشغيل الآن", Icons.Outlined.PlayArrow, Icons.Filled.PlayArrow),
    Settings("settings", "الإعدادات", Icons.Outlined.Settings, Icons.Filled.Settings),
}

private fun isPushed(route: String?): Boolean =
    route != null && (route.startsWith("artist") || route.startsWith("album"))

/** منحنى حركة التنقّل في iOS (يبدأ سريعاً وينتهي بنعومة). */
private val IosNavEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private const val IosNavMs = 380

@Composable
fun MainScreen() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // يبقى آخر تبويب محدداً حتى عند فتح شاشة فنان أو ألبوم
    var lastTab by remember { mutableIntStateOf(0) }
    Tab.entries.forEachIndexed { i, tab ->
        if (backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true) lastTab = i
    }

    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdrop = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val glassBackdrop = remember(backdrop, blurOk) {
        if (blurOk) GlassBackdrop(backdrop) { contentOrigin } else null
    }
    val dir = LocalLayoutDirection.current

    // شريط التبويبات يصغر عند التمرير للأسفل ويعود عند التمرير للأعلى
    var compact by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) { compact = false }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) compact = true else if (available.y > 6f) compact = false
                return Offset.Zero
            }
        }
    }

    // اتجاه الدفع: من الحافة الخلفية (يمين في LTR، يسار في RTL)
    val sign = if (dir == LayoutDirection.Rtl) -1 else 1

    Scaffold { padding ->
        val navInset = padding.calculateBottomPadding()
        val barBottom = navInset + 10.dp
        val inset = barBottom + GlassBarHeight + 14.dp
        val sides = PaddingValues(
            start = padding.calculateStartPadding(dir),
            end = padding.calculateEndPadding(dir),
        )
        // لا نضيف هامشاً علوياً: كل شاشة تمتد خلف شريط الحالة وتتولى الهامش بنفسها
        Box(Modifier.fillMaxSize().padding(sides)) {
            // المحتوى يمتد خلف الشريط العائم، ونلتقط نسخة منه لتمويهها داخل الزجاج
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection)
                    .onGloballyPositioned { contentOrigin = it.positionInWindow() }
                    .drawWithContent {
                        if (blurOk) {
                            backdrop.record { this@drawWithContent.drawContent() }
                            drawLayer(backdrop)
                        } else {
                            drawContent()
                        }
                    },
            ) {
                CompositionLocalProvider(LocalBottomBarInset provides inset) {
                    NavHost(
                        navController = nav,
                        startDestination = Tab.Search.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(tween(220)) },
                        exitTransition = { fadeOut(tween(220)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(220)) },
                    ) {
                        val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                            if (isPushed(targetState.destination.route)) {
                                slideOutHorizontally(tween(IosNavMs, easing = IosNavEasing)) { -sign * it / 3 } +
                                    fadeOut(tween(IosNavMs))
                            } else fadeOut(tween(220))
                        }
                        val tabPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                            if (isPushed(initialState.destination.route)) {
                                slideInHorizontally(tween(IosNavMs, easing = IosNavEasing)) { -sign * it / 3 } +
                                    fadeIn(tween(IosNavMs))
                            } else fadeIn(tween(220))
                        }
                        val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                            slideInHorizontally(tween(IosNavMs, easing = IosNavEasing)) { sign * it }
                        }
                        val pushPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                            slideOutHorizontally(tween(IosNavMs, easing = IosNavEasing)) { sign * it }
                        }

                        composable(
                            Tab.Search.route,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                        ) {
                            SearchScreen(
                                onArtistClick = { id -> nav.navigate("artist/$id") },
                                onAlbumClick = { id -> nav.navigate("album/$id") },
                            )
                        }
                        composable(
                            Tab.Favorites.route,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                        ) { FavoritesScreen() }
                        composable(
                            Tab.NowPlaying.route,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                        ) {
                            NowPlayingScreen(
                                onArtistClick = { id -> nav.navigate("artist/$id") },
                                onAlbumClick = { id -> nav.navigate("album/$id") },
                            )
                        }
                        composable(
                            Tab.Settings.route,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                        ) { SettingsScreen() }
                        composable(
                            route = "artist/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                            enterTransition = pushEnter,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                            popExitTransition = pushPopExit,
                        ) {
                            ArtistScreen(
                                onBack = { nav.popBackStack() },
                                onAlbumClick = { id -> nav.navigate("album/$id") },
                            )
                        }
                        composable(
                            route = "album/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                            enterTransition = pushEnter,
                            exitTransition = tabExit,
                            popEnterTransition = tabPopEnter,
                            popExitTransition = pushPopExit,
                        ) {
                            AlbumScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }

            // حافة التمرير السفلية خلف الشريط العائم (ما عدا شاشة التشغيل ذات الخلفية الفنية)
            if (currentRoute != Tab.NowPlaying.route) {
                ScrollEdge(
                    backdrop = glassBackdrop,
                    top = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(barBottom + GlassBarHeight + 30.dp),
                )
            }

            GlassTabBar(
                items = Tab.entries.map { GlassTabItem(it.label, it.icon, it.selectedIcon) },
                selectedIndex = lastTab,
                onSelect = { i ->
                    val tab = Tab.entries[i]
                    if (currentRoute != tab.route) {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                backdrop = if (blurOk) backdrop else null,
                contentOrigin = { contentOrigin },
                compact = compact,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = barBottom)
                    .widthIn(max = 460.dp),
            )
        }
    }
}
