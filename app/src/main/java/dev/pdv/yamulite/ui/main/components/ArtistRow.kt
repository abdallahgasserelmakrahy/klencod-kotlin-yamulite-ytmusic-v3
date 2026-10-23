package dev.pdv.yamulite.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pdv.yamulite.data.music.dto.AlbumDto
import dev.pdv.yamulite.data.music.dto.ArtistDto
import dev.pdv.yamulite.ui.theme.LocalIos

/** الفنان: صورة دائرية كما في تطبيق الموسيقى في iOS. */
@Composable
fun ArtistRow(artist: ArtistDto, onClick: () -> Unit = {}) {
    val ios = LocalIos.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .iosRowClick(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CoverImage(
                coverUri = artist.cover?.uri ?: artist.ogImage,
                side = 52.dp,
                cornerRadius = 26.dp,
            )
            Text(
                artist.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = ios.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Chevron(color = ios.tertiaryLabel)
        }
        HairlineDivider(Modifier.padding(start = RowSeparatorInset))
    }
}

@Composable
fun AlbumRow(album: AlbumDto, onClick: () -> Unit = {}) {
    val ios = LocalIos.current
    val artists = remember(album.id, album.artists) { album.artists.joinToString("، ") { it.name } }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .iosRowClick(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CoverImage(coverUri = album.coverUri, side = 52.dp, cornerRadius = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ios.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artists.isNotBlank()) {
                    Text(
                        artists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ios.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Chevron(color = ios.tertiaryLabel)
        }
        HairlineDivider(Modifier.padding(start = RowSeparatorInset))
    }
}
