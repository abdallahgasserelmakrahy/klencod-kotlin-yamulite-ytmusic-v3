package dev.pdv.yamulite.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pdv.yamulite.data.music.dto.TrackDto
import dev.pdv.yamulite.data.playback.DownloadInfo
import dev.pdv.yamulite.data.playback.DownloadState
import dev.pdv.yamulite.ui.theme.LocalIos

fun TrackDto.displayLine(): String {
    val artist = artists.joinToString(", ") { it.name }
    val t = title.orEmpty()
    return when {
        artist.isBlank() && t.isBlank() -> "(بدون عنوان)"
        artist.isBlank() -> t
        t.isBlank() -> artist
        else -> "$artist — $t"
    }
}

/** بداية الفاصل: هامش الصف + الغلاف + المسافة، فيبدأ الخط عند النص كما في iOS. */
val RowSeparatorInset = 80.dp

@Composable
fun TrackRow(
    track: TrackDto,
    isLiked: Boolean,
    download: DownloadInfo?,
    onClick: () -> Unit,
    onLikeToggle: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val ios = LocalIos.current
    val cover = track.coverUri ?: track.albums.firstOrNull()?.coverUri
    val title = remember(track.id, track.title) { track.title.orEmpty() }
    val artists = remember(track.id, track.artists) { track.artists.joinToString("، ") { it.name } }
    val fallback = remember(track.id, track.title, track.artists) { track.displayLine() }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .iosRowClick(onClick = onClick)
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            CoverImage(coverUri = cover, side = 52.dp, cornerRadius = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = if (title.isNotBlank()) title else fallback,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ios.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title.isNotBlank() && artists.isNotBlank()) {
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ios.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FavoriteToggleButton(isLiked = isLiked, onToggle = onLikeToggle, modifier = Modifier.size(44.dp))
            DownloadButton(download = download, onClick = onDownloadClick)
        }
        HairlineDivider(Modifier.padding(start = RowSeparatorInset))
    }
}

@Composable
private fun DownloadButton(download: DownloadInfo?, onClick: () -> Unit) {
    val ios = LocalIos.current
    when (download?.state) {
        null -> LiquidIconButton(
            icon = Icons.Rounded.CloudDownload,
            contentDescription = "تنزيل",
            onClick = onClick,
            tint = ios.tint,
        )
        DownloadState.Failed -> LiquidIconButton(
            icon = Icons.Rounded.ErrorOutline,
            contentDescription = "إعادة التنزيل",
            onClick = onClick,
            tint = ios.red,
        )
        DownloadState.Downloading -> Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            IosProgressRing(progress = if (download.progress > 0f) download.progress else null)
        }
        DownloadState.Done -> LiquidIconButton(
            icon = Icons.Rounded.CloudDone,
            contentDescription = "حذف التنزيل",
            onClick = onClick,
            tint = ios.tint,
        )
    }
}
