package com.blackpool2026.muse.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel
import com.blackpool2026.muse.Route
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.player.PlayerUi
import kotlinx.coroutines.delay

@Composable
fun MuseRoot(vm: MuseViewModel, askPerm: () -> Unit, openSettings: () -> Unit) {
    val ctxRoot = LocalContext.current
    val st = vm.settings
    val imgRes = if (st.themeId == Themes.CUSTOM_IMAGE) 0 else (Themes.find(st.themeId)?.imageRes ?: 0)
    val isImg = st.themeId == Themes.CUSTOM_IMAGE || imgRes != 0
    // Brillo medio de la imagen de fondo, para elegir texto claro u oscuro automáticamente.
    val luma by produceState(0.25f, st.themeId, st.imgRev, imgRes) {
        value = if (!isImg) 0.25f else withContext(Dispatchers.IO) {
            sampleLuma(ctxRoot, imgRes, if (st.themeId == Themes.CUSTOM_IMAGE) vm.custom else null)
        }
    }
    val t = remember(vm.settings, vm.custom, luma) { resolveTheme(vm.settings, vm.custom, luma) }
    val act = ctxRoot as? ComponentActivity
    LaunchedEffect(t.dark) {
        val style = if (t.dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        act?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    val bar = t.bar.copy(alpha = 1f)
    val scheme = remember(t) {
        if (t.dark) darkColorScheme(
            primary = t.accent, onPrimary = t.onAccent, background = bar, onBackground = t.text,
            surface = bar, onSurface = t.text, surfaceVariant = bar, onSurfaceVariant = t.textDim,
            surfaceContainer = bar, surfaceContainerHigh = bar, surfaceContainerHighest = bar
        ) else lightColorScheme(
            primary = t.accent, onPrimary = t.onAccent, background = bar, onBackground = t.text,
            surface = bar, onSurface = t.text, surfaceVariant = bar, onSurfaceVariant = t.textDim,
            surfaceContainer = bar, surfaceContainerHigh = bar, surfaceContainerHighest = bar
        )
    }
    CompositionLocalProvider(LocalMuse provides t) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize()) {
                MuseBackground(t)
                if (!vm.hasPerm) PermissionScreen(askPerm, openSettings) else MainContent(vm)
            }
        }
    }
}

/** Brillo medio (0..1) de una imagen, leyéndola en pequeño. */
private fun sampleLuma(ctx: Context, res: Int, path: String?): Float = try {
    fun decode(o: BitmapFactory.Options): Bitmap? =
        if (res != 0) BitmapFactory.decodeResource(ctx.resources, res, o) else BitmapFactory.decodeFile(path, o)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var s = 1
    while (bounds.outWidth / (s * 2) >= 64) s *= 2
    val b = decode(BitmapFactory.Options().apply { inSampleSize = s })
    if (b == null) 0.25f else {
        val sm = Bitmap.createScaledBitmap(b, 16, 16, true)
        var sum = 0f
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = sm.getPixel(x, y)
            sum += (0.299f * AColor.red(p) + 0.587f * AColor.green(p) + 0.114f * AColor.blue(p)) / 255f
        }
        sum / 256f
    }
} catch (_: Throwable) { 0.25f }

@Composable
fun PermissionScreen(askPerm: () -> Unit, openSettings: () -> Unit) {
    val t = LocalMuse.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Ic(I.Note, t.accent, 84.dp)
        Spacer(Modifier.height(20.dp))
        MText("Muse", 34, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        MText(
            "Para mostrar tu música, Muse necesita permiso para leer los archivos de audio de tu teléfono. " +
                "También te pedirá permiso para mostrar los controles en la notificación.",
            15, color = t.textDim, maxLines = 6
        )
        Spacer(Modifier.height(28.dp))
        ActionButton("Dar permiso", I.Check, askPerm)
        Spacer(Modifier.height(12.dp))
        ActionButton("Abrir ajustes del sistema", I.Settings, openSettings, filled = false)
    }
}

@Composable
fun MainContent(vm: MuseViewModel) {
    val ui by vm.conn.state.collectAsState()
    val route = vm.current
    // Selector de imagen para portadas personalizadas (lo piden varios menús).
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.applyCover(uri) else vm.cancelCover()
    }
    LaunchedEffect(vm.coverTargets) { if (vm.coverTargets != null) coverPicker.launch("image/*") }
    LaunchedEffect(vm.hasPerm, vm.allFolders.size, vm.settings.onboarded) {
        if (!vm.settings.onboarded && vm.allFolders.isNotEmpty() && vm.current == null) vm.push(Route.FolderPicker)
    }
    BackHandler(enabled = vm.playerOpen || route != null || vm.selection.isNotEmpty() || vm.searchOpen) {
        when {
            vm.playerOpen -> vm.playerOpen = false
            vm.selection.isNotEmpty() -> vm.clearSelection()
            route != null -> vm.pop()
            vm.searchOpen -> { vm.searchOpen = false; vm.setQuery("") }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (route == null) HomeScreen(vm, ui)
                else when (route) {
                    Route.Settings, Route.Themes, Route.Eq, Route.FolderPicker, Route.Help -> SettingsScreen(vm, route)
                    else -> DetailScreen(vm, ui, route)
                }
            }
            MiniPlayer(vm, ui)
            if (route == null) BottomNav(vm) else Spacer(Modifier.navigationBarsPadding())
        }
        AnimatedVisibility(
            visible = vm.playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) { PlayerScreen(vm, ui) }
        val ids = vm.pickerIds
        if (ids != null) PlaylistPicker(vm, ids)
        vm.pendingTutorial?.let { TutorialDialog(vm, it) }
    }
}

/** Posición de reproducción actualizada mientras suena. */
@Composable
fun rememberPosition(vm: MuseViewModel, ui: PlayerUi): State<Long> {
    val s = remember { mutableStateOf(0L) }
    LaunchedEffect(ui.playing, ui.mediaId) {
        s.value = vm.conn.position()
        while (ui.playing) {
            delay(400)
            s.value = vm.conn.position()
        }
    }
    return s
}

@Composable
fun MiniPlayer(vm: MuseViewModel, ui: PlayerUi) {
    val t = LocalMuse.current
    @Suppress("UNUSED_VARIABLE") val refresh = vm.songs
    val song = Library.byId[ui.mediaId]
    if (song == null) return
    val pos by rememberPosition(vm, ui)
    val frac = if (ui.durationMs > 0) (pos.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().background(t.bar)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(t.stroke)) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(t.accent))
        }
        Row(
            Modifier.fillMaxWidth().clickable { vm.playerOpen = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(song, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MText(song.title, 14, FontWeight.SemiBold)
                MText(song.artist, 12, color = t.textDim)
            }
            IconBtn(I.Prev, { vm.conn.prev() })
            IconBtn(if (ui.playing) I.Pause else I.Play, { vm.conn.toggle() }, t.accent, 30.dp, 48.dp)
            IconBtn(I.Next, { vm.conn.next() })
        }
    }
}

@Composable
fun BottomNav(vm: MuseViewModel) {
    val t = LocalMuse.current
    val items = listOf(
        Triple(I.Note, "Canciones", 0), Triple(I.Album, "Álbumes", 1), Triple(I.Person, "Artistas", 2),
        Triple(I.Lists, "Listas", 3), Triple(I.Heart, "Favoritas", 4)
    )
    Row(Modifier.fillMaxWidth().background(t.bar).navigationBarsPadding().padding(vertical = 6.dp)) {
        items.forEach { (icon, label, idx) ->
            val sel = vm.tab == idx
            Column(
                Modifier.weight(1f).clickable { vm.tab = idx; vm.clearSelection() }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Ic(icon, if (sel) t.accent else t.textDim, 24.dp)
                MText(label, 11, if (sel) FontWeight.Bold else FontWeight.Normal, if (sel) t.accent else t.textDim)
            }
        }
    }
}
