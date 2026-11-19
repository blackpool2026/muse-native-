package com.blackpool2026.muse.ui

import android.graphics.Color as AColor
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.player.AudioFx

// ───────────────────────── utilidades de color ─────────────────────────
fun argbToHsv(argb: Long): FloatArray {
    val a = FloatArray(3)
    AColor.colorToHSV(argb.toInt(), a)
    return a
}

fun hsvToArgb(h: Float, s: Float, v: Float): Long =
    AColor.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL

@Composable
fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit, onDone: () -> Unit
) {
    val t = LocalMuse.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MText(label, 14, FontWeight.Medium, modifier = Modifier.weight(1f))
            MText(valueText, 13, color = t.textDim)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            onValueChangeFinished = onDone, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.stroke)
        )
    }
}

// ───────────────────────── ecualizador ─────────────────────────
@Composable
fun EqScreen(vm: MuseViewModel) {
    val t = LocalMuse.current
    val s = vm.settings
    val freqs = AudioFx.bandFreqsHz
    val lo = AudioFx.minLevel
    val hi = AudioFx.maxLevel
    LaunchedEffect(Unit) { vm.tip("eq") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Ecualizador y volumen", { vm.pop() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SwitchRow("Activar ecualizador", "Ajusta los graves, medios y agudos", s.eqEnabled) { v ->
                    vm.update { it.copy(eqEnabled = v) }; vm.applyFx()
                }
            }
            if (freqs.isEmpty()) {
                item {
                    MText(
                        "El ecualizador del sistema aún no está listo. Reproduce una canción y vuelve a esta pantalla.",
                        14, color = t.textDim, modifier = Modifier.padding(16.dp), maxLines = 4
                    )
                }
            } else {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioFx.presets.forEachIndexed { i, name ->
                            Pill(name, false) {
                                val lv = AudioFx.usePreset(i)
                                if (lv.isNotEmpty()) {
                                    vm.update { it.copy(eqLevels = lv, eqEnabled = true) }; vm.applyFx()
                                }
                            }
                        }
                        Pill("Restablecer", false) {
                            vm.update { it.copy(eqLevels = emptyList()) }; vm.applyFx()
                        }
                    }
                }
                itemsIndexed(freqs) { i, hz ->
                    val cur = s.eqLevels.getOrNull(i) ?: 0
                    val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                    SliderRow(
                        label, cur.toFloat(), lo.toFloat()..hi.toFloat(), "%+.1f dB".format(cur / 100f),
                        onChange = { v ->
                            val nl = List(freqs.size) { idx -> if (idx == i) v.toInt() else (s.eqLevels.getOrNull(idx) ?: 0) }
                            vm.update(false) { it.copy(eqLevels = nl) }; vm.applyFx()
                        },
                        onDone = { vm.update { it } }
                    )
                }
            }
            item { SectionTitle("Volumen") }
            item {
                SliderRow(
                    "Amplificación", s.boost.toFloat(), 0f..100f,
                    "+%.1f dB".format(s.boost * AudioFx.GAIN_PER_STEP_MB / 100f),
                    onChange = { v -> vm.update(false) { it.copy(boost = v.toInt()) }; vm.applyFx() },
                    onDone = { vm.update { it } }
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Normal" to 0, "Alto +10 dB" to 33, "Muy alto +20 dB" to 67, "Máximo +30 dB" to 100).forEach { (label, v) ->
                        Pill(label, kotlin.math.abs(s.boost - v) <= 2) {
                            vm.update { it.copy(boost = v) }; vm.applyFx()
                        }
                    }
                }
            }
            item {
                MText(
                    "Sube el volumen del teléfono y luego la amplificación. Funciona con audífonos de cable, Bluetooth y altavoz.",
                    12, color = t.textDim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 3
                )
            }
            if (s.boost > 33) item {
                MText(
                    "Aviso: un volumen muy alto puede distorsionar el sonido y dañar tus oídos o los audífonos. " +
                        "Empieza bajo y súbelo poco a poco, sobre todo con audífonos puestos.",
                    13, color = t.accent, modifier = Modifier.padding(16.dp), maxLines = 4
                )
            }
        }
    }
}

// ───────────────────────── carpetas ─────────────────────────
@Composable
fun FolderPickerScreen(vm: MuseViewModel) {
    val t = LocalMuse.current
    val all = vm.allFolders
    var sel by remember { mutableStateOf(vm.settings.folders ?: all.map { it.path }.toSet()) }
    // Marcar como "configurado" al salir, aunque no se aplique.
    DisposableEffect(Unit) {
        onDispose { if (!vm.settings.onboarded) vm.update { it.copy(onboarded = true) } }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Carpetas de música", { vm.pop() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Todas", null, { sel = all.map { it.path }.toSet() }, filled = false)
            ActionButton("Ninguna", null, { sel = emptySet() }, filled = false)
            Spacer(Modifier.weight(1f))
            ActionButton("Aplicar", I.Check, {
                val chosen = if (sel.size == all.size) null else sel
                vm.applyFolders(chosen)
                vm.pop()
            })
        }
        MText(
            "Elige qué carpetas se muestran en la biblioteca (por ejemplo, para ocultar audios de WhatsApp).",
            13, color = t.textDim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), maxLines = 3
        )
        if (all.isEmpty()) EmptyState(I.Folder, "Sin carpetas", "Escanea tu música primero.")
        else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(all, key = { _, f -> f.path }) { _, f ->
                val on = sel.contains(f.path)
                Row(
                    Modifier.fillMaxWidth().clickable { sel = if (on) sel - f.path else sel + f.path }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (on) t.accent else Color.Transparent)
                            .border(1.5.dp, if (on) t.accent else t.stroke, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) { if (on) Ic(I.Check, t.onAccent, 18.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        MText(f.name, 15, FontWeight.Medium)
                        MText("${f.songs.size} canciones · ${f.path}", 11, color = t.textDim)
                    }
                }
            }
        }
    }
}

// ───────────────────────── temas personalizados ─────────────────────────
@Composable
fun ColorPicker(label: String, value: Long, onChange: (Long) -> Unit, onDone: () -> Unit) {
    val t = LocalMuse.current
    val hsv = remember(value) { argbToHsv(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(value)).border(1.dp, t.stroke, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
            MText(label, 14, FontWeight.SemiBold)
        }
        SliderRow("Tono", hsv[0], 0f..360f, "${hsv[0].toInt()}°", { onChange(hsvToArgb(it, hsv[1], hsv[2])) }, onDone)
        SliderRow("Intensidad", hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()}%", { onChange(hsvToArgb(hsv[0], it, hsv[2])) }, onDone)
        SliderRow("Brillo", hsv[2], 0f..1f, "${(hsv[2] * 100).toInt()}%", { onChange(hsvToArgb(hsv[0], hsv[1], it)) }, onDone)
    }
}

@Composable
fun CustomColorPanel(vm: MuseViewModel) {
    val t = LocalMuse.current
    val s = vm.settings
    val active = s.themeId == Themes.CUSTOM_COLOR
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().padding(16.dp).height(110.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(s.customBg1), Color(s.customBg2))))
                .border(if (active) 2.5.dp else 1.dp, if (active) t.accent else t.stroke, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Color(s.customAccent))) }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(if (active) "Tema activo" else "Usar este tema", I.Check, {
                vm.update { it.copy(themeId = Themes.CUSTOM_COLOR) }
            })
        }
        ColorPicker("Fondo superior", s.customBg1, { c -> vm.update(false) { it.copy(customBg1 = c, themeId = Themes.CUSTOM_COLOR) } }, { vm.update { it } })
        ColorPicker("Fondo inferior", s.customBg2, { c -> vm.update(false) { it.copy(customBg2 = c, themeId = Themes.CUSTOM_COLOR) } }, { vm.update { it } })
        ColorPicker("Color de acento", s.customAccent, { c -> vm.update(false) { it.copy(customAccent = c, themeId = Themes.CUSTOM_COLOR) } }, { vm.update { it } })
    }
}

/** Controles de desenfoque y oscurecimiento para los temas con imagen. */
@Composable
fun ImageTuning(vm: MuseViewModel, withPicker: Boolean, onPick: () -> Unit) {
    val s = vm.settings
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (withPicker) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Elegir imagen de la galería", I.Image, onPick)
            }
        }
        SliderRow("Desenfoque", s.imgBlur.toFloat(), 0f..25f, "${s.imgBlur}",
            { v -> vm.update(false) { it.copy(imgBlur = v.toInt()) } }, { vm.update { it } })
        SliderRow("Oscurecer imagen", s.imgDim, 0f..0.85f, "${(s.imgDim * 100).toInt()}%",
            { v -> vm.update(false) { it.copy(imgDim = v) } }, { vm.update { it } })
    }
}
