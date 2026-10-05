package dev.n0png.fieldops.core.dsp

import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode

data class DecodeResult(
    val mode: DigitalMode,
    val utcMillis: Long,
    val audioHz: Float? = null,
    val snrDb: Int? = null,
    val dtSeconds: Float? = null,
    val text: String,
    val raw: ByteArray? = null,
)

data class EncodeRequest(
    val mode: DigitalMode,
    val text: String,
    val audioHz: Float,
    val sampleRate: Int = 12_000,
)

interface WindowedDspEngine {
    val modes: Set<DigitalMode>
    fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult>
    fun encode(request: EncodeRequest): TxWaveform
}

/**
 * Continuous 12 kHz weak-signal consumer.
 *
 * JS8 belongs here rather than in a UTC slot-window assembler. The engine owns
 * its internal decode timing; FieldOps owns the shared 12 kHz PCM source.
 */
interface Streaming12kDspEngine {
    val mode: DigitalMode
    fun start(onDecode: (DecodeResult) -> Unit)
    fun stop()
    fun accept12k(block: TimedPcmBlock)
}

interface StreamingDspEngine {
    fun accept48k(utcStartNanos: Long, samples48k: FloatArray): List<DecodeResult>
}
