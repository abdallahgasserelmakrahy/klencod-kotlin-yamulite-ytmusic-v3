@file:Suppress("DEPRECATION")

package dev.pdv.yamulite.data.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.session.BitmapLoader
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

@Suppress("DEPRECATION")
class CoilBitmapLoader(private val context: Context) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        SettableFuture.create<Bitmap>().also {
            it.setException(UnsupportedOperationException("embedded artwork not supported"))
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(uri.toString())
            .target(
                onSuccess = { image ->
                    val bm = (image as? BitmapImage)?.bitmap
                    if (bm != null) future.set(bm)
                    else future.setException(Exception("Not a BitmapImage: $uri"))
                },
                onError = {
                    future.setException(Exception("Failed to load artwork: $uri"))
                },
            )
            .build()
        imageLoader.enqueue(request)
        return future
    }
}
