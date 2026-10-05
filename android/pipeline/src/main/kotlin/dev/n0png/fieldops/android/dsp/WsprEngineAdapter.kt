package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine

/** Explicit adapter boundary for a GPLv3 wsprd/WSPR native module. */
class WsprEngineAdapter(private val bridge: Bridge) : WindowedDspEngine {
    interface Bridge {
        fun decode(slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult>
        fun encode(text: String, audioHz: Float, sampleRate: Int): TxWaveform
    }
    override val modes = setOf(DigitalMode.WSPR)
    override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray) =
        bridge.decode(slotStartUtcMillis, samples12k)
    override fun encode(request: EncodeRequest) = bridge.encode(request.text, request.audioHz, request.sampleRate)
}
