package dev.n0png.fieldops.core.aprs

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.StreamingDspEngine

/**
 * Streaming facade. It keeps a short rolling buffer so HDLC frames crossing
 * Android audio callbacks are still decoded. Duplicate frames are suppressed.
 */
class AprsAfskEngine : StreamingDspEngine {
    private var pending = FloatArray(0)
    private val seen = ArrayDeque<Int>()

    override fun accept48k(utcStartNanos: Long, samples48k: FloatArray): List<DecodeResult> {
        require(samples48k.isNotEmpty())
        val merged = FloatArray(pending.size + samples48k.size)
        pending.copyInto(merged)
        samples48k.copyInto(merged, pending.size)
        val frames = Bell202Afsk.demodulateAx25(merged)
        val out = mutableListOf<DecodeResult>()
        for (f in frames) {
            val hash = f.contentHashCode()
            if (hash in seen) continue
            seen.add(hash)
            while (seen.size > 64) seen.removeFirst()
            out += DecodeResult(
                mode = DigitalMode.APRS,
                utcMillis = utcStartNanos / 1_000_000L,
                text = "APRS AX.25 ${f.size} bytes",
                raw = f,
            )
        }
        // Keep 1.5 seconds so a normal packet can straddle callbacks.
        val keep = minOf(72_000, merged.size)
        pending = merged.copyOfRange(merged.size - keep, merged.size)
        return out
    }
}
