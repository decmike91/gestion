package com.decmike.gestion.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Réglage final envoyé au moteur d'effets. */
data class SoundSettings(
    val enabled: Boolean,
    val left: FloatArray,
    val right: FloatArray,
    val virtualizer: Int,
    val compression: Boolean,
    val summary: String,
) {
    override fun equals(other: Any?): Boolean = other is SoundSettings && enabled == other.enabled &&
        left.contentEquals(other.left) && right.contentEquals(other.right) &&
        virtualizer == other.virtualizer && compression == other.compression && summary == other.summary

    override fun hashCode(): Int = left.contentHashCode() * 31 + right.contentHashCode()
}

/**
 * Calcule la courbe d'égalisation = correction auditive (audiogramme) + goûts de l'auditeur
 * + correction du système d'écoute + ambiance.
 */
object EqEngine {
    val BANDS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    /** Fréquence haute de chaque bande (DynamicsProcessing attend des fréquences de coupure). */
    val CUTOFFS: FloatArray = FloatArray(BANDS.size) { i ->
        if (i == BANDS.lastIndex) 20000f else sqrt(BANDS[i] * BANDS[i + 1])
    }

    private fun log2(x: Float) = (ln(x.toDouble()) / ln(2.0)).toFloat()

    /** Interpolation logarithmique du seuil (dB HL) à une fréquence donnée. */
    fun thresholdAt(freq: Float, values: List<Int>): Float {
        val f = Audiogram.FREQS
        if (freq <= f.first()) return values.first().toFloat()
        if (freq >= f.last()) return values.last().toFloat()
        for (i in 0 until f.lastIndex) {
            if (freq <= f[i + 1]) {
                val t = (log2(freq) - log2(f[i].toFloat())) / (log2(f[i + 1].toFloat()) - log2(f[i].toFloat()))
                return values[i] + t * (values[i + 1] - values[i])
            }
        }
        return values.last().toFloat()
    }

    /**
     * Gain de compensation inspiré de la règle du demi-gain :
     * pas de correction sous 20 dB HL, puis ~0,5 dB de gain par dB de perte,
     * réduit dans les graves (effet de masque) et modulé par la préférence de l'auditeur.
     */
    fun hearingGain(freq: Float, values: List<Int>, compensationPct: Int): Float {
        val loss = max(0f, thresholdAt(freq, values) - 20f)
        val lowFactor = when {
            freq < 200f -> 0.3f
            freq < 400f -> 0.6f
            else -> 1f
        }
        return loss * 0.5f * lowFactor * (compensationPct / 100f)
    }

    private fun shelf(freq: Float, bass: Int, treble: Int): Float {
        val b = when {
            freq <= 62f -> 1f
            freq <= 125f -> 0.8f
            freq <= 250f -> 0.4f
            else -> 0f
        }
        val t = when {
            freq >= 8000f -> 1f
            freq >= 4000f -> 0.6f
            freq >= 2000f -> 0.2f
            else -> 0f
        }
        return bass * b + treble * t
    }

    fun compute(
        listener: ListenerProfile?,
        device: DeviceProfile?,
        ambiance: Ambiance?,
        enabled: Boolean,
    ): SoundSettings {
        val n = BANDS.size
        val left = FloatArray(n)
        val right = FloatArray(n)
        val maxBoost = (listener?.maxBoost ?: 12).toFloat()
        for (i in 0 until n) {
            val f = BANDS[i]
            val common = (device?.gains?.getOrNull(i) ?: 0f) +
                (ambiance?.gains?.getOrNull(i) ?: 0f) +
                (listener?.let { shelf(f, it.bass, it.treble) } ?: 0f)
            val gl = common + (listener?.let { hearingGain(f, it.audiogram.left, it.compensation) } ?: 0f)
            val gr = common + (listener?.let { hearingGain(f, it.audiogram.right, it.compensation) } ?: 0f)
            left[i] = gl.coerceIn(-15f, maxBoost)
            right[i] = gr.coerceIn(-15f, maxBoost)
        }
        val bal = listener?.balance ?: 0
        if (bal > 0) for (i in 0 until n) left[i] -= abs(bal).toFloat()
        if (bal < 0) for (i in 0 until n) right[i] -= abs(bal).toFloat()

        val summary = listOfNotNull(listener?.name, device?.name, ambiance?.name).joinToString(" · ")
        return SoundSettings(
            enabled = enabled,
            left = left,
            right = right,
            virtualizer = ambiance?.virtualizer ?: 0,
            compression = ambiance?.compression ?: false,
            summary = summary,
        )
    }
}
