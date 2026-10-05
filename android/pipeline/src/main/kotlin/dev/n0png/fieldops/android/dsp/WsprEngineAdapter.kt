package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import kotlin.math.roundToInt

/**
 * WSPR adapter around the pinned Guenael/rtlsdr-wsprd codec.
 *
 * RX uses the pinned decoder at 375 sps complex I/Q. TX uses the pinned
 * upstream channel-symbol encoder, then FieldOps performs continuous-phase
 * 4-FSK synthesis in the canonical 12 kHz modem domain.
 *
 * This class never owns CAT/PTT or USB audio. Physical transmission is routed
 * separately through WsprTxController and Ftx1RadioSession.
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

    interface NativeBridge {
        fun decode375(i: FloatArray, q: FloatArray): List<NativeDecode>
        fun encodeSymbols(message: String): ByteArray
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
        require(request.sampleRate == WsprTxWaveformSynthesizer.SAMPLE_RATE) {
            "WSPR TX synthesis is fixed at 12 kHz in CP-0002D"
        }

        val normalized = request.text.trim().uppercase()
        require(normalized.isNotEmpty()) { "WSPR message must not be empty" }
        require(normalized.length <= 22) { "WSPR message must be at most 22 ASCII characters" }
        require(normalized.all { it.code in 0x20..0x7e }) { "WSPR message must be printable ASCII" }

        val symbols = native.encodeSymbols(normalized)
        return WsprTxWaveformSynthesizer.synthesize(
            symbols = symbols,
            centerAudioHz = request.audioHz,
        )
    }
}
