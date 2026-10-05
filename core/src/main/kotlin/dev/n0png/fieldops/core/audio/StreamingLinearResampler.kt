package dev.n0png.fieldops.core.audio

/**
 * Stateful rational-rate PCM adapter for streaming modem audio.
 *
 * Output timestamps are derived with integer arithmetic so chunk boundaries do
 * not accumulate clock drift. Samples are linearly interpolated between source
 * points. CP-0002B uses this for the pinned JS8 TX tap (11,520 Hz) to the
 * FTX-1 USB playback domain (48,000 Hz).
 */
class StreamingLinearResampler(
    val inputRate: Int,
    val outputRate: Int,
) {
    init {
        require(inputRate > 0)
        require(outputRate > 0)
    }

    private var totalInputSamples = 0L
    private var nextOutputIndex = 0L
    private var previousSample: Float? = null

    fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)

        val base = totalInputSamples
        val last = base + input.size - 1L
        val estimated = ((input.size.toLong() * outputRate + inputRate - 1L) / inputRate + 4L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val out = ArrayList<Float>(estimated)

        fun sourceAt(index: Long): Float {
            if (index == base - 1L) return requireNotNull(previousSample)
            require(index in base..last) { "resampler source index outside current continuity window" }
            return input[(index - base).toInt()]
        }

        while (true) {
            val numerator = nextOutputIndex * inputRate.toLong()
            val leftIndex = numerator / outputRate.toLong()
            val remainder = numerator % outputRate.toLong()

            if (leftIndex > last) break
            if (remainder != 0L && leftIndex + 1L > last) break

            val left = sourceAt(leftIndex)
            val sample = if (remainder == 0L) {
                left
            } else {
                val right = sourceAt(leftIndex + 1L)
                val fraction = remainder.toDouble() / outputRate.toDouble()
                (left + (right - left) * fraction).toFloat()
            }
            out += sample
            nextOutputIndex++
        }

        totalInputSamples += input.size
        previousSample = input.last()
        return out.toFloatArray()
    }

    /**
     * Preserve the final source duration by extending the last sample only for
     * the fractional interpolation tail that cannot be produced without a next
     * source point. For 11,520 -> 48,000 this is at most a few output samples.
     */
    fun flushHold(): FloatArray {
        val last = previousSample ?: return FloatArray(0)
        val expectedOutput = totalInputSamples * outputRate.toLong() / inputRate.toLong()
        if (nextOutputIndex >= expectedOutput) return FloatArray(0)
        val count = (expectedOutput - nextOutputIndex).toInt()
        nextOutputIndex = expectedOutput
        return FloatArray(count) { last }
    }

    fun reset() {
        totalInputSamples = 0L
        nextOutputIndex = 0L
        previousSample = null
    }
}
