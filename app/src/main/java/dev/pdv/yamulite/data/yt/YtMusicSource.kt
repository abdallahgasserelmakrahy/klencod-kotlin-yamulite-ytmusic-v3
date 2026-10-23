package dev.pdv.yamulite.data.yt

import dev.pdv.yamulite.data.music.dto.AlbumDto
import dev.pdv.yamulite.data.music.dto.AlbumShortDto
import dev.pdv.yamulite.data.music.dto.AlbumWithTracksDto
import dev.pdv.yamulite.data.music.dto.ArtistBriefDto
import dev.pdv.yamulite.data.music.dto.ArtistDto
import dev.pdv.yamulite.data.music.dto.ArtistShortDto
import dev.pdv.yamulite.data.music.dto.TrackDto
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

enum class YtSearchKind(internal val filter: String) {
    Songs(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
    Albums(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
    Artists(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
}

class YtSearchPage(
    val items: List<InfoItem>,
    internal val handler: SearchQueryHandler,
    internal val next: Page?,
) {
    val hasMore: Boolean get() = next != null
}

data class YtStream(val url: String, val userAgent: String, val extension: String, val bitrateKbps: Int)

/**
 * Everything that talks to YouTube Music, via NewPipeExtractor. No account, token or API key.
 * All functions are blocking — call them from Dispatchers.IO.
 */
@Singleton
class YtMusicSource @Inject constructor(
    downloader: NewPipeDownloader,
    okHttp: okhttp3.OkHttpClient,
) {

    private val fastResolver = VisionOsStreamResolver(okHttp)

    private val service = ServiceList.YouTube

    init {
        NewPipe.init(downloader, Localization.DEFAULT, ContentCountry.DEFAULT)
    }

    // ---- Search --------------------------------------------------------------------------

    fun search(text: String, kind: YtSearchKind): YtSearchPage {
        val handler = service.searchQHFactory.fromQuery(text, listOf(kind.filter), "")
        val info = SearchInfo.getInfo(service, handler)
        return YtSearchPage(info.relatedItems.orEmpty(), handler, info.nextPage)
    }

    fun searchMore(prev: YtSearchPage): YtSearchPage {
        val page = prev.next ?: return YtSearchPage(emptyList(), prev.handler, null)
        val res = SearchInfo.getMoreItems(service, prev.handler, page)
        return YtSearchPage(res.items.orEmpty(), prev.handler, res.nextPage)
    }

    // ---- Details -------------------------------------------------------------------------

    fun album(albumId: String): AlbumWithTracksDto {
        val info = PlaylistInfo.getInfo(service, "https://www.youtube.com/playlist?list=$albumId")
        val items = ArrayList<StreamInfoItem>(info.relatedItems.orEmpty())
        var next: Page? = info.nextPage
        var guard = 0
        while (next != null && guard++ < 8) {
            val more = PlaylistInfo.getMoreItems(service, "https://www.youtube.com/playlist?list=$albumId", next)
            items += more.items.orEmpty()
            next = more.nextPage
        }
        val cover = bestImage(info.thumbnails)
        val albumName = info.name.orEmpty()
        val albumArtist = info.uploaderName?.cleanArtist().orEmpty()
        val tracks = items.mapNotNull { it.toTrack() }.map { t ->
            t.copy(
                coverUri = t.coverUri ?: cover,
                albums = listOf(AlbumShortDto(albumId, albumName, cover)),
                artists = t.artists.ifEmpty { listOf(ArtistShortDto(name = albumArtist)) },
            )
        }
        return AlbumWithTracksDto(
            id = albumId,
            title = albumName,
            coverUri = cover,
            artists = listOf(ArtistShortDto(channelId(info.uploaderUrl), albumArtist)),
            volumes = listOf(tracks),
        )
    }

    fun artist(channelId: String): ArtistBriefDto {
        val ch = ChannelInfo.getInfo(service, "https://www.youtube.com/channel/$channelId")
        val name = ch.name.orEmpty().cleanArtist()
        val avatar = bestImage(ch.avatars)

        val songs = runCatching { search(name, YtSearchKind.Songs).items }.getOrDefault(emptyList())
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
        val ownSongs = songs.filter { t -> t.artists.any { it.id == channelId || it.name.equals(name, true) } }

        val albums = runCatching { search(name, YtSearchKind.Albums).items }.getOrDefault(emptyList())
            .filterIsInstance<PlaylistInfoItem>()
            .mapNotNull { it.toAlbum() }
        val ownAlbums = albums.filter { a -> a.artists.any { it.name.contains(name, true) } }

        return ArtistBriefDto(
            artist = ArtistDto(id = channelId, name = name, ogImage = avatar),
            popularTracks = ownSongs.ifEmpty { songs },
            albums = ownAlbums.ifEmpty { albums },
        )
    }

    /**
     * "Radio" for endless playback: YouTube's auto-generated mix for [seedId]. Mixes keep
     * producing related songs, so chaining calls with the last queued track never runs dry.
     */
    fun radio(seedId: String): List<TrackDto> {
        val info = PlaylistInfo.getInfo(service, "https://www.youtube.com/watch?v=$seedId&list=RD$seedId")
        return info.relatedItems.orEmpty().mapNotNull { it.toTrack() }
    }

    // ---- Streams -------------------------------------------------------------------------

    /** Picks the best audio-only stream not above [maxKbps] (or the smallest one if all are above). */
    fun audioStream(videoId: String, maxKbps: Int): YtStream {
        // 1) Fast anonymous path (VisionOS client); 2) NewPipeExtractor as the fallback.
        fastResolver.resolve(videoId, maxKbps)?.let {
            return YtStream(it.url, VisionOsStreamResolver.USER_AGENT, it.extension, it.kbps)
        }
        val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val candidates = extractor.audioStreams.orEmpty().filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrBlank()
        }
        if (candidates.isEmpty()) error("لا توجد روابط صوت متاحة لهذا المقطع")

        fun kbps(bitrate: Int) = if (bitrate > 0) bitrate else 128
        val within = candidates.filter { kbps(it.averageBitrate) <= maxKbps }
        val pick = if (within.isNotEmpty()) {
            within.sortedWith(
                compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { kbps(it.averageBitrate) }
                    .thenByDescending { it.format?.suffix == "m4a" }
            ).first()
        } else {
            candidates.minByOrNull { kbps(it.averageBitrate) }!!
        }
        val url = pick.content
        return YtStream(
            url = url,
            userAgent = userAgentFor(url),
            extension = pick.format?.suffix ?: "m4a",
            bitrateKbps = kbps(pick.averageBitrate),
        )
    }

    private fun userAgentFor(url: String): String = when {
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        url.contains("&c=ANDROID") -> ANDROID_UA
        url.contains("&c=IOS") -> IOS_UA
        else -> NewPipeDownloader.USER_AGENT
    }

    // ---- Mapping -------------------------------------------------------------------------

    fun trackOf(item: InfoItem): TrackDto? = (item as? StreamInfoItem)?.toTrack()
    fun albumOf(item: InfoItem): AlbumDto? = (item as? PlaylistInfoItem)?.toAlbum()
    fun artistOf(item: InfoItem): ArtistDto? = (item as? ChannelInfoItem)?.toArtist()

    private fun StreamInfoItem.toTrack(): TrackDto? {
        val id = videoId(url) ?: return null
        val artistName = uploaderName?.cleanArtist().orEmpty()
        val artists = if (artistName.isBlank()) emptyList()
        else listOf(ArtistShortDto(id = channelId(runCatching { uploaderUrl }.getOrNull()), name = artistName))
        return TrackDto(
            id = id,
            title = name,
            coverUri = bestImage(thumbnails),
            durationMs = duration.takeIf { it > 0 }?.times(1000),
            artists = artists,
            available = true,
        )
    }

    private fun PlaylistInfoItem.toAlbum(): AlbumDto? {
        val id = Regex("[?&]list=([\\w-]+)").find(url)?.groupValues?.get(1) ?: return null
        return AlbumDto(
            id = id,
            title = name.orEmpty(),
            coverUri = bestImage(thumbnails),
            artists = uploaderName?.cleanArtist()?.takeIf { it.isNotBlank() }
                ?.let { listOf(ArtistShortDto(name = it)) } ?: emptyList(),
        )
    }

    private fun ChannelInfoItem.toArtist(): ArtistDto? {
        val id = channelId(url)
        if (id.isBlank()) return null
        return ArtistDto(id = id, name = name.orEmpty().cleanArtist(), ogImage = bestImage(thumbnails))
    }

    private fun String.cleanArtist() = removeSuffix(" - Topic").trim()

    private fun videoId(url: String?): String? =
        url?.let { Regex("[?&]v=([\\w-]{11})").find(it)?.groupValues?.get(1) }

    private fun channelId(url: String?): String =
        url?.let { Regex("/(?:channel|browse)/(UC[\\w-]+)").find(it)?.groupValues?.get(1) }.orEmpty()

    private fun bestImage(images: List<Image>?): String? =
        images?.filter { it.url.isNotBlank() }
            ?.maxByOrNull { maxOf(it.height, 0) * maxOf(it.width, 0) }
            ?.url
            ?: images?.firstOrNull()?.url

    companion object {
        private const val ANDROID_UA = "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"
        private const val IOS_UA =
            "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
    }
}
