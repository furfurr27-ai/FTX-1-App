package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine

/**
 * Boundary for JS8Call-improved's Android `js8core-lib` AAR/JNI engine.
 * The upstream Android port already separates core/, adapters/android/ and JNI.
 * Wiring to its AAR belongs here so its GPLv3 boundary remains explicit.
 */
class Js8EngineAdapter(private val bridge: Bridge) : WindowedDspEngine {
    interface Bridge {
        fun decode(slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult>
        fun encode(text: String, audioHz: Float, sampleRate: Int): TxWaveform
    }
    override val modes = setOf(DigitalMode.JS8)
    override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray) =
        bridge.decode(slotStartUtcMillis, samples12k)
    override fun encode(request: EncodeRequest) = bridge.encode(request.text, request.audioHz, request.sampleRate)
}
