package com.blackpool2026.muse.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.text.Normalizer

const val UNKNOWN_ARTIST = "Artista desconocido"
const val UNKNOWN_ALBUM = "Álbum desconocido"

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val dateAdded: Long,
    val track: Int,
    val year: Int,
    val size: Long,
    val mime: String
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val folder: String = path.substringBeforeLast('/', "")
    /** Clave de portada POR CANCIÓN: antes era por álbum y canciones distintas compartían imagen. */
    val coverKey: String = "v2_${id}_$size"
    val albumKey: Long = if (albumId > 0 && album != UNKNOWN_ALBUM) albumId else -(album.hashCode().toLong() and 0xFFFFFFL) - 1L
    val searchKey: String by lazy { norm("$title $artist $album") }
}

data class AlbumInfo(val key: Long, val name: String, val artist: String, val songs: List<Song>)
data class ArtistInfo(val name: String, val songs: List<Song>, val albums: Int)
data class FolderInfo(val path: String, val songs: List<Song>) {
    val name: String get() = path.substringAfterLast('/', path).ifBlank { "/" }
}
data class Playlist(val id: Long, val name: String, val ids: List<Long>)

fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%d KB".format(bytes / 1024)
}
