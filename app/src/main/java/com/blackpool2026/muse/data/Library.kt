package com.blackpool2026.muse.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore.Audio.Media as A
import androidx.core.content.ContextCompat

object Library {
    /** Todo lo escaneado (antes de filtrar por carpetas). */
    @Volatile var all: List<Song> = emptyList(); private set
    /** Canciones visibles según los ajustes de carpetas. */
    @Volatile var songs: List<Song> = emptyList(); private set
    @Volatile var byId: Map<Long, Song> = emptyMap(); private set
    @Volatile var loaded = false; private set

    fun hasPermission(ctx: Context): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureLoaded(ctx: Context) {
        if (!loaded) scan(ctx)
    }

    /** Aplica sólo el filtro de carpetas sin volver a consultar MediaStore. */
    @Synchronized fun refilter() {
        val f = Store.settings.folders
        songs = if (f == null) all else all.filter { f.contains(it.folder) }
        byId = all.associateBy { it.id }
    }

    @Synchronized fun scan(ctx: Context): List<Song> {
        val s = Store.settings
        val out = ArrayList<Song>()
        if (hasPermission(ctx)) {
            val proj = arrayOf(
                A._ID, A.TITLE, A.ARTIST, A.ALBUM, A.ALBUM_ID, A.DURATION, A.DATA,
                A.DATE_ADDED, A.TRACK, A.YEAR, A.SIZE, A.MIME_TYPE
            )
            val sel = StringBuilder("${A.DURATION} >= ?")
            if (s.includeAll) sel.append(" AND ${A.IS_RINGTONE} = 0 AND ${A.IS_NOTIFICATION} = 0 AND ${A.IS_ALARM} = 0")
            else sel.append(" AND ${A.IS_MUSIC} != 0")
            try {
                ctx.contentResolver.query(
                    A.EXTERNAL_CONTENT_URI, proj, sel.toString(),
                    arrayOf((s.minDurSec * 1000L).toString()), null
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(A._ID)
                    val iTitle = c.getColumnIndexOrThrow(A.TITLE)
                    val iArtist = c.getColumnIndexOrThrow(A.ARTIST)
                    val iAlbum = c.getColumnIndexOrThrow(A.ALBUM)
                    val iAlbumId = c.getColumnIndexOrThrow(A.ALBUM_ID)
                    val iDur = c.getColumnIndexOrThrow(A.DURATION)
                    val iData = c.getColumnIndexOrThrow(A.DATA)
                    val iAdded = c.getColumnIndexOrThrow(A.DATE_ADDED)
                    val iTrack = c.getColumnIndexOrThrow(A.TRACK)
                    val iYear = c.getColumnIndexOrThrow(A.YEAR)
                    val iSize = c.getColumnIndexOrThrow(A.SIZE)
                    val iMime = c.getColumnIndexOrThrow(A.MIME_TYPE)
                    while (c.moveToNext()) {
                        val path = c.getString(iData) ?: ""
                        val rawTitle = c.getString(iTitle)
                        val title = if (rawTitle.isNullOrBlank() || rawTitle == "<unknown>")
                            path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Sin título" }
                        else rawTitle
                        val artist = c.getString(iArtist).let { if (it.isNullOrBlank() || it == "<unknown>") UNKNOWN_ARTIST else it }
                        val album = c.getString(iAlbum).let { if (it.isNullOrBlank() || it == "<unknown>") UNKNOWN_ALBUM else it }
                        out.add(
                            Song(
                                id = c.getLong(iId), title = title, artist = artist, album = album,
                                albumId = c.getLong(iAlbumId), durationMs = c.getLong(iDur), path = path,
                                dateAdded = c.getLong(iAdded), track = c.getInt(iTrack) % 1000,
                                year = c.getInt(iYear), size = c.getLong(iSize), mime = c.getString(iMime) ?: ""
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
        }
        all = out
        loaded = true
        refilter()
        return songs
    }
}
