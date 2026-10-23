package dev.pdv.yamulite.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pdv.yamulite.data.settings.Quality
import dev.pdv.yamulite.data.settings.ThemeMode
import dev.pdv.yamulite.ui.main.components.GroupedCheckRow
import dev.pdv.yamulite.ui.main.components.GroupedSection
import dev.pdv.yamulite.ui.main.components.LargeTitle
import dev.pdv.yamulite.ui.main.components.LiquidScaffold
import dev.pdv.yamulite.ui.main.components.LiquidTopBar
import dev.pdv.yamulite.ui.main.components.LiquidTopBarHeight
import dev.pdv.yamulite.ui.main.components.LocalBottomBarInset
import dev.pdv.yamulite.ui.main.components.statusBarTop

private const val Title = "الإعدادات"

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val current by vm.quality.collectAsStateWithLifecycle()
    val currentTheme by vm.themeMode.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val titleCollapsed by remember { derivedStateOf { scroll.value > 70 } }

    LiquidScaffold(
        overlay = { backdrop ->
            LiquidTopBar(backdrop, title = Title, titleVisible = titleCollapsed, onBack = null)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = statusBarTop() + LiquidTopBarHeight)
                .padding(bottom = LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LargeTitle(Title)

            GroupedSection(header = "المظهر") {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    GroupedCheckRow(
                        title = mode.label,
                        selected = currentTheme == mode,
                        showDivider = i < ThemeMode.entries.size - 1,
                        onClick = { vm.setThemeMode(mode) },
                    )
                }
            }

            GroupedSection(
                header = "جودة الموسيقى",
                footer = "الموسيقى تُجلب من YouTube Music بدون أي تسجيل دخول. " +
                    "يمكنك تنزيل المقاطع بزر التنزيل بجانب كل مقطع، وتُشغَّل المنزَّلة دون إنترنت.",
            ) {
                Quality.entries.forEachIndexed { i, q ->
                    GroupedCheckRow(
                        title = q.label,
                        selected = current == q,
                        showDivider = i < Quality.entries.size - 1,
                        onClick = { vm.setQuality(q) },
                    )
                }
            }
        }
    }
}
