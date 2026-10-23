package dev.pdv.yamulite.data.playback

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pdv.yamulite.data.music.StreamUrlResolver
import dev.pdv.yamulite.data.settings.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val resolver: StreamUrlResolver,
    private val settings: SettingsStore,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val baseDir = File(applicationContext.filesDir, "tracks").also { it.mkdirs() }
        val safeName = trackId

        val q = settings.currentQuality()
        val resolved = try {
            resolver.resolve(trackId, q.bitrate)
        } catch (e: Exception) {
            null
        } ?: return Result.failure(workDataOf(KEY_ERROR to "تعذّر الحصول على الرابط"))

        val ext = resolved.codec.toFileExtension()
        val dest = File(baseDir, "$safeName.$ext")
        val tmp = File(baseDir, "$safeName.$ext.part")

        return try {
            okHttp.newCall(Request.Builder().url(resolved.url).header("User-Agent", resolved.userAgent).build()).execute().use { resp ->
                val body = resp.body ?: error("استجابة فارغة من الخادم")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var soFar = 0L
                        var lastReportAt = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            soFar += n
                            if (total > 0) {
                                // Throttled to a few updates/sec — a progress bar can't show
                                // the difference between this and per-chunk updates anyway,
                                // but each update triggers a WorkInfo emission app-wide.
                                val now = System.currentTimeMillis()
                                if (now - lastReportAt >= 200) {
                                    lastReportAt = now
                                    setProgress(workDataOf(KEY_PROGRESS to (soFar.toFloat() / total).coerceIn(0f, 1f)))
                                }
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            Result.success(workDataOf(KEY_LOCAL_PATH to dest.absolutePath))
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "خطأ في التحميل")))
        }
    }

    companion object {
        const val KEY_TRACK_ID = "trackId"
        const val KEY_PROGRESS = "progress"
        const val KEY_LOCAL_PATH = "localPath"
        const val KEY_ERROR = "error"
    }
}

private fun String.toFileExtension() = when (lowercase()) {
    "webm" -> "webm"
    "mp3" -> "mp3"
    else -> "m4a"
}
