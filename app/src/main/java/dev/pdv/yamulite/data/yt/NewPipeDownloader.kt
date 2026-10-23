package dev.pdv.yamulite.data.yt

import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** OkHttp bridge that lets NewPipeExtractor talk to YouTube / YouTube Music. */
@Singleton
class NewPipeDownloader @Inject constructor(
    baseClient: OkHttpClient,
) : Downloader() {

    // Own client: no response cache (YouTube responses must be fresh) and a longer read timeout.
    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()

        val builder = Builder()
            .method(method, body)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val headers = resp.headers.toMultimap()
            return Response(
                resp.code,
                resp.message,
                headers,
                resp.body?.string(),
                resp.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
