package com.decmike.gestion.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Applique la courbe calculée sur la session audio du lecteur grâce à DynamicsProcessing
 * (égaliseur multibande séparé gauche / droite, compression, limiteur anti-saturation).
 */
class EffectsApplier {
    private var dp: DynamicsProcessing? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId = 0
    private var last: SoundSettings? = null

    fun attach(session: Int) {
        if (session == sessionId && dp != null) return
        release()
        if (session == 0) return
        sessionId = session
        try {
            val n = EqEngine.BANDS.size
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, n,
                true, MBC_BANDS.size,
                false, 0,
                true,
            ).build()
            dp = DynamicsProcessing(0, session, config)
        } catch (e: Exception) {
            Log.w(TAG, "DynamicsProcessing indisponible", e)
            dp = null
        }
        try {
            virtualizer = Virtualizer(0, session)
        } catch (e: Exception) {
            virtualizer = null
        }
        last?.let { apply(it) }
    }

    fun apply(s: SoundSettings) {
        last = s
        val p = dp ?: return
        try {
            val n = EqEngine.BANDS.size
            for ((ch, gains) in listOf(0 to s.left, 1 to s.right)) {
                val eq = DynamicsProcessing.Eq(true, true, n)
                for (i in 0 until n) {
                    eq.setBand(i, DynamicsProcessing.EqBand(true, EqEngine.CUTOFFS[i], gains[i]))
                }
                p.setPreEqByChannelIndex(ch, eq)
            }
            // Pré-atténuation pour laisser de la marge avant le limiteur.
            val peak = maxOf(s.left.maxOrNull() ?: 0f, s.right.maxOrNull() ?: 0f)
            p.setInputGainAllChannelsTo(if (peak > 0) -peak * 0.6f else 0f)

            val mbc = DynamicsProcessing.Mbc(true, s.compression, MBC_BANDS.size)
            for (i in MBC_BANDS.indices) {
                mbc.setBand(
                    i,
                    DynamicsProcessing.MbcBand(
                        s.compression, MBC_BANDS[i],
                        10f, 150f, 3f, -32f, 6f,
                        -90f, 1f, 0f, if (s.compression) 5f else 0f,
                    )
                )
            }
            p.setMbcAllChannelsTo(mbc)

            p.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            p.enabled = s.enabled
        } catch (e: Exception) {
            Log.w(TAG, "Échec application EQ", e)
        }
        try {
            virtualizer?.let { v ->
                if (v.strengthSupported) v.setStrength(s.virtualizer.coerceIn(0, 1000).toShort())
                v.enabled = s.enabled && s.virtualizer > 0
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try { dp?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        dp = null
        virtualizer = null
        sessionId = 0
    }

    companion object {
        private const val TAG = "EffectsApplier"
        private val MBC_BANDS = floatArrayOf(250f, 4000f, 20000f)
    }
}
