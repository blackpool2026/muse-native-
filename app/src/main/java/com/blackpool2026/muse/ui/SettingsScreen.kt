package com.blackpool2026.muse.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.Route
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.fmtSize

@Composable
fun SettingsScreen(vm: MuseViewModel, route: Route) {
    when (route) {
        Route.Themes -> ThemesScreen(vm)
        Route.Eq -> EqScreen(vm)
        Route.FolderPicker -> FolderPickerScreen(vm)
        Route.Help -> HelpScreen(vm)
        else -> SettingsMain(vm)
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalMuse.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            MText(title, 16, FontWeight.Medium)
            if (sub.isNotEmpty()) MText(sub, 13, color = t.textDim, maxLines = 2)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = t.onAccent, checkedTrackColor = t.accent,
                uncheckedThumbColor = t.textDim, uncheckedTrackColor = t.card, uncheckedBorderColor = t.stroke
            )
        )
    }
}

@Composable
fun SettingsMain(vm: MuseViewModel) {
    val t = LocalMuse.current
    val ctx = LocalContext.current
    val s = vm.settings
    var minDlg by remember { mutableStateOf(false) }
    val durOpts = listOf(0, 15, 30, 60, 120)
    if (minDlg) ChoiceDialog(
        "Duración mínima", durOpts.map { if (it == 0) "Sin límite" else "$it segundos" },
        durOpts.indexOf(s.minDurSec), { minDlg = false }
    ) { i -> vm.update { it.copy(minDurSec = durOpts[i]) }; vm.rescanWithSettings(); minDlg = false }

    var textDlg by remember { mutableStateOf(false) }
    val textOpts = listOf("Automático (según el fondo)", "Claro", "Oscuro")
    if (textDlg) ChoiceDialog("Color del texto", textOpts, s.textMode, { textDlg = false }) { i ->
        vm.update { it.copy(textMode = i) }; textDlg = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Ajustes", { vm.pop() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { SectionTitle("Apariencia") }
            item { ListRow(I.Palette, "Temas", "Más de ${com.blackpool2026.muse.ui.Themes.all.size} temas por categoría") { vm.push(Route.Themes) } }
            item { ListRow(I.Info, "Color del texto", textOpts.getOrElse(s.textMode) { textOpts[0] }) { textDlg = true } }
            item { SectionTitle("Biblioteca") }
            item { ListRow(I.Folder, "Carpetas", "Elige de dónde leer la música") { vm.push(Route.FolderPicker) } }
            item { ListRow(I.Refresh, "Buscar música nueva", "${vm.songs.size} canciones en la biblioteca") { vm.scan(false); vm.toast("Buscando música…") } }
            item { ListRow(I.Refresh, "Re-escanear todo", "Vuelve a leer todo y limpia el caché de portadas") { vm.scan(true); vm.toast("Re-escaneando…") } }
            item { ListRow(I.Timer, "Duración mínima", if (s.minDurSec == 0) "Sin límite" else "${s.minDurSec} segundos") { minDlg = true } }
            item {
                SwitchRow("Incluir todo el audio", "Notas de voz, grabaciones y audios que no son música", s.includeAll) { v ->
                    vm.update { it.copy(includeAll = v) }; vm.rescanWithSettings()
                }
            }
            item { SectionTitle("Reproducción") }
            item { ListRow(I.Tune, "Ecualizador y volumen", "Ecualizador, amplificación") { vm.push(Route.Eq) } }
            item {
                SwitchRow("Omitir silencios", "Salta los silencios largos entre partes de una pista", s.skipSilence) { v ->
                    vm.update { it.copy(skipSilence = v) }; vm.applyFx()
                    com.blackpool2026.muse.player.PlaybackService.instance?.player?.skipSilenceEnabled = v
                }
            }
            item { ListRow(I.Info, "Optimización de batería", "Evita que Android detenga la música en segundo plano") { openBattery(ctx) } }
            item { SectionTitle("Información") }
            item { ListRow(I.Help, "Ayuda y tutoriales", "Aprende lo básico y repasa los consejos") { vm.push(Route.Help) } }
            item {
                val (n, bytes) = Covers.cacheInfo()
                ListRow(I.Info, "Caché de portadas", "$n portadas · ${fmtSize(bytes)}") {
                    Covers.clearAll(); vm.toast("Caché limpiada")
                }
            }
            item { ListRow(I.Note, "Muse 2.0", "Reproductor nativo · com.blackpool2026.muse") {} }
        }
    }
}

private fun openBattery(ctx: Context) {
    try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val i = if (pm.isIgnoringBatteryOptimizations(ctx.packageName))
            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        try {
            ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
    }
}

// ───────────────────────── temas ─────────────────────────
@Composable
fun ThemesScreen(vm: MuseViewModel) {
    val cur = vm.settings.themeId
    val groups = Themes.groups.filter { Themes.inGroup(it).isNotEmpty() || it == "Personalizado" }
    var group by remember {
        mutableStateOf(
            when (cur) {
                Themes.CUSTOM_COLOR, Themes.CUSTOM_IMAGE -> "Personalizado"
                else -> Themes.find(cur)?.group ?: groups.first()
            }
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setCustomImage(uri)
    }
    LaunchedEffect(Unit) { vm.tip("themes") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Temas", { vm.pop() })
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { groups.forEach { g -> Pill(g, g == group) { group = g } } }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            MText("Texto:", 13, FontWeight.Bold, LocalMuse.current.textDim)
            listOf("Auto", "Claro", "Oscuro").forEachIndexed { i, label ->
                Pill(label, vm.settings.textMode == i) { vm.update { it.copy(textMode = i) } }
            }
        }
        if (group == "Personalizado") {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionTitle("Tu imagen de fondo") }
                item {
                    Column {
                        ImageTuning(vm, true) { picker.launch("image/*") }
                        if (cur == Themes.CUSTOM_IMAGE) MText(
                            "Imagen personalizada activa.", 13, color = LocalMuse.current.accent,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                item { SectionTitle("Tus colores") }
                item { CustomColorPanel(vm) }
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(104.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (group == "Imagen") item(span = { GridItemSpan(maxLineSpan) }) {
                    ImageTuning(vm, false) {}
                }
                items(Themes.inGroup(group), key = { it.id }) { d ->
                    ThemeSwatch(d, d.id == cur) { vm.update { it.copy(themeId = d.id) } }
                }
            }
        }
    }
}

@Composable
fun ThemeSwatch(d: ThemeDef, selected: Boolean, onClick: () -> Unit) {
    val t = LocalMuse.current
    val shape = RoundedCornerShape(16.dp)
    val light = d.imageRes == 0 && Color(d.bg2).luminance() > 0.4f
    val tc = if (light) Color(0xFF16161A) else Color.White
    Box(
        Modifier.aspectRatio(0.78f).clip(shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) t.accent else t.stroke, shape)
            .clickable(onClick = onClick)
    ) {
        if (d.imageRes != 0) {
            Image(painterResource(d.imageRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(d.bg1), Color(d.bg2), Color(d.bg3)))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(d.accent)).border(1.dp, tc.copy(alpha = 0.5f), CircleShape))
            Spacer(Modifier.width(1.dp))
            MText(d.name, 12, FontWeight.SemiBold, tc, Modifier.padding(top = 4.dp), 2)
        }
        if (selected) Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(t.accent),
            contentAlignment = Alignment.Center
        ) { Ic(I.Check, t.onAccent, 14.dp) }
    }
}
