package com.blackpool2026.muse.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(Uri.parse("muse-cover://song/$id"))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()

/**
 * Carga las portadas de la notificación, pantalla de bloqueo y widget con el mismo
 * cargador que usa la app, así que siempre coinciden.
 */
@UnstableApi
class MuseBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec = Executors.newFixedThreadPool(2)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val f = SettableFuture.create<Bitmap>()
        exec.execute {
            try {
                val b = Covers.decodeBytes(data, 640)
                if (b != null) f.set(b) else f.setException(IllegalStateException("decode"))
            } catch (e: Throwable) { f.setException(e) }
        }
        return f
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val f = SettableFuture.create<Bitmap>()
        exec.execute {
            try {
                val id = uri.lastPathSegment?.toLongOrNull()
                Library.ensureLoaded(ctx)
                val song = id?.let { Library.byId[it] }
                val bmp = song?.let { Covers.get(ctx, it, 640) }
                    ?: Covers.placeholder(song?.title ?: "muse", 512)
                f.set(bmp)
            } catch (e: Throwable) { f.setException(e) }
        }
        return f
    }
}
