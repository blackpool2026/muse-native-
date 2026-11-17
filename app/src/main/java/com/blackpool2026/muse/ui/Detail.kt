package com.blackpool2026.muse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.Route
import com.blackpool2026.muse.data.Playlist
import com.blackpool2026.muse.data.Song
import com.blackpool2026.muse.data.Store
import com.blackpool2026.muse.player.PlayerUi

@Composable
fun DetailScreen(vm: MuseViewModel, ui: PlayerUi, route: Route) {
    var title = ""
    var list: List<Song> = emptyList()
    var playlist: Playlist? = null
    when (route) {
        is Route.Album -> {
            val a = vm.albums.firstOrNull { it.key == route.key }
            title = a?.name ?: "Álbum"; list = a?.songs ?: emptyList()
        }
        is Route.Artist -> {
            val a = vm.artists.firstOrNull { it.name == route.name }
            title = route.name; list = vm.sortSongs(a?.songs ?: emptyList())
        }
        is Route.Folder -> {
            val f = vm.folders.firstOrNull { it.path == route.path }
            title = f?.name ?: "Carpeta"; list = vm.sortSongs(f?.songs ?: emptyList())
        }
        is Route.Playlist -> {
            playlist = vm.playlists.firstOrNull { it.id == route.id }
            title = playlist?.name ?: "Lista"
            list = playlist?.let { vm.playlistSongs(it) } ?: emptyList()
        }
        is Route.Smart -> {
            title = when (route.kind) {
                "top" -> "Más escuchadas"; "recent" -> "Reproducidas recientemente"
                "added" -> "Agregadas recientemente"; else -> "Favoritas"
            }
            list = vm.smartList(route.kind)
        }
        else -> {}
    }
    // Si la lista desapareció (borrada), volver atrás.
    if (route is Route.Playlist && playlist == null) {
        LaunchedEffect(route) { vm.pop() }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pl = playlist
    if (renaming && pl != null) TextInputDialog("Renombrar lista", pl.name, "Guardar", { renaming = false }) {
        Store.renamePlaylist(pl.id, it); renaming = false
    }
    if (deleting && pl != null) MuseDialog("¿Eliminar lista?", { deleting = false }, "Eliminar", {
        deleting = false; Store.deletePlaylist(pl.id); vm.pop()
    }) { MText("Se eliminará \"${pl.name}\". Las canciones no se borran del teléfono.", 14, maxLines = 4) }

    Column(Modifier.fillMaxSize()) {
        if (vm.selection.isNotEmpty()) SelectionTop(vm, list)
        else TopBar(title, { vm.pop() }) {
            if (pl != null) Box {
                IconBtn(I.More, { menuOpen = true })
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    MenuItem("Renombrar") { menuOpen = false; renaming = true }
                    MenuItem("Eliminar lista") { menuOpen = false; deleting = true }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) EmptyState(I.Note, "Nada por aquí", "Esta lista no tiene canciones todavía.")
            else SongListView(vm, ui, list, playlist, header = { ActionRow(vm, list) })
        }
    }
}

@Composable
fun PlaylistPicker(vm: MuseViewModel, ids: List<Long>) {
    val t = LocalMuse.current
    var creating by remember { mutableStateOf(false) }
    val close = { vm.pickerIds = null }
    if (creating) {
        TextInputDialog("Nueva lista", "", "Crear", { creating = false }) { name ->
            vm.createPlaylist(name, ids); vm.pickerIds = null; vm.clearSelection()
        }
    } else {
        MuseDialog("Añadir a una lista", close) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                ActionButton("Nueva lista", I.Add, { creating = true }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.width(8.dp))
                vm.playlists.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.addToPlaylist(p.id, ids); vm.pickerIds = null; vm.clearSelection()
                        }.padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ic(I.Lists, t.accent, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        MText(p.name, 16, FontWeight.Medium, modifier = Modifier.weight(1f))
                        MText("${p.ids.size}", 13, color = t.textDim)
                    }
                }
            }
        }
    }
}
