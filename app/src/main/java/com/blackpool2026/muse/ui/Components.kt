@file:OptIn(ExperimentalFoundationApi::class)

package com.blackpool2026.muse.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.Song
import com.blackpool2026.muse.data.fmtTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

val CoverDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

// ───────────────────────── fondo ─────────────────────────
@Composable
fun MuseBackground(t: MuseColors, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.fillMaxSize()) {
        if (!t.isImage) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.bg1, t.bg2, t.bg3))))
        } else {
            val img by produceState<ImageBitmap?>(null, t.imageRes, t.imagePath, t.imgRev) {
                value = withContext(CoverDispatcher) {
                    try {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        if (t.imageRes != 0) BitmapFactory.decodeResource(ctx.resources, t.imageRes, o)
                        else BitmapFactory.decodeFile(t.imagePath, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= 1080) s *= 2
                        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                        val b: Bitmap? = if (t.imageRes != 0) BitmapFactory.decodeResource(ctx.resources, t.imageRes, o2)
                        else BitmapFactory.decodeFile(t.imagePath, o2)
                        b?.asImageBitmap()
                    } catch (_: Throwable) { null }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black))
            img?.let {
                Image(
                    bitmap = it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(t.imgBlur.dp)
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = t.imgDim.coerceIn(0f, 0.9f))))
        }
    }
}

// ───────────────────────── texto y botones ─────────────────────────
@Composable
fun MText(
    text: String, size: Int = 15, weight: FontWeight = FontWeight.Normal,
    color: Color = LocalMuse.current.text, modifier: Modifier = Modifier, maxLines: Int = 1
) {
    Text(
        text, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, modifier = modifier
    )
}

@Composable
fun IconBtn(icon: I, onClick: () -> Unit, tint: Color = LocalMuse.current.text, size: Dp = 24.dp, box: Dp = 44.dp) {
    Box(
        Modifier.size(box).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ic(icon, tint, size) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalMuse.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) t.accent else t.card)
            .border(1.dp, if (selected) t.accent else t.stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { MText(text, 13, FontWeight.Medium, if (selected) t.onAccent else t.text) }
}

@Composable
fun ActionButton(text: String, icon: I?, onClick: () -> Unit, filled: Boolean = true, modifier: Modifier = Modifier) {
    val t = LocalMuse.current
    Row(
        modifier.clip(RoundedCornerShape(50))
            .background(if (filled) t.accent else t.card)
            .border(1.dp, if (filled) t.accent else t.stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Ic(icon, if (filled) t.onAccent else t.text, 18.dp); Spacer(Modifier.width(8.dp)) }
        MText(text, 14, FontWeight.SemiBold, if (filled) t.onAccent else t.text)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val t = LocalMuse.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = t.text, fontSize = 16.sp),
        cursorBrush = SolidColor(t.accent),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.card)
                    .border(1.dp, t.stroke, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(I.Search, t.textDim, 20.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) MText(hint, 16, color = t.textDim)
                    inner()
                }
                if (value.isNotEmpty()) Box(Modifier.clip(CircleShape).clickable { onChange("") }) { Ic(I.Close, t.textDim, 18.dp) }
            }
        }
    )
}

@Composable
fun SectionTitle(text: String) {
    val t = LocalMuse.current
    MText(text.uppercase(), 12, FontWeight.Bold, t.accent, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

// ───────────────────────── portadas ─────────────────────────
@Composable
fun CoverImage(song: Song?, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = song?.coverKey
    var bmp by remember(key, px) { mutableStateOf(song?.let { Covers.peek(it, px) }?.asImageBitmap()) }
    LaunchedEffect(key, px) {
        if (song != null && bmp == null) {
            val b = withContext(CoverDispatcher) { Covers.get(ctx, song, px) }
            if (b != null) bmp = b.asImageBitmap()
        }
    }
    Box(modifier.size(size).clip(shape)) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Placeholder(song?.title ?: "muse", size)
        }
    }
}

@Composable
fun Placeholder(seed: String, size: Dp) {
    val h = ((seed.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    val c1 = Color.hsv(h, 0.55f, 0.45f)
    val c2 = Color.hsv((h + 40f) % 360f, 0.7f, 0.2f)
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center
    ) { Ic(I.Note, Color.White.copy(alpha = 0.75f), size * 0.5f) }
}

// ───────────────────────── filas de canciones ─────────────────────────
@Composable
fun PlayingBars(color: Color) {
    val tr = rememberInfiniteTransition(label = "bars")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(1f, 0.3f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(0.5f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Box(Modifier.size(18.dp).drawBehind {
        val w = size.width / 5f
        val hs = listOf(a, b, c)
        for (i in 0 until 3) {
            val h = size.height * hs[i]
            drawRect(color, Offset(i * 2 * w, size.height - h), Size(w, h))
        }
    })
}

@Composable
fun SongRow(
    song: Song, playing: Boolean, isPlayingNow: Boolean, selected: Boolean, selecting: Boolean,
    fav: Boolean, onClick: () -> Unit, onLong: () -> Unit, menu: @Composable (dismiss: () -> Unit) -> Unit
) {
    val t = LocalMuse.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) t.accent.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            CoverImage(song, 50.dp)
            if (selecting) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = if (selected) 0.55f else 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Ic(I.Check, t.accent, 26.dp)
                }
            } else if (playing) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { if (isPlayingNow) PlayingBars(t.accent) else Ic(I.Play, t.accent, 22.dp) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MText(song.title, 15, FontWeight.Medium, if (playing) t.accent else t.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) { Ic(I.Heart, t.accent, 12.dp); Spacer(Modifier.width(4.dp)) }
                MText("${song.artist} · ${fmtTime(song.durationMs)}", 13, color = t.textDim)
            }
        }
        if (!selecting) {
            Box {
                IconBtn(I.More, { open = true }, t.textDim, 22.dp, 40.dp)
                DropdownMenu(open, { open = false }) { menu { open = false } }
            }
        }
    }
}

@Composable
fun SongCard(
    song: Song, playing: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier
) {
    val t = LocalMuse.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) t.accent.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp)
    ) {
        BoxSquare { CoverImage(song, 150.dp, Modifier.fillMaxSize(), RoundedCornerShape(12.dp)) }
        Spacer(Modifier.height(6.dp))
        MText(song.title, 13, FontWeight.Medium, if (playing) t.accent else t.text)
        MText(song.artist, 12, color = t.textDim)
    }
}

@Composable
fun BoxSquare(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.size(maxWidth)) { content() }
    }
}

@Composable
fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, fontSize = 15.sp) }, onClick = onClick)
}

// ───────────────────────── diálogos ─────────────────────────
@Composable
fun MuseDialog(
    title: String, onDismiss: () -> Unit, confirmText: String? = null, onConfirm: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val t = LocalMuse.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.bar.copy(alpha = 1f),
        titleContentColor = t.text, textContentColor = t.text,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { content() },
        confirmButton = {
            if (confirmText != null && onConfirm != null)
                TextButton(onClick = onConfirm) { Text(confirmText, color = t.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (onConfirm == null) "Cerrar" else "Cancelar", color = t.textDim) } }
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val t = LocalMuse.current
    var v by remember { mutableStateOf(initial) }
    MuseDialog(title, onDismiss, confirm, { if (v.isNotBlank()) onOk(v) }) {
        BasicTextField(
            value = v, onValueChange = { v = it }, singleLine = true,
            textStyle = TextStyle(color = t.text, fontSize = 17.sp),
            cursorBrush = SolidColor(t.accent),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.card)
                        .border(1.dp, t.stroke, RoundedCornerShape(12.dp)).padding(12.dp)
                ) { inner() }
            }
        )
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val t = LocalMuse.current
    MuseDialog(title, onDismiss) {
        Column {
            options.forEachIndexed { i, o ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(i) }.padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { if (i == selected) Ic(I.Check, t.accent, 20.dp) }
                    Spacer(Modifier.width(10.dp))
                    MText(o, 16, if (i == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: I, title: String, sub: String, modifier: Modifier = Modifier) {
    val t = LocalMuse.current
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Ic(icon, t.textDim, 56.dp)
        Spacer(Modifier.height(14.dp))
        MText(title, 18, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MText(sub, 14, color = t.textDim, maxLines = 4)
    }
}
