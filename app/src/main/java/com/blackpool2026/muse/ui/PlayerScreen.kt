package com.blackpool2026.muse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.Route
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.data.Song
import com.blackpool2026.muse.data.fmtSize
import com.blackpool2026.muse.data.fmtTime
import com.blackpool2026.muse.player.PlaybackService
import com.blackpool2026.muse.player.PlayerUi

@Composable
fun PlayerScreen(vm: MuseViewModel, ui: PlayerUi) {
    val t = LocalMuse.current
    val song = Library.byId[ui.mediaId]
    val pos by rememberPosition(vm, ui)
    val sleepEnd by PlaybackService.sleepEnd.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val dur = ui.durationMs.coerceAtLeast(1L)
    val frac = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f)
    val shownPos = if (dragging) (dragValue * dur).toLong() else pos
    val fav = song != null && vm.favorites.contains(song.id)

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        MuseBackground(t)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.accent.copy(alpha = 0.22f), Color.Transparent))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            // barra superior
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(I.Down, { vm.playerOpen = false }, size = 28.dp)
                MText("REPRODUCIENDO", 12, FontWeight.Bold, t.textDim, Modifier.weight(1f).padding(horizontal = 8.dp))
                Box {
                    IconBtn(I.More, { menuOpen = true })
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        MenuItem("Información") { menuOpen = false; showInfo = true }
                        if (song != null) {
                            MenuItem("Ir al álbum") { menuOpen = false; vm.playerOpen = false; vm.push(Route.Album(song.albumKey)) }
                            MenuItem("Ir al artista") { menuOpen = false; vm.playerOpen = false; vm.push(Route.Artist(song.artist)) }
                        }
                        MenuItem("Vaciar cola") { menuOpen = false; vm.conn.clearQueue(); vm.playerOpen = false }
                    }
                }
            }
            // portada
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                val s = maxWidth.coerceAtMost(maxHeight)
                CoverImage(song, s, shape = RoundedCornerShape(24.dp))
            }
            // título
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MText(song?.title ?: "—", 22, FontWeight.Bold, maxLines = 2)
                    MText(song?.artist ?: "", 15, color = t.textDim)
                }
                IconBtn(
                    if (fav) I.Heart else I.HeartOutline,
                    { song?.let { vm.toggleFav(it.id) } },
                    if (fav) t.accent else t.text, 26.dp, 48.dp
                )
            }
            // progreso
            Slider(
                value = frac,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { vm.conn.seekTo((dragValue * dur).toLong()); dragging = false },
                colors = SliderDefaults.colors(
                    thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.stroke
                )
            )
            Row(Modifier.fillMaxWidth()) {
                MText(fmtTime(shownPos), 12, color = t.textDim)
                Spacer(Modifier.weight(1f))
                MText(fmtTime(ui.durationMs), 12, color = t.textDim)
            }
            Spacer(Modifier.height(8.dp))
            // controles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(I.Shuffle, { vm.conn.toggleShuffle() }, if (ui.shuffle) t.accent else t.textDim, 26.dp, 48.dp)
                IconBtn(I.Prev, { vm.conn.prev() }, size = 34.dp, box = 56.dp)
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(t.accent).clickable { vm.conn.toggle() },
                    contentAlignment = Alignment.Center
                ) { Ic(if (ui.playing) I.Pause else I.Play, t.onAccent, 38.dp) }
                IconBtn(I.Next, { vm.conn.next() }, size = 34.dp, box = 56.dp)
                IconBtn(
                    if (ui.repeat == 1) I.RepeatOne else I.Repeat, { vm.conn.cycleRepeat() },
                    if (ui.repeat != 0) t.accent else t.textDim, 26.dp, 48.dp
                )
            }
            Spacer(Modifier.height(8.dp))
            // extras
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(I.Queue, { showQueue = true })
                IconBtn(I.Timer, { showSleep = true }, if (sleepEnd > 0) t.accent else t.text)
                IconBtn(I.Speed, { showSpeed = true }, if (ui.speed != 1f) t.accent else t.text)
                IconBtn(I.Tune, { vm.playerOpen = false; vm.push(Route.Eq) })
                IconBtn(I.Lists, { song?.let { vm.pickerIds = listOf(it.id) } })
            }
            if (sleepEnd > 0) {
                val min = ((sleepEnd - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L) + 1
                MText("Se detendrá en $min min", 12, color = t.accent, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        if (showQueue) QueuePanel(vm, ui) { showQueue = false }
        if (showSleep) {
            val opts = listOf(0, 5, 10, 15, 30, 45, 60, 90)
            ChoiceDialog(
                "Temporizador de apagado",
                opts.map { if (it == 0) "Desactivado" else "$it minutos" },
                if (sleepEnd > 0) -1 else 0,
                { showSleep = false }
            ) { i ->
                PlaybackService.setSleep(opts[i]); showSleep = false
                vm.toast(if (opts[i] == 0) "Temporizador desactivado" else "La música se detendrá en ${opts[i]} min")
            }
        }
        if (showSpeed) {
            val opts = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            ChoiceDialog(
                "Velocidad", opts.map { "${it}x" }, opts.indexOfFirst { it == ui.speed }, { showSpeed = false }
            ) { i -> vm.conn.setSpeed(opts[i]); vm.update { it.copy(speed = opts[i]) }; showSpeed = false }
        }
        if (showInfo && song != null) SongInfoDialog(song) { showInfo = false }
    }
}

@Composable
fun SongInfoDialog(s: Song, onClose: () -> Unit) {
    val t = LocalMuse.current
    MuseDialog("Información", onClose) {
        Column(Modifier.heightInMax()) {
            listOf(
                "Título" to s.title, "Artista" to s.artist, "Álbum" to s.album,
                "Año" to (if (s.year > 0) s.year.toString() else "—"),
                "Duración" to fmtTime(s.durationMs), "Formato" to s.mime.ifBlank { "—" },
                "Tamaño" to fmtSize(s.size), "Ruta" to s.path
            ).forEach { (k, v) ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    MText(k, 11, FontWeight.Bold, t.accent)
                    MText(v, 14, maxLines = 3)
                }
            }
        }
    }
}

private fun Modifier.heightInMax(): Modifier = this.then(Modifier.fillMaxWidth())

@Composable
fun QueuePanel(vm: MuseViewModel, ui: PlayerUi, onClose: () -> Unit) {
    val t = LocalMuse.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(t.bar.copy(alpha = 1f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MText("Cola · ${ui.queue.size}", 18, FontWeight.Bold, modifier = Modifier.weight(1f))
                ActionButton("Vaciar", I.Delete, { vm.conn.clearQueue(); onClose(); vm.playerOpen = false }, filled = false)
            }
            val state = rememberLazyListState(initialFirstVisibleItemIndex = (ui.index - 2).coerceAtLeast(0))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
                itemsIndexed(ui.queue) { i, id ->
                    val s = Library.byId[id]
                    val current = i == ui.index
                    Row(
                        Modifier.fillMaxWidth().background(if (current) t.accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { vm.conn.skipTo(i) }.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverImage(s, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            MText(s?.title ?: "Archivo no disponible", 14, FontWeight.Medium, if (current) t.accent else t.text)
                            MText(s?.artist ?: "", 12, color = t.textDim)
                        }
                        if (i > 0) IconBtn(I.Up, { vm.conn.move(i, i - 1) }, t.textDim, 20.dp, 36.dp)
                        if (i < ui.queue.size - 1) IconBtn(I.Down, { vm.conn.move(i, i + 1) }, t.textDim, 20.dp, 36.dp)
                        IconBtn(I.Close, { vm.conn.removeAt(i) }, t.textDim, 18.dp, 36.dp)
                    }
                }
            }
        }
    }
}
