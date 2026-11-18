package com.blackpool2026.muse.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Cargador de portadas con tres capas: memoria (LRU), disco (JPEG) y extracción.
 * Orden de extracción: miniatura del sistema -> imagen incrustada -> carpeta -> albumart antiguo.
 */
object Covers {
    private lateinit var dir: File
    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val GENERIC_FOLDERS = setOf(
        "download", "downloads", "telegram", "music", "musica", "música", "audio", "audios", "sounds",
        "documents", "documentos", "bluetooth", "whatsapp audio", "media", "0", "sdcard", "storage", "emulated"
    )
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val locks = ConcurrentHashMap<String, Any>()

    private lateinit var customDir: File
    private lateinit var customIndex: File
    /** id de canción -> nombre de archivo de su portada personalizada. */
    private val custom = ConcurrentHashMap<Long, String>()

    /** Cambia cuando se pone o quita una portada personalizada, para refrescar la interfaz. */
    var customRev by mutableIntStateOf(0)
        private set

    fun init(ctx: Context) {
        dir = File(ctx.cacheDir, "covers").apply { mkdirs() }
        // Borra el caché antiguo (claves por álbum) que causaba portadas repetidas.
        dir.listFiles()?.forEach { if (!it.name.startsWith("v2_")) it.delete() }
        customDir = File(ctx.filesDir, "covers_custom").apply { mkdirs() }
        customIndex = File(ctx.filesDir, "covers_custom.json")
        try {
            if (customIndex.exists()) {
                val o = JSONObject(customIndex.readText())
                o.keys().forEach { k -> k.toLongOrNull()?.let { custom[it] = o.getString(k) } }
            }
        } catch (_: Throwable) {}
    }

    fun hasCustom(id: Long): Boolean = custom.containsKey(id)

    private fun customFile(id: Long): File? = custom[id]?.let { File(customDir, it).takeIf { f -> f.exists() } }

    private fun saveIndex() {
        try {
            val o = JSONObject()
            custom.forEach { (k, v) -> o.put(k.toString(), v) }
            customIndex.writeText(o.toString())
        } catch (_: Throwable) {}
    }

    private fun refreshAfterCustomChange() {
        mem.evictAll()
        misses.clear()
    }

    /** Guarda una imagen elegida por el usuario como portada de todas esas canciones. Ejecutar fuera del hilo principal. */
    fun setCustom(ctx: Context, ids: List<Long>, uri: Uri): Boolean {
        return try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val src = decodeBytes(bytes, 640) ?: return false
            val side = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = if (side > 640) Bitmap.createScaledBitmap(sq, 640, 640, true) else sq
            val name = "c${System.currentTimeMillis()}.jpg"
            FileOutputStream(File(customDir, name)).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val old = ids.mapNotNull { custom[it] }.toSet()
            ids.forEach { custom[it] = name }
            old.forEach { deleteIfUnused(it) }
            saveIndex()
            refreshAfterCustomChange()
            true
        } catch (_: Throwable) { false }
    }

    fun removeCustom(ids: List<Long>) {
        val old = ids.mapNotNull { custom.remove(it) }.toSet()
        old.forEach { deleteIfUnused(it) }
        saveIndex()
        refreshAfterCustomChange()
    }

    private fun deleteIfUnused(name: String) {
        if (!custom.values.contains(name)) try { File(customDir, name).delete() } catch (_: Throwable) {}
    }

    /** Llamar desde el hilo principal tras un cambio para que las imágenes se recarguen. */
    fun notifyChanged() { customRev++ }

    private fun bucket(px: Int): Int = when {
        px <= 96 -> 96
        px <= 192 -> 192
        px <= 384 -> 384
        else -> 640
    }

    fun peek(song: Song, px: Int): Bitmap? = mem.get("${song.coverKey}@${bucket(px)}")

    fun get(ctx: Context, song: Song, px: Int): Bitmap? {
        val b = bucket(px)
        val memKey = "${song.coverKey}@$b"
        mem.get(memKey)?.let { return it }
        customFile(song.id)?.let { cf ->
            val bmp = decodeFile(cf.absolutePath, b)
            if (bmp != null) { mem.put(memKey, bmp); return bmp }
        }
        if (misses.contains(song.coverKey)) return null
        synchronized(locks.getOrPut(song.coverKey) { Any() }) {
            mem.get(memKey)?.let { return it }
            val f = File(dir, song.coverKey + ".jpg")
            if (!f.exists() || f.length() < 100) {
                val src = try { extract(ctx, song, 640) } catch (_: Throwable) { null }
                if (src == null) {
                    misses.add(song.coverKey)
                    return null
                }
                try {
                    FileOutputStream(f).use { src.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } catch (_: Throwable) {}
                if (b >= 640) { mem.put(memKey, src); return src }
            }
            val bmp = decodeFile(f.absolutePath, b) ?: run {
                f.delete()
                return null
            }
            mem.put(memKey, bmp)
            return bmp
        }
    }

    fun clearAll() {
        mem.evictAll()
        misses.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun clearMisses() { misses.clear() }

    fun cacheInfo(): Pair<Int, Long> {
        val files = dir.listFiles() ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }

    private fun extract(ctx: Context, song: Song, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try { return ctx.contentResolver.loadThumbnail(song.uri, Size(size, size), null) } catch (_: Throwable) {}
        }
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, song.uri)
                val d = r.embeddedPicture
                if (d != null) decodeBytes(d, size)?.let { return it }
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        // Imagen suelta en la carpeta: solo si la carpeta es de un álbum (no una carpeta genérica
        // como Descargas, donde una sola imagen se repetiría en todas las canciones).
        try {
            val folder = File(song.folder)
            if (folder.name.lowercase() !in GENERIC_FOLDERS) {
                for (n in listOf("cover", "folder", "front", "album", "albumart", "artwork")) {
                    for (e in listOf("jpg", "jpeg", "png")) {
                        val f = File(folder, "$n.$e")
                        if (f.exists()) decodeFile(f.absolutePath, size)?.let { return it }
                    }
                }
            }
        } catch (_: Throwable) {}
        // El albumart antiguo se comparte entre todas las canciones del mismo álbum: solo en Android < 10.
        if (Build.VERSION.SDK_INT < 29 && song.albumId > 0) {
            try {
                val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                ctx.contentResolver.openInputStream(u)?.use { s ->
                    val bytes = s.readBytes()
                    decodeBytes(bytes, size)?.let { return it }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun sampleFor(w: Int, h: Int, target: Int): Int {
        var s = 1
        val m = minOf(w, h)
        while (m / (s * 2) >= target) s *= 2
        return s
    }

    fun decodeBytes(d: ByteArray, target: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(d, 0, d.size, o)
        if (o.outWidth <= 0) return null
        val o2 = BitmapFactory.Options().apply { inSampleSize = sampleFor(o.outWidth, o.outHeight, target) }
        return BitmapFactory.decodeByteArray(d, 0, d.size, o2)
    }

    fun decodeFile(path: String, target: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        val o2 = BitmapFactory.Options().apply { inSampleSize = sampleFor(o.outWidth, o.outHeight, target) }
        return BitmapFactory.decodeFile(path, o2)
    }

    /** Color dominante vivo de una portada (para teñir el reproductor). */
    fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 12, 12, true)
        var r = 0.0; var g = 0.0; var bl = 0.0; var wsum = 0.0
        val hsv = FloatArray(3)
        for (x in 0 until 12) for (y in 0 until 12) {
            val c = s.getPixel(x, y)
            Color.colorToHSV(c, hsv)
            val w = (hsv[1] * (0.3f + hsv[2])).toDouble() + 0.02
            r += Color.red(c) * w; g += Color.green(c) * w; bl += Color.blue(c) * w; wsum += w
        }
        if (s !== b) s.recycle()
        return Color.rgb((r / wsum).toInt(), (g / wsum).toInt(), (bl / wsum).toInt())
    }

    /** Imagen generada cuando una canción no tiene portada. */
    fun placeholder(seed: String, size: Int): Bitmap {
        val h = (seed.hashCode() and 0x7FFFFFFF) % 360
        val c1 = Color.HSVToColor(floatArrayOf(h.toFloat(), 0.55f, 0.45f))
        val c2 = Color.HSVToColor(floatArrayOf(((h + 40) % 360).toFloat(), 0.7f, 0.18f))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        p.color = Color.argb(200, 255, 255, 255)
        val u = size / 100f
        c.drawCircle(42 * u, 68 * u, 11 * u, p)
        c.drawRect(50 * u, 28 * u, 55 * u, 68 * u, p)
        c.drawRect(50 * u, 28 * u, 70 * u, 36 * u, p)
        return bmp
    }
}
