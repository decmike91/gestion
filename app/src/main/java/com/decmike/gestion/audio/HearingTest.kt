package com.decmike.gestion.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Ear(val label: String) { RIGHT("Oreille droite"), LEFT("Oreille gauche") }

data class HearingTestState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val ear: Ear = Ear.RIGHT,
    val freqIndex: Int = 0,
    val levelDb: Int = START_DB,
    val step: Int = 0,
    val totalSteps: Int = Audiogram.FREQS.size * 2,
    /** Seuils bruts en dBFS (null = pas encore mesuré). */
    val rawRight: List<Int?> = List(Audiogram.FREQS.size) { null },
    val rawLeft: List<Int?> = List(Audiogram.FREQS.size) { null },
) {
    companion object {
        const val START_DB = -85
        const val MAX_DB = -6
    }
}

/**
 * Test auditif indicatif par sons purs pulsés, méthode ascendante par pas de 5 dB.
 * Sans casque étalonné, le résultat est relatif : il sert à personnaliser l'égalisation,
 * pas à établir un diagnostic médical.
 */
class HearingTest(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HearingTestState())
    val state: StateFlow<HearingTestState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var heard = false

    fun start() {
        job?.cancel()
        _state.value = HearingTestState(running = true)
        job = scope.launch(Dispatchers.Default) { run() }
    }

    fun heard() { heard = true }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(running = false)
    }

    private suspend fun run() {
        val rate = 44100
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(rate * 2 * 4 / 4)
            .build()
        track.play()
        try {
            var step = 0
            for (ear in Ear.entries) {
                for ((fi, freq) in Audiogram.FREQS.withIndex()) {
                    var level = HearingTestState.START_DB
                    heard = false
                    var result: Int? = null
                    while (scope.isActive && job?.isActive == true && level <= HearingTestState.MAX_DB) {
                        _state.value = _state.value.copy(ear = ear, freqIndex = fi, levelDb = level, step = step)
                        // 3 bips de 250 ms par palier
                        repeat(3) {
                            if (!heard) {
                                writeTone(track, rate, freq.toFloat(), level, ear, 0.25f)
                                writeSilence(track, rate, 0.15f)
                            }
                        }
                        writeSilence(track, rate, 0.3f)
                        if (heard) { result = level; break }
                        level += 5
                    }
                    if (job?.isActive != true) return
                    val r = result ?: (HearingTestState.MAX_DB + 5)
                    _state.value = _state.value.let { s ->
                        if (ear == Ear.RIGHT) s.copy(rawRight = s.rawRight.toMutableList().also { it[fi] = r })
                        else s.copy(rawLeft = s.rawLeft.toMutableList().also { it[fi] = r })
                    }
                    step++
                    writeSilence(track, rate, 0.8f)
                }
            }
            _state.value = _state.value.copy(running = false, finished = true, step = step)
        } finally {
            try { track.stop(); track.release() } catch (_: Exception) {}
        }
    }

    private fun writeTone(track: AudioTrack, rate: Int, freq: Float, db: Int, ear: Ear, seconds: Float) {
        val frames = (rate * seconds).toInt()
        val amp = 10.0.pow(db / 20.0).toFloat()
        val ramp = (rate * 0.02f).toInt()
        val buf = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val env = when {
                i < ramp -> i / ramp.toFloat()
                i > frames - ramp -> (frames - i) / ramp.toFloat()
                else -> 1f
            }
            val v = (sin(2 * PI * freq * i / rate).toFloat()) * amp * env
            buf[i * 2] = if (ear == Ear.LEFT) v else 0f
            buf[i * 2 + 1] = if (ear == Ear.RIGHT) v else 0f
        }
        track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
    }

    private fun writeSilence(track: AudioTrack, rate: Int, seconds: Float) {
        val buf = FloatArray((rate * seconds).toInt() * 2)
        track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
    }

    companion object {
        /** Sensibilité typique de l'oreille selon la fréquence (dB relatifs à 1 kHz). */
        private val REFERENCE = listOf(12, 5, 0, -2, -4, 8)

        /** Convertit les seuils bruts en audiogramme relatif (dB HL estimés). */
        fun toAudiogram(s: HearingTestState): Audiogram {
            val corrected = (s.rawRight + s.rawLeft).mapIndexedNotNull { i, v ->
                v?.let { it - REFERENCE[i % REFERENCE.size] }
            }
            val best = corrected.minOrNull() ?: 0
            fun conv(raw: List<Int?>) = raw.mapIndexed { i, v ->
                (((v ?: best) - REFERENCE[i]) - best).toFloat().div(5f).roundToInt().times(5).coerceIn(-10, 100)
            }
            return Audiogram(right = conv(s.rawRight), left = conv(s.rawLeft))
        }
    }
}
