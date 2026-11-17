package com.blackpool2026.muse.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.blackpool2026.muse.R
import com.blackpool2026.muse.data.Settings

data class ThemeDef(
    val id: String, val name: String, val group: String,
    val bg1: Long, val bg2: Long, val bg3: Long, val accent: Long,
    val imageRes: Int = 0
)

/** Colores ya resueltos de un tema, listos para usar en la interfaz. */
data class MuseColors(
    val bg1: Color, val bg2: Color, val bg3: Color,
    val text: Color, val textDim: Color,
    val accent: Color, val onAccent: Color,
    val dark: Boolean,
    val bar: Color, val card: Color, val stroke: Color,
    val imageRes: Int = 0, val imagePath: String? = null,
    val imgBlur: Int = 0, val imgDim: Float = 0f, val imgRev: Int = 0
) {
    val isImage: Boolean get() = imageRes != 0 || imagePath != null
}

val LocalMuse = compositionLocalOf { buildColors(0x0A0000, 0x3D0000, 0x3D0000, 0xFF3333) }

private fun c(hex: Int): Color = Color(0xFF000000L or hex.toLong())

fun buildColors(a: Int, b: Int, d: Int, accent: Int): MuseColors =
    buildColorsL(0xFF000000L or a.toLong(), 0xFF000000L or b.toLong(), 0xFF000000L or d.toLong(), 0xFF000000L or accent.toLong())

fun buildColorsL(a: Long, b: Long, d: Long, accent: Long, image: Boolean = false): MuseColors {
    val bg1 = Color(a); val bg2 = Color(b); val bg3 = Color(d); val ac = Color(accent)
    val avg = (bg1.luminance() + bg2.luminance() + bg3.luminance()) / 3f
    val dark = image || avg < 0.40f
    val text = if (dark) Color(0xFFF4F4F6) else Color(0xFF16161A)
    val bar = if (image) Color(0xE6000000) else lerp(bg3, if (dark) Color.Black else Color.White, if (dark) 0.40f else 0.45f)
    return MuseColors(
        bg1 = bg1, bg2 = bg2, bg3 = bg3,
        text = text, textDim = text.copy(alpha = 0.66f),
        accent = ac, onAccent = if (ac.luminance() > 0.45f) Color(0xFF101010) else Color.White,
        dark = dark, bar = bar.copy(alpha = if (image) 0.9f else 0.97f),
        card = text.copy(alpha = if (image) 0.10f else 0.07f),
        stroke = text.copy(alpha = 0.13f)
    )
}

object Themes {
    const val CUSTOM_COLOR = "custom_color"
    const val CUSTOM_IMAGE = "custom_image"

    val groups = listOf(
        "Oscuros", "Claros", "Grises", "Intensos", "Neón", "Cálidos", "Fríos",
        "Naturaleza", "Pastel", "Fusionados", "Imagen", "Personalizado"
    )

    private fun t(id: String, name: String, group: String, a: Int, b: Int, accent: Int, d: Int = b) =
        ThemeDef(id, name, group, 0xFF000000L or a.toLong(), 0xFF000000L or b.toLong(), 0xFF000000L or d.toLong(), 0xFF000000L or accent.toLong())

    private fun img(id: String, name: String, res: Int, accent: Int) =
        ThemeDef(id, name, "Imagen", 0xFF000000L, 0xFF000000L, 0xFF000000L, 0xFF000000L or accent.toLong(), res)

    val all: List<ThemeDef> = listOf(
        // ── Oscuros
        t("sangre", "Sangre", "Oscuros", 0x0A0000, 0x3D0000, 0xFF3333),
        t("carmesi", "Carmesí", "Oscuros", 0x140000, 0x5A0A1A, 0xE0314F),
        t("medianoche", "Medianoche", "Oscuros", 0x03071E, 0x0B2A6B, 0x4D8DFF),
        t("negro-amoled", "Negro AMOLED", "Oscuros", 0x000000, 0x0B0B0B, 0xFF4D6D),
        t("carbon", "Carbón", "Oscuros", 0x0F1113, 0x1E2226, 0x4FC3F7),
        t("bosque-noche", "Bosque Noche", "Oscuros", 0x04110A, 0x0F3A22, 0x3DDC84),
        t("vino", "Vino", "Oscuros", 0x14040C, 0x4A0E2B, 0xFF5C8A),
        t("uva-nocturna", "Uva Nocturna", "Oscuros", 0x0E0420, 0x3B1470, 0xB388FF),
        t("abismo", "Abismo", "Oscuros", 0x00080F, 0x00334D, 0x00D8FF),
        t("obsidiana", "Obsidiana", "Oscuros", 0x0B0B12, 0x1C1B2E, 0x9D8CFF),
        t("cafe-oscuro", "Café Oscuro", "Oscuros", 0x120A06, 0x3B2318, 0xD7A86E),
        t("tinta", "Tinta", "Oscuros", 0x050816, 0x131A3A, 0x6C8CFF),
        // ── Claros
        t("marfil", "Marfil", "Claros", 0xFBF6EA, 0xE8DCC4, 0xA61E2E),
        t("perla", "Perla", "Claros", 0xFFFFFF, 0xECEFF4, 0x3B5BDB),
        t("nube", "Nube", "Claros", 0xF5F7FA, 0xDDE6F0, 0x2B7DE9),
        t("crema", "Crema", "Claros", 0xFFF8E7, 0xF3E2BD, 0xB5651D),
        t("menta-fresca", "Menta Fresca", "Claros", 0xF0FFF7, 0xCDEFE0, 0x0B8F5A),
        t("lavanda", "Lavanda", "Claros", 0xF7F3FF, 0xE0D4F7, 0x6A3FD1),
        t("rosa-blanca", "Rosa Blanca", "Claros", 0xFFF5F8, 0xFAD7E3, 0xC2185B),
        t("cielo-claro", "Cielo Claro", "Claros", 0xF0F9FF, 0xCDEBFF, 0x0277BD),
        t("limon-suave", "Limón Suave", "Claros", 0xFFFDE8, 0xF7F0A8, 0x8A7500),
        t("papel", "Papel", "Claros", 0xF4EFE6, 0xE2D9C8, 0x5C4033),
        // ── Grises
        t("grafito", "Grafito", "Grises", 0x1A1A1A, 0x2E2E2E, 0xB0BEC5),
        t("pizarra", "Pizarra", "Grises", 0x1E252B, 0x37424B, 0x7FB3D5),
        t("acero", "Acero", "Grises", 0x2B3036, 0x4A525A, 0xA7C4DD),
        t("humo", "Humo", "Grises", 0x3A3A3C, 0x636366, 0xE0E0E0),
        t("ceniza", "Ceniza", "Grises", 0x555A5F, 0x8A9097, 0xFFFFFF),
        t("plata", "Plata", "Grises", 0xC9CDD3, 0xE6E8EC, 0x37474F),
        t("niebla", "Niebla", "Grises", 0xD6DADF, 0xF0F2F5, 0x455A64),
        t("plomo", "Plomo", "Grises", 0x23272B, 0x3C4349, 0xFFB74D),
        t("gris-azulado", "Gris Azulado", "Grises", 0x263238, 0x455A64, 0x80DEEA),
        // ── Intensos
        t("rojo-intenso", "Rojo Intenso", "Intensos", 0x7A0010, 0xD00024, 0xFFE082),
        t("azul-electrico", "Azul Eléctrico", "Intensos", 0x0A1F9E, 0x1E4DFF, 0xFFD54F),
        t("verde-intenso", "Verde Intenso", "Intensos", 0x00531F, 0x00A844, 0xFFF176),
        t("morado-intenso", "Morado Intenso", "Intensos", 0x3A0078, 0x7B1FF2, 0xFFAB40),
        t("naranja-intenso", "Naranja Intenso", "Intensos", 0x9E2A00, 0xFF6D00, 0xFFFFFF),
        t("magenta", "Magenta", "Intensos", 0x6A004A, 0xD6008F, 0xFFEB3B),
        t("turquesa-intenso", "Turquesa Intenso", "Intensos", 0x00595C, 0x00BFA5, 0xFFFFFF),
        t("rosa-intenso", "Rosa Intenso", "Intensos", 0x8B0035, 0xFF1F6B, 0xFFF59D),
        t("indigo", "Índigo", "Intensos", 0x1A0F6B, 0x3D2DD6, 0x69F0AE),
        t("abeja", "Abeja", "Intensos", 0x1A1400, 0x4D3D00, 0xFFD600),
        // ── Neón
        t("neon-rosa", "Neón Rosa", "Neón", 0x12001A, 0x3A0050, 0xFF2BD6),
        t("neon-verde", "Neón Verde", "Neón", 0x00110A, 0x003A1E, 0x39FF14),
        t("neon-azul", "Neón Azul", "Neón", 0x00081A, 0x001F4D, 0x00F0FF),
        t("neon-amarillo", "Neón Amarillo", "Neón", 0x141400, 0x3A3A00, 0xFFFF33),
        t("neon-naranja", "Neón Naranja", "Neón", 0x160800, 0x4D2000, 0xFF8A00),
        t("cyberpunk", "Cyberpunk", "Neón", 0x0D0221, 0x2E0A5A, 0xFF2A6D),
        t("synthwave", "Synthwave", "Neón", 0x1A0033, 0x5B0A91, 0x05D9E8),
        t("laser", "Láser", "Neón", 0x050014, 0x0A1A4D, 0xFF3D81),
        t("plasma", "Plasma", "Neón", 0x0A0020, 0x240D5E, 0x00FFC6),
        t("electrico", "Eléctrico", "Neón", 0x000A14, 0x002B4D, 0xFFE600),
        // ── Cálidos
        t("atardecer", "Atardecer", "Cálidos", 0x2A0A12, 0xB5381F, 0xFFC857),
        t("ambar", "Ámbar", "Cálidos", 0x1A0F00, 0x5C3A00, 0xFFB300),
        t("miel", "Miel", "Cálidos", 0x2B1A00, 0x7A4B00, 0xFFD54F),
        t("canela", "Canela", "Cálidos", 0x1E0F08, 0x5A2E1A, 0xFF9E6D),
        t("terracota", "Terracota", "Cálidos", 0x2A120C, 0x8A3B22, 0xFFC1A1),
        t("arena", "Arena", "Cálidos", 0xE8D5B0, 0xC9A66B, 0x5A3A12),
        t("fuego", "Fuego", "Cálidos", 0x1A0000, 0x7A1A00, 0xFF6A1F),
        t("durazno", "Durazno", "Cálidos", 0x3A1410, 0xC76B50, 0xFFE3D0),
        t("ladrillo", "Ladrillo", "Cálidos", 0x230B09, 0x6E2A22, 0xFF8A65),
        // ── Fríos
        t("oceano", "Océano", "Fríos", 0x001018, 0x00506B, 0x4DD0E1),
        t("glaciar", "Glaciar", "Fríos", 0x0A1A2A, 0x3A7CA5, 0xBFE9FF),
        t("hielo", "Hielo", "Fríos", 0xE3F4FF, 0xB7DDF5, 0x0B5C8A),
        t("laguna", "Laguna", "Fríos", 0x002A2A, 0x00796B, 0x64FFDA),
        t("cielo-nocturno", "Cielo Nocturno", "Fríos", 0x050B24, 0x1B3A8A, 0x82B1FF),
        t("marea", "Marea", "Fríos", 0x04202B, 0x0B5563, 0x7DF9FF),
        t("nordico", "Nórdico", "Fríos", 0x1B2430, 0x3E5C76, 0xE0FBFC),
        t("aguamarina", "Aguamarina", "Fríos", 0x022B2F, 0x0E7C86, 0xB2FFF5),
        t("zafiro", "Zafiro", "Fríos", 0x060B2E, 0x1F3FBF, 0x9FB8FF),
        // ── Naturaleza
        t("selva", "Selva", "Naturaleza", 0x03170B, 0x1F6B34, 0xB9F6CA),
        t("musgo", "Musgo", "Naturaleza", 0x0F1A0A, 0x3F5A1E, 0xC5E1A5),
        t("oliva", "Oliva", "Naturaleza", 0x1C1D08, 0x5B5E1B, 0xE6EE9C),
        t("pradera", "Pradera", "Naturaleza", 0xE6F4D7, 0xB7DD8E, 0x2E6B16),
        t("desierto", "Desierto", "Naturaleza", 0x2B1B0C, 0xA8742F, 0xFFE0A3),
        t("volcan", "Volcán", "Naturaleza", 0x0A0505, 0x4A1A0A, 0xFF5722),
        t("tormenta", "Tormenta", "Naturaleza", 0x0F1218, 0x37414F, 0x90CAF9),
        t("otono", "Otoño", "Naturaleza", 0x24100A, 0x8C4A14, 0xFFB347),
        t("primavera", "Primavera", "Naturaleza", 0xFFF0F5, 0xCDEAC0, 0xD81B60),
        // ── Pastel
        t("pastel-rosa", "Pastel Rosa", "Pastel", 0xFFE4EC, 0xFFC9DC, 0xE91E63),
        t("pastel-azul", "Pastel Azul", "Pastel", 0xDDEBFF, 0xBBD4FF, 0x3F6FD8),
        t("pastel-menta", "Pastel Menta", "Pastel", 0xD9FFF0, 0xB6F0D8, 0x1B8F63),
        t("pastel-lila", "Pastel Lila", "Pastel", 0xEBDDFF, 0xD4BDFF, 0x7B4BD8),
        t("pastel-durazno", "Pastel Durazno", "Pastel", 0xFFE6D5, 0xFFCDB0, 0xD9622B),
        t("pastel-limon", "Pastel Limón", "Pastel", 0xFFFAD1, 0xF5EE9E, 0x8A7D00),
        t("algodon", "Algodón de Azúcar", "Pastel", 0xFFD6F0, 0xC9E4FF, 0xC2308F),
        t("helado", "Helado", "Pastel", 0xFFEFD5, 0xFFD1E6, 0xD6457D),
        // ── Fusionados (degradados de 3 colores)
        t("aurora-boreal", "Aurora Boreal", "Fusionados", 0x03120F, 0x0F4C5C, 0x5CFFB1, 0x3A1C71),
        t("atardecer-mar", "Atardecer en el Mar", "Fusionados", 0x1A0B3D, 0xC94B4B, 0xFFD27F, 0xFF9E4A),
        t("galaxia", "Galaxia", "Fusionados", 0x05010F, 0x2B0A5C, 0xE040FB, 0x7A1FA2),
        t("fuego-hielo", "Fuego y Hielo", "Fusionados", 0x061A3F, 0x4A2C6F, 0xFFB199, 0xB03A2E),
        t("rosa-oro", "Rosa y Oro", "Fusionados", 0x2A0F1E, 0xA84B6B, 0xFFE2B0, 0xE8B86D),
        t("menta-lavanda", "Menta y Lavanda", "Fusionados", 0x0F2A2A, 0x3F5D8A, 0x9FFFE0, 0x8E6BBF),
        t("rubi-zafiro", "Rubí y Zafiro", "Fusionados", 0x4A0014, 0x3B1B6B, 0xFF7A9C, 0x0B2B7A),
        t("bosque-ambar", "Bosque y Ámbar", "Fusionados", 0x04170D, 0x2E5A2A, 0xFFD27F, 0xB8741A),
        t("oceano-coral", "Océano y Coral", "Fusionados", 0x002B3D, 0x0A6E7A, 0xFFB4A8, 0xFF6F61),
        t("violeta-dorado", "Violeta Dorado", "Fusionados", 0x1A0533, 0x5A1F9E, 0xFFE082, 0xD4A017),
        t("neblina-rosa", "Neblina Rosa", "Fusionados", 0x1E1B33, 0x6B4C8F, 0xFFC4E1, 0xE59AC0),
        t("tropico", "Trópico", "Fusionados", 0x003B2F, 0x00A388, 0xFFF1B8, 0xFFB347),
        // ── Imagen
        img("img-nebulosa-roja", "Nebulosa Roja", R.drawable.bg_nebula, 0xFF4D4D),
        img("img-aurora-1", "Aurora 1", R.drawable.bg_aurora_1, 0xB9A7FF),
        img("img-aurora-2", "Aurora 2", R.drawable.bg_aurora_2, 0x6EE7C8),
        img("img-cosmos-1", "Cosmos 1", R.drawable.bg_cosmos_1, 0xFFB26B),
        img("img-cosmos-2", "Cosmos 2", R.drawable.bg_cosmos_2, 0xC084FC),
        img("img-cosmos-3", "Cosmos 3", R.drawable.bg_cosmos_3, 0xFF9A4D),
        img("img-tormenta", "Rayos de Luz", R.drawable.bg_gothic_3, 0xE5E7EB)
    )

    fun find(id: String): ThemeDef? = all.firstOrNull { it.id == id }
    fun inGroup(g: String): List<ThemeDef> = all.filter { it.group == g }
    fun isImageTheme(id: String): Boolean = id == CUSTOM_IMAGE || (find(id)?.imageRes ?: 0) != 0
}

fun resolveTheme(s: Settings, customImagePath: String? = null): MuseColors {
    if (s.themeId == Themes.CUSTOM_COLOR)
        return buildColorsL(s.customBg1, s.customBg2, s.customBg2, s.customAccent)
    if (s.themeId == Themes.CUSTOM_IMAGE) {
        val b = buildColorsL(0xFF000000, 0xFF000000, 0xFF000000, s.imgAccent, image = true)
        return b.copy(imagePath = customImagePath ?: "", imgBlur = s.imgBlur, imgDim = s.imgDim, imgRev = s.imgRev)
    }
    val d = Themes.find(s.themeId) ?: Themes.all[0]
    if (d.imageRes != 0) {
        val b = buildColorsL(d.bg1, d.bg2, d.bg3, d.accent, image = true)
        return b.copy(imageRes = d.imageRes, imgBlur = s.imgBlur, imgDim = s.imgDim)
    }
    return buildColorsL(d.bg1, d.bg2, d.bg3, d.accent)
}
