package com.blackpool2026.muse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackpool2026.muse.MuseViewModel

data class TutStep(val icon: I, val title: String, val text: String)
data class Tutorial(val id: String, val name: String, val icon: I, val steps: List<TutStep>)

/** Tutoriales de Muse. Cada uno sale solo la primera vez que usas esa parte de la app. */
object Tutorials {
    val all: List<Tutorial> = listOf(
        Tutorial(
            "basics", "Primeros pasos", I.Note, listOf(
                TutStep(I.Note, "Bienvenido a Muse", "Muse reproduce la música que ya tienes en tu teléfono. No necesita internet ni cuenta."),
                TutStep(I.Folder, "Tu biblioteca", "Las canciones se leen solas. Si falta alguna, entra en Ajustes > Carpetas para elegir de dónde leer, o usa el menú de tres puntos > Buscar música nueva."),
                TutStep(I.Play, "Reproducir", "Toca una canción para reproducirla. Toca la barra de abajo para abrir el reproductor completo."),
                TutStep(I.More, "Más opciones", "Toca los tres puntos junto a una canción para añadirla a una lista, a favoritas o a la cola. Mantén pulsada una canción para seleccionar varias."),
                TutStep(I.Image, "Portadas propias", "Si una canción no tiene portada, usa los tres puntos > Cambiar portada y elige una imagen de tu galería."),
                TutStep(I.Help, "Ayuda cuando la necesites", "Cada parte nueva de la app te mostrará un consejo corto la primera vez. Puedes repasarlos en Ajustes > Ayuda y tutoriales.")
            )
        ),
        Tutorial(
            "player", "El reproductor", I.Play, listOf(
                TutStep(I.Play, "Controles", "Pausa, salta de canción o arrastra la barra para avanzar. Los botones de los lados activan mezclar y repetir."),
                TutStep(I.Heart, "Favoritas", "Toca el corazón para guardar la canción en Favoritas."),
                TutStep(I.Queue, "Cola de reproducción", "Con el icono de lista ves lo que viene después. Puedes reordenar o quitar canciones."),
                TutStep(I.Timer, "Temporizador y velocidad", "Usa el reloj para que la música se apague sola y cambia la velocidad de reproducción cuando quieras."),
                TutStep(I.More, "Menú del reproductor", "Los tres puntos de arriba te dejan ver la información de la canción, ir a su álbum o artista y cambiar su portada.")
            )
        ),
        Tutorial(
            "select", "Selección múltiple", I.SelectAll, listOf(
                TutStep(I.SelectAll, "Seleccionar varias", "Mantén pulsada una canción y toca otras para marcarlas. Arriba aparecen las acciones."),
                TutStep(I.Lists, "Qué puedes hacer", "Reproducirlas, añadirlas a la cola, a una lista o a favoritas, y ponerles la misma portada a todas a la vez."),
                TutStep(I.SelectAll, "Seleccionar todo", "El último icono de la barra superior selecciona todas las canciones de la pantalla.")
            )
        ),
        Tutorial(
            "lists", "Listas y favoritas", I.Lists, listOf(
                TutStep(I.Lists, "Tus listas", "En esta pestaña creas tus propias listas y ves las automáticas: más escuchadas, recientes y agregadas."),
                TutStep(I.Add, "Crear una lista", "Selecciona canciones o usa los tres puntos > Añadir a una lista y elige Nueva lista."),
                TutStep(I.Heart, "Favoritas", "Todo lo que marques con el corazón aparece en la pestaña Favoritas.")
            )
        ),
        Tutorial(
            "themes", "Temas y colores", I.Palette, listOf(
                TutStep(I.Palette, "Muchos temas", "Están ordenados por categorías. Toca una categoría y luego el tema que quieras."),
                TutStep(I.Image, "Tu imagen y tus colores", "En Personalizado puedes elegir tu propia imagen de fondo o crear tus propios colores."),
                TutStep(I.Info, "Texto siempre legible", "El texto cambia solo a claro u oscuro según el fondo. Si prefieres, fíjalo tú en Color del texto.")
            )
        ),
        Tutorial(
            "covers", "Portadas", I.Image, listOf(
                TutStep(I.Image, "De dónde salen", "Muse busca la portada dentro del archivo de audio. Si no tiene, verás un dibujo de color."),
                TutStep(I.Add, "Poner tu propia portada", "Con los tres puntos > Cambiar portada eliges una imagen de tu galería. En selección múltiple puedes ponerla a muchas canciones a la vez, y en un álbum a todo el álbum."),
                TutStep(I.Refresh, "Volver a la original", "Con los tres puntos > Quitar portada propia vuelve la portada original.")
            )
        ),
        Tutorial(
            "eq", "Ecualizador", I.Tune, listOf(
                TutStep(I.Tune, "Ecualizador", "Sube o baja cada banda para cambiar el sonido, o elige uno de los presets."),
                TutStep(I.Speed, "Amplificador", "Si el volumen te queda bajo, súbelo con el amplificador. Con cuidado: demasiado puede distorsionar.")
            )
        )
    )

    fun find(id: String): Tutorial? = all.firstOrNull { it.id == id }
}

@Composable
fun TutorialDialog(vm: MuseViewModel, id: String) {
    val tut = Tutorials.find(id)
    if (tut == null) {
        LaunchedEffect(id) { vm.closeTutorial() }
        return
    }
    val t = LocalMuse.current
    var step by remember(id) { mutableIntStateOf(0) }
    val last = step >= tut.steps.lastIndex
    val s = tut.steps[step.coerceIn(0, tut.steps.lastIndex)]
    MuseDialog(
        tut.name, { vm.closeTutorial() },
        if (last) "Entendido" else "Siguiente",
        { if (last) vm.closeTutorial() else step++ },
        if (last) "Cerrar" else "Omitir"
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(t.accent.copy(alpha = 0.18f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) { Ic(s.icon, t.accent, 32.dp) }
            Spacer(Modifier.height(14.dp))
            MText(s.title, 17, FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            MText(s.text, 14, color = t.textDim, maxLines = 10)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..tut.steps.lastIndex) {
                    Box(
                        Modifier.size(if (i == step) 9.dp else 6.dp).clip(CircleShape)
                            .background(if (i == step) t.accent else t.stroke)
                    )
                    if (i < tut.steps.lastIndex) Spacer(Modifier.width(7.dp))
                }
            }
        }
    }
}

@Composable
fun HelpScreen(vm: MuseViewModel) {
    val seen = vm.settings.tutorialSeen
    Column(Modifier.fillMaxSize()) {
        TopBar("Ayuda y tutoriales", { vm.pop() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.Top) {
            item { SectionTitle("Toca uno para verlo") }
            Tutorials.all.forEach { tut ->
                item(key = tut.id) {
                    ListRow(tut.icon, tut.name, if (seen.contains(tut.id)) "Visto" else "Nuevo") { vm.openTutorial(tut.id) }
                }
            }
        }
    }
}
