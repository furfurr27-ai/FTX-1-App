package dev.n0png.fieldops.android.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FieldOps WSPR receive front-end.
 *
 * Input is one complete 120-second 12 kHz real PCM window. The passband is
 * mixed around the conventional 1500 Hz WSPR audio center, then decimated
 * 12,000 -> 3,000 -> 750 -> 375 complex samples/second using three
 * anti-alias FIR stages. The final decoder window is exactly 45,000 I/Q pairs.
 */
class WsprRxFrontEnd(
    private val centerHz: Double = 1500.0,
) {
    data class IqWindow(
        val i: FloatArray,
        val q: FloatArray,
        val sampleRate: Int = OUTPUT_RATE,
    ) {
        init {
            require(i.size == q.size)
            require(sampleRate == OUTPUT_RATE)
        }
    }

    fun convert(samples12k: FloatArray): IqWindow {
        require(samples12k.size == INPUT_SAMPLES) {
            "WSPR RX requires exactly $INPUT_SAMPLES samples (120 s at 12 kHz)"
        }

        val mixedI = FloatArray(samples12k.size)
        val mixedQ = FloatArray(samples12k.size)
        val step = 2.0 * PI * centerHz / INPUT_RATE.toDouble()
        var phase = 0.0

        for (index in samples12k.indices) {
            val sample = samples12k[index].toDouble()
            mixedI[index] = (sample * cos(phase)).toFloat()
            mixedQ[index] = (-sample * sin(phase)).toFloat()
            phase += step
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
        }

        val stage1 = ComplexFirDecimator(
            inputRate = 12_000,
            factor = 4,
            taps = lowPassTaps(sampleRate = 12_000, cutoffHz = 1_200.0, count = 65),
        ).process(mixedI, mixedQ)

        val stage2 = ComplexFirDecimator(
            inputRate = 3_000,
            factor = 4,
            taps = lowPassTaps(sampleRate = 3_000, cutoffHz = 300.0, count = 65),
        ).process(stage1.i, stage1.q)

        val stage3 = ComplexFirDecimator(
            inputRate = 750,
            factor = 2,
            taps = lowPassTaps(sampleRate = 750, cutoffHz = 140.0, count = 129),
        ).process(stage2.i, stage2.q)

        check(stage3.i.size == OUTPUT_SAMPLES) {
            "WSPR front-end produced ${stage3.i.size} I/Q samples, expected $OUTPUT_SAMPLES"
        }
        return IqWindow(stage3.i, stage3.q)
    }

    private data class ComplexBuffer(val i: FloatArray, val q: FloatArray)

    private class ComplexFirDecimator(
        inputRate: Int,
        private val factor: Int,
        private val taps: FloatArray,
    ) {
        init {
            require(inputRate > 0)
            require(factor > 1)
            require(taps.isNotEmpty() && taps.size % 2 == 1)
        }

        fun process(inputI: FloatArray, inputQ: FloatArray): ComplexBuffer {
            require(inputI.size == inputQ.size)
            require(inputI.size % factor == 0)

            val outI = FloatArray(inputI.size / factor)
            val outQ = FloatArray(inputQ.size / factor)
            val historyI = FloatArray(taps.size)
            val historyQ = FloatArray(taps.size)
            var write = 0
            var out = 0

            for (n in inputI.indices) {
                historyI[write] = inputI[n]
                historyQ[write] = inputQ[n]
                write++
                if (write == taps.size) write = 0

                if ((n + 1) % factor != 0) continue

                var accI = 0.0
                var accQ = 0.0
                var h = write - 1
                if (h < 0) h += taps.size
                for (k in taps.indices) {
                    val t = taps[k].toDouble()
                    accI += historyI[h] * t
                    accQ += historyQ[h] * t
                    h--
                    if (h < 0) h += taps.size
                }
                outI[out] = accI.toFloat()
                outQ[out] = accQ.toFloat()
                out++
            }

            check(out == outI.size)
            return ComplexBuffer(outI, outQ)
        }
    }

    companion object {
        const val INPUT_RATE = 12_000
        const val OUTPUT_RATE = 375
        const val SLOT_SECONDS = 120
        const val INPUT_SAMPLES = INPUT_RATE * SLOT_SECONDS
        const val OUTPUT_SAMPLES = OUTPUT_RATE * SLOT_SECONDS

        internal fun lowPassTaps(sampleRate: Int, cutoffHz: Double, count: Int): FloatArray {
            require(sampleRate > 0)
            require(cutoffHz > 0.0 && cutoffHz < sampleRate / 2.0)
            require(count >= 3 && count % 2 == 1)

            val taps = DoubleArray(count)
            val mid = (count - 1) / 2
            val normalized = cutoffHz / sampleRate.toDouble()
            var sum = 0.0

            for (n in 0 until count) {
                val m = n - mid
                val sinc = if (m == 0) {
                    2.0 * normalized
                } else {
                    sin(2.0 * PI * normalized * m) / (PI * m)
                }
                val window = 0.42 -
                    0.5 * cos(2.0 * PI * n / (count - 1).toDouble()) +
                    0.08 * cos(4.0 * PI * n / (count - 1).toDouble())
                taps[n] = sinc * window
                sum += taps[n]
            }

            require(sum != 0.0)
            return FloatArray(count) { (taps[it] / sum).toFloat() }
        }
    }
}
