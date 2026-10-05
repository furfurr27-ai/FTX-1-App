package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import kotlin.math.roundToInt

/**
 * WSPR receive adapter around the pinned Guenael/rtlsdr-wsprd decoder.
 *
 * CP-0002C is receive-only. The native boundary accepts the decoder's native
 * 375 sps complex I/Q domain. FieldOps owns the 12 kHz real-audio front-end.
 * WSPR transmit stays deliberately unavailable until CP-0002D.
 */
class WsprEngineAdapter(
    private val native: NativeBridge,
    private val frontEnd: WsprRxFrontEnd = WsprRxFrontEnd(),
) : WindowedDspEngine {

    data class NativeDecode(
        val snrDb: Float,
        val dtSeconds: Float,
        val audioHz: Float,
        val driftHz: Float,
        val message: String,
        val call: String,
        val grid: String,
        val powerDbm: String,
    )

    fun interface NativeBridge {
        fun decode375(i: FloatArray, q: FloatArray): List<NativeDecode>
    }

    override val modes = setOf(DigitalMode.WSPR)

    override fun decode(
        mode: DigitalMode,
        slotStartUtcMillis: Long,
        samples12k: FloatArray,
    ): List<DecodeResult> {
        require(mode == DigitalMode.WSPR)
        val iq = frontEnd.convert(samples12k)
        return native.decode375(iq.i, iq.q).map { decoded ->
            DecodeResult(
                mode = DigitalMode.WSPR,
                utcMillis = slotStartUtcMillis,
                audioHz = decoded.audioHz,
                snrDb = decoded.snrDb.roundToInt(),
                dtSeconds = decoded.dtSeconds,
                text = decoded.message.trim(),
            )
        }
    }

    override fun encode(request: EncodeRequest): TxWaveform {
        require(request.mode == DigitalMode.WSPR)
        throw UnsupportedOperationException("WSPR TX is intentionally unavailable until CP-0002D")
    }
}
