package dev.n0png.fieldops.core.audio

/** Immutable PCM block. Consumers must not mutate [samples]. */
data class TimedPcmBlock(
    val utcStartNanos: Long,
    val sampleRate: Int,
    val samples: FloatArray,
    val sequence: Long = 0,
) {
    init {
        require(sampleRate > 0)
        require(samples.isNotEmpty())
    }

    val durationNanos: Long
        get() = samples.size.toLong() * 1_000_000_000L / sampleRate

    fun utcNanosAt(sampleIndex: Int): Long {
        require(sampleIndex in 0..samples.size)
        return utcStartNanos + sampleIndex.toLong() * 1_000_000_000L / sampleRate
    }
}

/** A generated TX waveform. Float samples are normalized to -1.0..+1.0. */
data class TxWaveform(
    val sampleRate: Int,
    val samples: FloatArray,
    val nominalAudioHz: Float,
) {
    init {
        require(sampleRate > 0)
        require(samples.isNotEmpty())
        require(nominalAudioHz >= 0f)
    }
}
