package dev.pdv.yamulite.data.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import dev.pdv.yamulite.data.music.StreamUrlResolver
import dev.pdv.yamulite.data.settings.Quality
import dev.pdv.yamulite.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolvingDataSourceFactory @Inject constructor(
    private val resolver: StreamUrlResolver,
    settings: SettingsStore,
    private val downloads: DownloadManager,
) : DataSource.Factory {

    // Kept current reactively so open() never needs runBlocking for settings reads
    @Volatile private var currentQuality: Quality = Quality.High

    private class CachedStream(val url: String, val userAgent: String, val at: Long)

    // LRU cache of resolved stream URLs — repeated plays skip the resolve step.
    // YouTube URLs expire after a few hours, so entries older than 2h are ignored.
    private val urlCache = object : LinkedHashMap<String, CachedStream>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedStream>) = size > 30
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { settings.quality.collect { currentQuality = it } }
    }

    override fun createDataSource(): DataSource = ResolvingDataSource()

    private fun httpSource(userAgent: String): DataSource =
        DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .createDataSource()

    private inner class ResolvingDataSource : DataSource {
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            if (dataSpec.uri.scheme != "yamulite") {
                val ds = httpSource(NewPipeUa)
                delegate = ds
                return ds.open(dataSpec)
            }

            val trackId = dataSpec.uri.lastPathSegment
                ?: throw IOException("Missing track id in ${dataSpec.uri}")

            val localPath = downloads.localPath(trackId)
            val ds: DataSource
            val realUri: Uri
            if (localPath != null) {
                realUri = Uri.fromFile(File(localPath))
                ds = FileDataSource()
            } else {
                val cacheKey = "$trackId:${currentQuality.name}"
                val now = System.currentTimeMillis()
                val cached = synchronized(urlCache) { urlCache[cacheKey] }
                    ?.takeIf { now - it.at < 2 * 60 * 60 * 1000L }
                val stream = cached ?: run {
                    val resolved = try {
                        runBlocking { resolver.resolve(trackId, currentQuality.bitrate) }
                    } catch (e: Exception) {
                        throw IOException("Could not resolve stream URL for track $trackId: ${e.message}", e)
                    } ?: throw IOException("Could not resolve stream URL for track $trackId")
                    CachedStream(resolved.url, resolved.userAgent, now).also { c ->
                        synchronized(urlCache) { urlCache[cacheKey] = c }
                    }
                }
                realUri = Uri.parse(stream.url)
                ds = httpSource(stream.userAgent)
            }

            delegate = ds
            return ds.open(dataSpec.buildUpon().setUri(realUri).build())
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = delegate?.uri

        override fun close() {
            delegate?.close()
            delegate = null
        }
    }

    private companion object {
        const val NewPipeUa = dev.pdv.yamulite.data.yt.NewPipeDownloader.USER_AGENT
    }
}
