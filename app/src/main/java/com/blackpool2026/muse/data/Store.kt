package com.blackpool2026.muse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Settings(
    val themeId: String = "sangre",
    val customBg1: Long = 0xFF1A0008,
    val customBg2: Long = 0xFF5A0A20,
    val customAccent: Long = 0xFFFF4D6D,
    val imgBlur: Int = 0,
    val imgDim: Float = 0.40f,
    val imgAccent: Long = 0xFFFF4D6D,
    val imgRev: Int = 0,
    val sortKey: String = "title",
    val sortAsc: Boolean = true,
    val grid: Boolean = false,
    val minDurSec: Int = 30,
    val includeAll: Boolean = false,
    val folders: Set<String>? = null,
    val boost: Int = 0,
    val skipSilence: Boolean = false,
    val speed: Float = 1f,
    val eqEnabled: Boolean = false,
    val eqLevels: List<Int> = emptyList(),
    val onboarded: Boolean = false
)

data class Snap(val ids: List<Long>, val index: Int, val pos: Long, val shuffle: Boolean, val repeat: Int)

object Store {
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()

    @Volatile var settings = Settings(); private set
    @Volatile var favorites: Set<Long> = emptySet(); private set
    @Volatile var playlists: List<Playlist> = emptyList(); private set
    @Volatile var plays: Map<Long, Int> = emptyMap(); private set
    @Volatile var lastPlayed: Map<Long, Long> = emptyMap(); private set
    @Volatile private var snapIds: List<Long> = emptyList()

    /** Se incrementa en cada cambio para que la interfaz se refresque. */
    val rev = MutableStateFlow(0)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        try { read("settings.json")?.let { settings = settingsFrom(JSONObject(it)) } } catch (_: Throwable) {}
        try {
            read("favorites.json")?.let { t ->
                val a = JSONArray(t)
                favorites = (0 until a.length()).map { a.getLong(it) }.toSet()
            }
        } catch (_: Throwable) {}
        try {
            read("playlists.json")?.let { t ->
                val a = JSONArray(t)
                playlists = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val ids = o.getJSONArray("ids")
                    Playlist(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
                }
            }
        } catch (_: Throwable) {}
        try {
            read("stats.json")?.let { t ->
                val o = JSONObject(t)
                val p = o.optJSONObject("plays")
                val l = o.optJSONObject("last")
                if (p != null) plays = p.keys().asSequence().associate { it.toLong() to p.getInt(it) }
                if (l != null) lastPlayed = l.keys().asSequence().associate { it.toLong() to l.getLong(it) }
            }
        } catch (_: Throwable) {}
    }

    private fun read(name: String): String? {
        val f = File(app.filesDir, name)
        return if (f.exists()) f.readText() else null
    }

    private fun write(name: String, text: String) {
        io.execute {
            try {
                val tmp = File(app.filesDir, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(app.filesDir, name))
            } catch (_: Throwable) {}
        }
    }

    private fun bump() { rev.value = rev.value + 1 }

    // ---------- ajustes ----------
    fun updateSettings(f: (Settings) -> Settings, persist: Boolean = true): Settings {
        val s = f(settings)
        settings = s
        if (persist) write("settings.json", settingsToJson(s).toString())
        bump()
        return s
    }

    // ---------- favoritas ----------
    fun toggleFav(id: Long): Boolean {
        val now = if (favorites.contains(id)) favorites - id else favorites + id
        setFavorites(now)
        return now.contains(id)
    }

    fun setFavorites(set: Set<Long>) {
        favorites = set
        write("favorites.json", JSONArray(set.toList()).toString())
        bump()
    }

    // ---------- playlists ----------
    private fun savePlaylists(l: List<Playlist>) {
        playlists = l
        val arr = JSONArray()
        l.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.ids)))
        }
        write("playlists.json", arr.toString())
        bump()
    }

    fun createPlaylist(name: String, ids: List<Long> = emptyList()): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.trim().ifBlank { "Nueva lista" }, ids.distinct())
        savePlaylists(playlists + p)
        return p
    }

    fun renamePlaylist(id: Long, name: String) =
        savePlaylists(playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })

    fun deletePlaylist(id: Long) = savePlaylists(playlists.filter { it.id != id })

    fun addToPlaylist(id: Long, ids: List<Long>): Int {
        var added = 0
        savePlaylists(playlists.map { p ->
            if (p.id != id) p else {
                val fresh = ids.filter { !p.ids.contains(it) }.distinct()
                added = fresh.size
                p.copy(ids = p.ids + fresh)
            }
        })
        return added
    }

    fun removeFromPlaylist(id: Long, songId: Long) =
        savePlaylists(playlists.map { if (it.id == id) it.copy(ids = it.ids - songId) else it })

    fun moveInPlaylist(id: Long, from: Int, to: Int) =
        savePlaylists(playlists.map { p ->
            if (p.id != id || from !in p.ids.indices || to !in p.ids.indices) p else {
                val m = p.ids.toMutableList()
                m.add(to, m.removeAt(from))
                p.copy(ids = m)
            }
        })

    // ---------- estadísticas ----------
    fun incPlay(id: Long) {
        plays = plays + (id to ((plays[id] ?: 0) + 1))
        lastPlayed = lastPlayed + (id to System.currentTimeMillis())
        val o = JSONObject()
        val p = JSONObject(); plays.forEach { (k, v) -> p.put(k.toString(), v) }
        val l = JSONObject(); lastPlayed.forEach { (k, v) -> l.put(k.toString(), v) }
        o.put("plays", p).put("last", l)
        write("stats.json", o.toString())
        bump()
    }

    // ---------- sesión (cola) ----------
    fun saveSnap(ids: List<Long>?, index: Int, pos: Long, shuffle: Boolean, repeat: Int) {
        if (ids != null) snapIds = ids
        if (snapIds.isEmpty()) return
        val o = JSONObject()
        o.put("ids", snapIds.joinToString(","))
        o.put("index", index).put("pos", pos).put("shuffle", shuffle).put("repeat", repeat)
        write("session.json", o.toString())
    }

    fun loadSnap(): Snap? = try {
        read("session.json")?.let { t ->
            val o = JSONObject(t)
            val s = o.getString("ids")
            val ids = if (s.isBlank()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
            snapIds = ids
            Snap(ids, o.getInt("index"), o.getLong("pos"), o.getBoolean("shuffle"), o.getInt("repeat"))
        }
    } catch (_: Throwable) { null }

    // ---------- JSON de ajustes ----------
    private fun settingsToJson(s: Settings): JSONObject = JSONObject().apply {
        put("themeId", s.themeId)
        put("customBg1", s.customBg1); put("customBg2", s.customBg2); put("customAccent", s.customAccent)
        put("imgBlur", s.imgBlur); put("imgDim", s.imgDim.toDouble()); put("imgAccent", s.imgAccent); put("imgRev", s.imgRev)
        put("sortKey", s.sortKey); put("sortAsc", s.sortAsc); put("grid", s.grid)
        put("minDurSec", s.minDurSec); put("includeAll", s.includeAll)
        if (s.folders != null) put("folders", JSONArray(s.folders.toList()))
        put("boost", s.boost); put("skipSilence", s.skipSilence); put("speed", s.speed.toDouble())
        put("eqEnabled", s.eqEnabled); put("eqLevels", JSONArray(s.eqLevels))
        put("onboarded", s.onboarded)
    }

    private fun settingsFrom(j: JSONObject): Settings {
        val d = Settings()
        return Settings(
            themeId = j.optString("themeId", d.themeId),
            customBg1 = j.optLong("customBg1", d.customBg1),
            customBg2 = j.optLong("customBg2", d.customBg2),
            customAccent = j.optLong("customAccent", d.customAccent),
            imgBlur = j.optInt("imgBlur", d.imgBlur),
            imgDim = j.optDouble("imgDim", d.imgDim.toDouble()).toFloat(),
            imgAccent = j.optLong("imgAccent", d.imgAccent),
            imgRev = j.optInt("imgRev", d.imgRev),
            sortKey = j.optString("sortKey", d.sortKey),
            sortAsc = j.optBoolean("sortAsc", d.sortAsc),
            grid = j.optBoolean("grid", d.grid),
            minDurSec = j.optInt("minDurSec", d.minDurSec),
            includeAll = j.optBoolean("includeAll", d.includeAll),
            folders = j.optJSONArray("folders")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            boost = j.optInt("boost", d.boost),
            skipSilence = j.optBoolean("skipSilence", d.skipSilence),
            speed = j.optDouble("speed", d.speed.toDouble()).toFloat(),
            eqEnabled = j.optBoolean("eqEnabled", d.eqEnabled),
            eqLevels = j.optJSONArray("eqLevels")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
            onboarded = j.optBoolean("onboarded", d.onboarded)
        )
    }
}
