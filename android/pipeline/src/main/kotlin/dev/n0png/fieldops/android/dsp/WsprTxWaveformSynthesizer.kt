package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TxWaveform
import kotlin.math.PI
import kotlin.math.sin

/**
 * WSPR 4-FSK waveform synthesis in FieldOps' canonical 12 kHz modem domain.
 *
 * Channel symbols come from the pinned upstream get_wspr_channel_symbols()
 * implementation. This class performs only continuous-phase audio modulation;
 * it does not reimplement WSPR message packing/coding.
 */
object WsprTxWaveformSynthesizer {
    const val SAMPLE_RATE = 12_000
    const val SYMBOL_COUNT = 162
    const val SAMPLES_PER_SYMBOL = 8_192
    const val TOTAL_SAMPLES = SYMBOL_COUNT * SAMPLES_PER_SYMBOL
    const val TONE_SPACING_HZ = 375.0 / 256.0
    const val DURATION_SECONDS = TOTAL_SAMPLES.toDouble() / SAMPLE_RATE.toDouble()
    const val DEFAULT_AMPLITUDE = 0.8f

    fun toneHz(centerAudioHz: Double, symbol: Int): Double {
        require(symbol in 0..3) { "WSPR channel symbol must be in 0..3" }
        return centerAudioHz + (symbol - 1.5) * TONE_SPACING_HZ
    }

    fun synthesize(
        symbols: ByteArray,
        centerAudioHz: Float,
        amplitude: Float = DEFAULT_AMPLITUDE,
    ): TxWaveform {
        require(symbols.size == SYMBOL_COUNT) {
            "WSPR requires exactly " + SYMBOL_COUNT + " channel symbols"
        }
        require(amplitude > 0f && amplitude <= 1f) {
            "WSPR amplitude must be in (0, 1]"
        }

        val lowest = toneHz(centerAudioHz.toDouble(), 0)
        val highest = toneHz(centerAudioHz.toDouble(), 3)
        require(lowest > 0.0 && highest < SAMPLE_RATE / 2.0) {
            "WSPR tones must remain inside the 12 kHz audio Nyquist range"
        }

        val samples = FloatArray(TOTAL_SAMPLES)
        var phase = 0.0
        var cursor = 0

        for (raw in symbols) {
            val symbol = raw.toInt()
            require(symbol in 0..3) { "WSPR channel symbol must be in 0..3" }
            val frequency = toneHz(centerAudioHz.toDouble(), symbol)
            val step = 2.0 * PI * frequency / SAMPLE_RATE.toDouble()

            repeat(SAMPLES_PER_SYMBOL) {
                samples[cursor++] = (amplitude * sin(phase)).toFloat()
                phase += step
                if (phase >= 2.0 * PI) phase %= 2.0 * PI
            }
        }

        check(cursor == TOTAL_SAMPLES)
        return TxWaveform(
            sampleRate = SAMPLE_RATE,
            samples = samples,
            nominalAudioHz = centerAudioHz,
        )
    }
}
