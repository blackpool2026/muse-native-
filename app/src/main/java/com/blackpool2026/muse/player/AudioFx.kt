package com.blackpool2026.muse.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.blackpool2026.muse.data.Store

/** Ecualizador y amplificación; se enlaza al id de sesión de audio del reproductor. */
object AudioFx {
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var session = 0

    /** Cada punto del control de amplificación (0..100) suma 0,30 dB: el máximo son +30 dB. */
    const val GAIN_PER_STEP_MB = 30

    @Volatile var bandFreqsHz: List<Int> = emptyList(); private set
    @Volatile var minLevel = -1500; private set
    @Volatile var maxLevel = 1500; private set
    @Volatile var presets: List<String> = emptyList(); private set

    fun attach(sessionId: Int) {
        if (sessionId == 0) return
        if (sessionId == session && eq != null) return
        release()
        session = sessionId
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val n = e.numberOfBands.toInt()
            bandFreqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            val r = e.bandLevelRange
            minLevel = r[0].toInt(); maxLevel = r[1].toInt()
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        } catch (_: Throwable) { eq = null }
        try { loud = LoudnessEnhancer(sessionId) } catch (_: Throwable) { loud = null }
        apply()
    }

    fun apply() {
        val s = Store.settings
        try {
            eq?.let { e ->
                e.setEnabled(s.eqEnabled)
                for (i in 0 until e.numberOfBands.toInt()) {
                    val lvl = (s.eqLevels.getOrNull(i) ?: 0).coerceIn(minLevel, maxLevel)
                    e.setBandLevel(i.toShort(), lvl.toShort())
                }
            }
        } catch (_: Throwable) {}
        try {
            loud?.let { l ->
                l.setTargetGain(s.boost.coerceIn(0, 100) * GAIN_PER_STEP_MB)
                l.setEnabled(s.boost > 0)
            }
        } catch (_: Throwable) {}
    }

    /** Aplica un preajuste del sistema y devuelve los niveles resultantes. */
    fun usePreset(index: Int): List<Int> {
        val e = eq ?: return emptyList()
        return try {
            e.usePreset(index.toShort())
            (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (_: Throwable) { emptyList() }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { loud?.release() } catch (_: Throwable) {}
        eq = null; loud = null; session = 0
    }
}
