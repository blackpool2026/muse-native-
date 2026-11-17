package com.blackpool2026.muse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.Route
import com.blackpool2026.muse.data.Playlist
import com.blackpool2026.muse.data.Song
import com.blackpool2026.muse.data.Store
import com.blackpool2026.muse.data.fmtTime
import com.blackpool2026.muse.player.PlayerUi

private val TAB_TITLES = listOf("Canciones", "Álbumes", "Artistas", "Listas", "Favoritas")

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconBtn(I.Back, onBack) else Spacer(Modifier.width(10.dp))
        MText(title, 22, FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun HomeScreen(vm: MuseViewModel, ui: PlayerUi) {
    val t = LocalMuse.current
    val favList = remember(vm.songs, vm.favorites, vm.settings, vm.plays) {
        vm.sortSongs(vm.songs.filter { vm.favorites.contains(it.id) })
    }
    val active: List<Song> = when (vm.tab) { 0 -> vm.visible; 4 -> favList; else -> emptyList() }
    Column(Modifier.fillMaxSize()) {
        if (vm.selection.isNotEmpty()) SelectionTop(vm, active)
        else HomeTop(vm)
        if (vm.searchOpen && vm.tab == 0) {
            SearchField(
                vm.query, { vm.setQuery(it) }, "Buscar título, artista o álbum",
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.tab) {
                0 -> if (vm.songs.isEmpty()) LibraryEmpty(vm)
                else if (vm.visible.isEmpty()) EmptyState(I.Search, "Sin resultados", "No hay canciones que coincidan con la búsqueda.")
                else SongListView(vm, ui, vm.visible, header = {
                    ActionRow(vm, vm.visible)
                })
                1 -> AlbumsTab(vm)
                2 -> ArtistsTab(vm)
                3 -> PlaylistsTab(vm)
                else -> if (favList.isEmpty()) EmptyState(I.Heart, "Sin favoritas", "Toca el corazón en el reproductor para marcar canciones.")
                else SongListView(vm, ui, favList, header = { ActionRow(vm, favList) })
            }
        }
    }
}

@Composable
fun HomeTop(vm: MuseViewModel) {
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val s = vm.settings
    val count = when (vm.tab) {
        0 -> vm.songs.size; 1 -> vm.albums.size; 2 -> vm.artists.size; 3 -> vm.playlists.size; else -> vm.favorites.size
    }
    TopBar("${TAB_TITLES[vm.tab]}  ·  $count", null) {
        if (vm.tab == 0) IconBtn(I.Search, {
            vm.searchOpen = !vm.searchOpen
            if (!vm.searchOpen) vm.setQuery("")
        })
        if (vm.tab == 0 || vm.tab == 4) {
            Box {
                IconBtn(I.Sort, { sortOpen = true })
                DropdownMenu(sortOpen, { sortOpen = false }) {
                    listOf(
                        "title" to "Título", "artist" to "Artista", "album" to "Álbum",
                        "added" to "Fecha agregada", "duration" to "Duración", "plays" to "Más reproducidas"
                    ).forEach { (k, label) ->
                        val mark = if (s.sortKey == k) (if (s.sortAsc) "  ▲" else "  ▼") else ""
                        MenuItem(label + mark) { vm.setSort(k); sortOpen = false }
                    }
                }
            }
        }
        if (vm.tab == 0) IconBtn(I.Grid, { vm.update { it.copy(grid = !it.grid) } })
        Box {
            IconBtn(I.More, { moreOpen = true })
            DropdownMenu(moreOpen, { moreOpen = false }) {
                MenuItem("Buscar música nueva") { moreOpen = false; vm.scan(false); vm.toast("Buscando música…") }
                MenuItem("Re-escanear todo") { moreOpen = false; vm.scan(true); vm.toast("Re-escaneando…") }
                MenuItem("Temas") { moreOpen = false; vm.push(Route.Themes) }
                MenuItem("Ajustes") { moreOpen = false; vm.push(Route.Settings) }
            }
        }
    }
}

@Composable
fun LibraryEmpty(vm: MuseViewModel) {
    val t = LocalMuse.current
    if (vm.loading) {
        EmptyState(I.Refresh, "Buscando música…", "Esto sólo tarda unos segundos.")
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(
                I.Note, "No se encontró música",
                "Revisa que tus archivos estén en el teléfono, que dure más de ${vm.settings.minDurSec} s " +
                    "y prueba a re-escanear.",
                Modifier.weight(1f, fill = false)
            )
            ActionButton("Re-escanear", I.Refresh, { vm.scan(true) })
            Spacer(Modifier.height(10.dp))
            ActionButton(
                if (vm.settings.includeAll) "Sólo música" else "Incluir todo el audio", null,
                { vm.update { it.copy(includeAll = !it.includeAll) }; vm.scan(false) }, filled = false
            )
            Spacer(Modifier.height(10.dp))
            MText("Encontrados en total: ${vm.scanned}", 12, color = t.textDim)
        }
    }
}

@Composable
fun ActionRow(vm: MuseViewModel, list: List<Song>) {
    val total = list.sumOf { it.durationMs }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton("Reproducir", I.Play, { vm.play(list, 0) })
        Spacer(Modifier.width(8.dp))
        ActionButton("Aleatorio", I.Shuffle, { vm.playShuffle(list) }, filled = false)
        Spacer(Modifier.weight(1f))
        MText("${list.size} · ${fmtTime(total)}", 12, color = LocalMuse.current.textDim)
    }
}

// ───────────────────────── lista de canciones ─────────────────────────
@Composable
fun SongListView(
    vm: MuseViewModel, ui: PlayerUi, list: List<Song>,
    playlist: Playlist? = null, header: (@Composable () -> Unit)? = null
) {
    val selecting = vm.selection.isNotEmpty()
    if (vm.settings.grid && playlist == null) {
        LazyVerticalGrid(
            GridCells.Adaptive(130.dp), contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            gridItemsIndexed(list, key = { _, s -> s.id }) { i, song ->
                SongCard(
                    song, ui.mediaId == song.id, vm.selection.contains(song.id),
                    onClick = { if (selecting) vm.toggleSelect(song.id) else vm.play(list, i) },
                    onLong = { vm.toggleSelect(song.id) }
                )
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (header != null) item { header() }
            itemsIndexed(list, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song = song,
                    playing = ui.mediaId == song.id,
                    isPlayingNow = ui.mediaId == song.id && ui.playing,
                    selected = vm.selection.contains(song.id),
                    selecting = selecting,
                    fav = vm.favorites.contains(song.id),
                    onClick = { if (selecting) vm.toggleSelect(song.id) else vm.play(list, i) },
                    onLong = { vm.toggleSelect(song.id) },
                    menu = { dismiss -> SongMenu(vm, song, playlist, i, list.size, dismiss) }
                )
            }
        }
    }
}

@Composable
fun SongMenu(vm: MuseViewModel, song: Song, playlist: Playlist?, index: Int, count: Int, dismiss: () -> Unit) {
    MenuItem("Reproducir siguiente") { vm.conn.playNext(listOf(song)); vm.toast("Sonará a continuación"); dismiss() }
    MenuItem("Añadir a la cola") { vm.conn.addToQueue(listOf(song)); vm.toast("Añadida a la cola"); dismiss() }
    MenuItem("Añadir a una lista") { vm.pickerIds = listOf(song.id); dismiss() }
    MenuItem(if (vm.favorites.contains(song.id)) "Quitar de favoritas" else "Añadir a favoritas") {
        vm.toggleFav(song.id); dismiss()
    }
    MenuItem("Ir al álbum") { dismiss(); vm.push(Route.Album(song.albumKey)) }
    MenuItem("Ir al artista") { dismiss(); vm.push(Route.Artist(song.artist)) }
    if (playlist != null) {
        MenuItem("Quitar de esta lista") { Store.removeFromPlaylist(playlist.id, song.id); dismiss() }
        if (index > 0) MenuItem("Subir") { Store.moveInPlaylist(playlist.id, index, index - 1); dismiss() }
        if (index < count - 1) MenuItem("Bajar") { Store.moveInPlaylist(playlist.id, index, index + 1); dismiss() }
    }
}

// ───────────────────────── barra de selección ─────────────────────────
@Composable
fun SelectionTop(vm: MuseViewModel, list: List<Song>) {
    val t = LocalMuse.current
    val sel = vm.selectedSongs(list)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(I.Close, { vm.clearSelection() })
        Column(Modifier.weight(1f)) {
            MText("${sel.size} seleccionadas", 16, FontWeight.Bold)
            MText(fmtTime(sel.sumOf { it.durationMs }), 12, color = t.textDim)
        }
        IconBtn(I.Play, { vm.play(sel, 0); vm.clearSelection() }, t.accent)
        IconBtn(I.Queue, { vm.conn.addToQueue(sel); vm.toast("Añadidas a la cola"); vm.clearSelection() })
        IconBtn(I.Lists, { vm.pickerIds = sel.map { it.id } })
        IconBtn(I.Heart, { vm.addFavs(sel); vm.clearSelection() })
        IconBtn(I.SelectAll, { vm.selectAll(list) })
    }
}

// ───────────────────────── pestañas ─────────────────────────
@Composable
fun AlbumsTab(vm: MuseViewModel) {
    if (vm.albums.isEmpty()) { EmptyState(I.Album, "Sin álbumes", "Cuando haya música aparecerá aquí."); return }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
        gridItems(vm.albums, key = { it.key }) { a ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.push(Route.Album(a.key)) }.padding(6.dp)
            ) {
                BoxSquare { CoverImage(a.songs.first(), 150.dp, Modifier.fillMaxSize(), RoundedCornerShape(12.dp)) }
                Spacer(Modifier.height(6.dp))
                MText(a.name, 13, FontWeight.Medium)
                MText("${a.artist} · ${a.songs.size}", 12, color = LocalMuse.current.textDim)
            }
        }
    }
}

@Composable
fun ArtistsTab(vm: MuseViewModel) {
    val t = LocalMuse.current
    if (vm.artists.isEmpty()) { EmptyState(I.Person, "Sin artistas", "Cuando haya música aparecerá aquí."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(vm.artists, key = { _, a -> a.name }) { _, a ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.push(Route.Artist(a.name)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(a.songs.first(), 52.dp, shape = CircleShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    MText(a.name, 16, FontWeight.Medium)
                    MText("${a.songs.size} canciones · ${a.albums} álbumes", 13, color = t.textDim)
                }
            }
        }
    }
}

@Composable
fun ListRow(icon: I, title: String, sub: String, onClick: () -> Unit) {
    val t = LocalMuse.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(t.card),
            contentAlignment = Alignment.Center
        ) { Ic(icon, t.accent, 24.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MText(title, 16, FontWeight.Medium)
            MText(sub, 13, color = t.textDim)
        }
    }
}

@Composable
fun PlaylistsTab(vm: MuseViewModel) {
    var creating by remember { mutableStateOf(false) }
    if (creating) TextInputDialog("Nueva lista", "", "Crear", { creating = false }) {
        vm.createPlaylist(it, emptyList()); creating = false
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item { SectionTitle("Inteligentes") }
        item { ListRow(I.Speed, "Más escuchadas", "Tus canciones más reproducidas") { vm.push(Route.Smart("top")) } }
        item { ListRow(I.Timer, "Reproducidas recientemente", "Lo último que escuchaste") { vm.push(Route.Smart("recent")) } }
        item { ListRow(I.Add, "Agregadas recientemente", "Las últimas 100 canciones nuevas") { vm.push(Route.Smart("added")) } }
        item {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Mis listas") }
                ActionButton("Nueva", I.Add, { creating = true }, filled = false)
            }
        }
        if (vm.playlists.isEmpty()) {
            item { MText("Aún no tienes listas. Crea una o añade canciones con la selección múltiple.", 13, color = LocalMuse.current.textDim, modifier = Modifier.padding(16.dp), maxLines = 3) }
        }
        itemsIndexed(vm.playlists, key = { _, p -> p.id }) { _, p ->
            ListRow(I.Lists, p.name, "${p.ids.size} canciones") { vm.push(Route.Playlist(p.id)) }
        }
        item { SectionTitle("Carpetas") }
        itemsIndexed(vm.folders, key = { _, f -> f.path }) { _, f ->
            ListRow(I.Folder, f.name, "${f.songs.size} canciones") { vm.push(Route.Folder(f.path)) }
        }
    }
}
