package com.blackpool2026.muse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Familia única de iconos de Muse: cuadrícula de 24, trazo de 2 con puntas y uniones redondeadas.
 * Los de reproducción (y el corazón lleno) además van rellenos para que se vean sólidos.
 */
enum class I(val d: String, val fill: Boolean = false) {
    // ── transporte (rellenos, esquinas redondeadas)
    Play("M7,4.5L19.5,12L7,19.5Z", true),
    Pause("M7,4.5h3v15H7z M14,4.5h3v15h-3z", true),
    Next("M6,5L16,12L6,19Z M19,5L19,19", true),
    Prev("M18,5L8,12L18,19Z M5,5L5,19", true),
    Shuffle("M16,3h5v5 M4,20L21,3 M21,16v5h-5 M15,15L21,21 M4,4L9,9"),
    Repeat("M17,1L21,5L17,9 M3,11V9a4,4 0 0 1 4,-4h14 M7,23L3,19L7,15 M21,13v2a4,4 0 0 1 -4,4H3"),
    RepeatOne("M17,1L21,5L17,9 M3,11V9a4,4 0 0 1 4,-4h14 M7,23L3,19L7,15 M21,13v2a4,4 0 0 1 -4,4H3 M11,10h1v4"),

    // ── acciones
    Heart("M20.84,4.61a5.5,5.5 0 0 0 -7.78,0L12,5.67l-1.06,-1.06a5.5,5.5 0 0 0 -7.78,7.78l1.06,1.06L12,21.23l7.78,-7.78 1.06,-1.06a5.5,5.5 0 0 0 0,-7.78z", true),
    HeartOutline("M20.84,4.61a5.5,5.5 0 0 0 -7.78,0L12,5.67l-1.06,-1.06a5.5,5.5 0 0 0 -7.78,7.78l1.06,1.06L12,21.23l7.78,-7.78 1.06,-1.06a5.5,5.5 0 0 0 0,-7.78z"),
    Queue("M8,6h13 M8,12h13 M8,18h13 M3,6h0.01 M3,12h0.01 M3,18h0.01"),
    Search("M3,11a8,8 0 1 0 16,0a8,8 0 1 0 -16,0 M21,21L16.65,16.65"),
    More("M10.7,5a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0 M10.7,12a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0 M10.7,19a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0", true),
    Settings("M4,21v-7 M4,10V3 M12,21v-9 M12,8V3 M20,21v-5 M20,12V3 M1,14h6 M9,8h6 M17,16h6"),
    Check("M20,6L9,17L4,12"),
    Close("M18,6L6,18 M6,6L18,18"),
    Back("M19,12H5 M12,19L5,12L12,5"),
    Down("M6,9L12,15L18,9"),
    Up("M18,15L12,9L6,15"),
    Add("M12,5v14 M5,12h14"),
    Delete("M3,6h18 M19,6v14a2,2 0 0 1 -2,2H7a2,2 0 0 1 -2,-2V6 M8,6V4a2,2 0 0 1 2,-2h4a2,2 0 0 1 2,2v2 M10,11v6 M14,11v6"),
    Refresh("M23,4v6h-6 M20.49,15a9,9 0 1 1 -2.12,-9.36L23,10"),
    SelectAll("M9,11l3,3L22,4 M21,12v7a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2h11"),
    Share("M15,5a3,3 0 1 0 6,0a3,3 0 1 0 -6,0 M3,12a3,3 0 1 0 6,0a3,3 0 1 0 -6,0 M15,19a3,3 0 1 0 6,0a3,3 0 1 0 -6,0 M8.59,13.51l6.83,3.98 M15.41,6.51l-6.82,3.98"),
    Sort("M3,6h18 M3,12h12 M3,18h6"),
    Grid("M3,3h7v7H3z M14,3h7v7h-7z M14,14h7v7h-7z M3,14h7v7H3z"),

    // ── secciones
    Note("M9,18V5l12,-2v13 M3,18a3,3 0 1 0 6,0a3,3 0 1 0 -6,0 M15,16a3,3 0 1 0 6,0a3,3 0 1 0 -6,0"),
    Album("M2,12a10,10 0 1 0 20,0a10,10 0 1 0 -20,0 M9,12a3,3 0 1 0 6,0a3,3 0 1 0 -6,0"),
    Person("M20,21v-2a4,4 0 0 0 -4,-4H8a4,4 0 0 0 -4,4v2 M8,7a4,4 0 1 0 8,0a4,4 0 1 0 -8,0"),
    Folder("M22,19a2,2 0 0 1 -2,2H4a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2h5l2,3h9a2,2 0 0 1 2,2z"),
    Lists("M21,15V6 M18.5,18a2.5,2.5 0 1 0 0,-5a2.5,2.5 0 0 0 0,5z M12,12H3 M16,6H3 M12,18H3"),
    Timer("M2,12a10,10 0 1 0 20,0a10,10 0 1 0 -20,0 M12,6v6l4,2"),
    Tune("M21,4H14 M10,4H3 M21,12h-9 M8,12H3 M21,20h-5 M12,20H3 M14,2v4 M8,10v4 M16,18v4"),
    Speed("M12,14l4,-4 M3.34,19a10,10 0 1 1 17.32,0"),
    Info("M2,12a10,10 0 1 0 20,0a10,10 0 1 0 -20,0 M12,16v-4 M12,8h0.01"),
    Help("M2,12a10,10 0 1 0 20,0a10,10 0 1 0 -20,0 M9.09,9a3,3 0 0 1 5.83,1c0,2 -3,3 -3,3 M12,17h0.01"),
    Palette("M12,22a1,1 0 0 1 0,-20a10,9 0 0 1 10,9a5,5 0 0 1 -5,5h-2.25a1.75,1.75 0 0 0 -1.4,2.8l0.3,0.4a1.75,1.75 0 0 1 -1.4,2.8z M13.5,6.5h0.01 M17.5,10.5h0.01 M8.5,7.5h0.01 M6.5,12.5h0.01"),
    Image("M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2z M7,8.5a1.5,1.5 0 1 0 3,0a1.5,1.5 0 1 0 -3,0 M21,15l-5,-5L5,21")
}

@Composable
fun Ic(i: I, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val path = remember(i) { PathParser().parsePathString(i.d).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            if (i.fill) drawPath(path, tint)
            drawPath(path, tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
