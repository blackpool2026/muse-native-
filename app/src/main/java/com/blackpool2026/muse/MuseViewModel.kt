package com.blackpool2026.muse

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackpool2026.muse.data.AlbumInfo
import com.blackpool2026.muse.data.ArtistInfo
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.FolderInfo
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.data.Playlist
import com.blackpool2026.muse.data.Settings
import com.blackpool2026.muse.data.Song
import com.blackpool2026.muse.data.Store
import com.blackpool2026.muse.data.UNKNOWN_ARTIST
import com.blackpool2026.muse.data.norm
import com.blackpool2026.muse.player.AudioFx
import com.blackpool2026.muse.player.PlayerConnection
import com.blackpool2026.muse.widget.MuseWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Collator
import java.util.Locale

sealed class Route {
    data class Album(val key: Long) : Route()
    data class Artist(val name: String) : Route()
    data class Folder(val path: String) : Route()
    data class Playlist(val id: Long) : Route()
    data class Smart(val kind: String) : Route()
    data object Settings : Route()
    data object Themes : Route()
    data object Eq : Route()
    data object FolderPicker : Route()
}

class MuseViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app
    val conn = PlayerConnection(app)

    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var visible by mutableStateOf<List<Song>>(emptyList()); private set
    var albums by mutableStateOf<List<AlbumInfo>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistInfo>>(emptyList()); private set
    var folders by mutableStateOf<List<FolderInfo>>(emptyList()); private set
    var allFolders by mutableStateOf<List<FolderInfo>>(emptyList()); private set
    var scanned by mutableStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var settings by mutableStateOf(Store.settings); private set
    var favorites by mutableStateOf(Store.favorites); private set
    var playlists by mutableStateOf<List<Playlist>>(Store.playlists); private set
    var plays by mutableStateOf(Store.plays); private set
    var hasPerm by mutableStateOf(Library.hasPermission(app))
    @set:JvmName("assignQuery")
    var query by mutableStateOf("")
    var tab by mutableStateOf(0)
    var playerOpen by mutableStateOf(false)
    var selection by mutableStateOf<Set<Long>>(emptySet())
    val route = mutableListOf<Route>()
    var routeTick by mutableStateOf(0); private set
    var custom by mutableStateOf(customImageFile().absolutePath); private set
    var pickerIds by mutableStateOf<List<Long>?>(null)
    var searchOpen by mutableStateOf(false)

    private var recomputeJob: Job? = null
    private val collator: Collator = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }

    init {
        conn.connect()
        viewModelScope.launch {
            Store.rev.collect {
                settings = Store.settings
                favorites = Store.favorites
                playlists = Store.playlists
                plays = Store.plays
                recomputeVisible()
            }
        }
        if (hasPerm) scan(false)
    }

    override fun onCleared() {
        conn.release()
        super.onCleared()
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // ---------- navegación ----------
    val current: Route? get() { routeTick; return route.lastOrNull() }
    fun push(r: Route) { route.add(r); routeTick++; clearSelection() }
    fun pop(): Boolean {
        if (route.isEmpty()) return false
        route.removeAt(route.size - 1); routeTick++; clearSelection(); return true
    }

    // ---------- biblioteca ----------
    fun onPermission() {
        hasPerm = Library.hasPermission(ctx)
        if (hasPerm) scan(false)
    }

    fun scan(full: Boolean) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (full) Covers.clearAll() else Covers.clearMisses()
                Library.scan(ctx)
            }
            applyLibrary()
            loading = false
        }
    }

    /** Se llama cuando MediaStore avisa de cambios (música nueva o borrada). */
    fun onMediaChanged() { if (hasPerm && !loading) scan(false) }

    private fun applyLibrary() {
        songs = Library.songs
        scanned = Library.all.size
        viewModelScope.launch {
            val list = Library.songs
            val all = Library.all
            val g = withContext(Dispatchers.Default) {
                val al = list.groupBy { it.albumKey }.map { (k, v) ->
                    val artistSet = v.map { it.artist }.distinct()
                    AlbumInfo(k, v[0].album, if (artistSet.size == 1) artistSet[0] else "Varios artistas",
                        v.sortedWith(compareBy<Song>({ it.track }, { it.title })))
                }.sortedWith { a, b -> collator.compare(a.name, b.name) }
                val ar = list.groupBy { it.artist }.map { (n, v) ->
                    ArtistInfo(n, v, v.map { it.albumKey }.distinct().size)
                }.sortedWith { a, b ->
                    if (a.name == UNKNOWN_ARTIST) 1 else if (b.name == UNKNOWN_ARTIST) -1 else collator.compare(a.name, b.name)
                }
                val fo = list.groupBy { it.folder }.map { (p, v) -> FolderInfo(p, v) }
                    .sortedWith { a, b -> collator.compare(a.name, b.name) }
                val fa = all.groupBy { it.folder }.map { (p, v) -> FolderInfo(p, v) }
                    .sortedWith { a, b -> collator.compare(a.name, b.name) }
                Triple(al, ar, fo) to fa
            }
            albums = g.first.first; artists = g.first.second; folders = g.first.third; allFolders = g.second
            recomputeVisible()
        }
    }

    fun sortSongs(list: List<Song>, s: Settings = settings): List<Song> {
        val cmp: Comparator<Song> = when (s.sortKey) {
            "artist" -> Comparator { a, b -> collator.compare(a.artist, b.artist).let { if (it != 0) it else collator.compare(a.title, b.title) } }
            "album" -> Comparator { a, b -> collator.compare(a.album, b.album).let { if (it != 0) it else a.track.compareTo(b.track) } }
            "added" -> Comparator { a, b -> a.dateAdded.compareTo(b.dateAdded) }
            "duration" -> Comparator { a, b -> a.durationMs.compareTo(b.durationMs) }
            "plays" -> Comparator { a, b -> (plays[a.id] ?: 0).compareTo(plays[b.id] ?: 0) }
            else -> Comparator { a, b -> collator.compare(a.title, b.title) }
        }
        val sorted = list.sortedWith(cmp)
        return if (s.sortAsc) sorted else sorted.reversed()
    }

    fun recomputeVisible() {
        recomputeJob?.cancel()
        val list = songs
        val q = norm(query.trim())
        val s = settings
        recomputeJob = viewModelScope.launch {
            val r = withContext(Dispatchers.Default) {
                val f = if (q.isEmpty()) list else list.filter { it.searchKey.contains(q) }
                sortSongs(f, s)
            }
            visible = r
        }
    }

    fun setQuery(q: String) { query = q; recomputeVisible() }

    fun filterSongs(list: List<Song>): List<Song> {
        val q = norm(query.trim())
        return if (q.isEmpty()) list else list.filter { it.searchKey.contains(q) }
    }

    // ---------- ajustes ----------
    fun update(persist: Boolean = true, f: (Settings) -> Settings) {
        Store.updateSettings(f, persist)
    }

    fun setSort(key: String) {
        update { it.copy(sortKey = key, sortAsc = if (it.sortKey == key) !it.sortAsc else true) }
    }

    fun applyFolders(sel: Set<String>?) {
        update { it.copy(folders = sel, onboarded = true) }
        Library.refilter()
        applyLibrary()
    }

    fun rescanWithSettings() { scan(false) }

    fun applyFx() = AudioFx.apply()

    // ---------- imagen personalizada ----------
    fun customImageFile(): File = File(ctx.filesDir, "custom_bg.jpg")

    fun setCustomImage(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
                    val bmp = Covers.decodeBytes(bytes, 1400) ?: return@withContext false
                    FileOutputStream(customImageFile()).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    val accent = Covers.dominant(bmp)
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(accent, hsv)
                    hsv[1] = hsv[1].coerceAtLeast(0.45f); hsv[2] = 0.95f
                    val ac = android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
                    update { it.copy(themeId = "custom_image", imgAccent = ac, imgRev = it.imgRev + 1) }
                    true
                } catch (_: Throwable) { false }
            }
            toast(if (ok) "Imagen aplicada" else "No se pudo usar esa imagen")
        }
    }

    // ---------- reproducción ----------
    fun play(list: List<Song>, index: Int) = conn.playQueue(list, index)
    fun playShuffle(list: List<Song>) = conn.playShuffled(list)

    // ---------- selección múltiple ----------
    fun toggleSelect(id: Long) { selection = if (selection.contains(id)) selection - id else selection + id }
    fun clearSelection() { if (selection.isNotEmpty()) selection = emptySet() }
    fun selectAll(list: List<Song>) { selection = list.map { it.id }.toSet() }
    fun selectedSongs(list: List<Song>): List<Song> = list.filter { selection.contains(it.id) }

    // ---------- favoritas / listas ----------
    fun toggleFav(id: Long) { Store.toggleFav(id) }
    fun addFavs(list: List<Song>) {
        Store.setFavorites(favorites + list.map { it.id })
        toast("${list.size} añadidas a favoritas")
    }
    fun createPlaylist(name: String, ids: List<Long>) {
        Store.createPlaylist(name, ids); toast("Lista creada")
    }
    fun addToPlaylist(pid: Long, ids: List<Long>) {
        val n = Store.addToPlaylist(pid, ids)
        toast(if (n == 0) "Ya estaban en la lista" else "$n canción(es) añadidas")
    }

    fun smartList(kind: String): List<Song> {
        val all = songs
        return when (kind) {
            "fav" -> all.filter { favorites.contains(it.id) }
            "top" -> all.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(100)
            "recent" -> {
                val lp = Store.lastPlayed
                all.filter { lp.containsKey(it.id) }.sortedByDescending { lp[it.id] ?: 0L }.take(100)
            }
            "added" -> all.sortedByDescending { it.dateAdded }.take(100)
            else -> emptyList()
        }
    }

    fun playlistSongs(p: Playlist): List<Song> {
        val m = Library.byId
        return p.ids.mapNotNull { m[it] }
    }

    fun widgetRefresh() = MuseWidget.refresh(ctx)
}
