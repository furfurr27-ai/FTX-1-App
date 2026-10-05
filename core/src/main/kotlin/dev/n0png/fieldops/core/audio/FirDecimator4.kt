package dev.n0png.fieldops.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stateful 48 kHz -> 12 kHz FIR decimator.
 *
 * The weak-signal modes only need a few kHz of audio bandwidth.  A 97-tap
 * Blackman-windowed sinc low-pass at 4.8 kHz gives useful alias rejection
 * before decimation while remaining cheap on a modern phone.
 */
class FirDecimator4(
    private val inputRate: Int = 48_000,
    private val outputRate: Int = 12_000,
    tapsCount: Int = 97,
    cutoffHz: Double = 4_800.0,
) {
    init {
        require(inputRate == outputRate * 4) { "This implementation is specifically 4:1" }
        require(tapsCount >= 17 && tapsCount % 2 == 1)
        require(cutoffHz > 0.0 && cutoffHz < outputRate / 2.0)
    }

    data class Result(
        val samples: FloatArray,
        /** Input-sample index in this input block that produced output[0]. */
        val firstOutputInputIndex: Int,
        /** Constant FIR group delay in input samples. */
        val groupDelayInputSamples: Int,
    )

    private val taps = designLowPass(tapsCount, cutoffHz / inputRate)
    private val delay = FloatArray(taps.size)
    private var delayPos = 0
    private var totalInputSamples = 0L

    val groupDelayInputSamples: Int = (taps.size - 1) / 2

    fun reset() {
        delay.fill(0f)
        delayPos = 0
        totalInputSamples = 0L
    }

    fun process(input: FloatArray): Result {
        if (input.isEmpty()) return Result(FloatArray(0), -1, groupDelayInputSamples)
        val tmp = FloatArray((input.size + 3) / 4 + 1)
        var outCount = 0
        var first = -1

        for (i in input.indices) {
            delay[delayPos] = input[i]
            if ((totalInputSamples and 3L) == 0L) {
                var acc = 0.0
                var p = delayPos
                for (k in taps.indices) {
                    acc += taps[k] * delay[p]
                    p--
                    if (p < 0) p = delay.lastIndex
                }
                if (first < 0) first = i
                tmp[outCount++] = acc.toFloat()
            }
            delayPos++
            if (delayPos == delay.size) delayPos = 0
            totalInputSamples++
        }
        return Result(tmp.copyOf(outCount), first, groupDelayInputSamples)
    }

    companion object {
        /** fc is cycles/sample, 0 < fc < 0.5. */
        private fun designLowPass(n: Int, fc: Double): DoubleArray {
            val h = DoubleArray(n)
            val m = (n - 1) / 2.0
            var sum = 0.0
            for (i in 0 until n) {
                val x = i - m
                val ideal = if (x == 0.0) 2.0 * fc else sin(2.0 * PI * fc * x) / (PI * x)
                val blackman = 0.42 - 0.5 * cos(2.0 * PI * i / (n - 1)) +
                    0.08 * cos(4.0 * PI * i / (n - 1))
                h[i] = ideal * blackman
                sum += h[i]
            }
            for (i in h.indices) h[i] /= sum
            return h
        }
    }
}
