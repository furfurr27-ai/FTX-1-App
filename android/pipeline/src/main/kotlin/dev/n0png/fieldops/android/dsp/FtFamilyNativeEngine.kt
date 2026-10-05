package dev.n0png.fieldops.android.dsp

import com.k1af.ft8af.Ft8Message
import com.k1af.ft8af.ft8listener.FT8SignalListener
import com.k1af.ft8af.ft8transmit.GenerateFT8
import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import kotlin.math.ceil

/**
 * FT8/FT4/FT2 adapter over the native implementation already present in the
 * uploaded FT8AF APK. FT2 uses the FT4 105-tone payload coding at half symbol
 * period, matching the mode metadata observed in that APK.
 */
class FtFamilyNativeEngine : WindowedDspEngine {
    override val modes = setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)
    private val native = FT8SignalListener()

    override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult> {
        require(mode in modes)
        require(samples12k.isNotEmpty())
        val handle = when (mode) {
            DigitalMode.FT2 -> native.InitDecoderFt2(slotStartUtcMillis, 12_000, samples12k.size, 2)
            DigitalMode.FT8, DigitalMode.FT4 -> native.InitDecoder(slotStartUtcMillis, 12_000, samples12k.size, mode == DigitalMode.FT8)
            else -> error("unsupported")
        }
        check(handle != 0L) { "native decoder allocation failed" }
        try {
            when (mode) {
                DigitalMode.FT2 -> {
                    native.DecoderFt2MonitorPressFloat(samples12k, handle)
                    native.setDecodeModeFt2(handle, false)
                }
                else -> {
                    native.DecoderMonitorPressFloat(samples12k, handle)
                    native.setDecodeMode(handle, false)
                }
            }
            val count = when (mode) {
                DigitalMode.FT2 -> native.DecoderFt2FindSync(handle)
                else -> native.DecoderFt8FindSync(handle)
            }.coerceAtLeast(0)
            val results = ArrayList<DecodeResult>(count)
            for (i in 0 until count) {
                val msg = Ft8Message()
                val ok = when (mode) {
                    DigitalMode.FT2 -> native.DecoderFt2Analysis(i, handle, msg)
                    else -> native.DecoderFt8Analysis(i, handle, msg)
                }
                if (!ok || !msg.isValid) continue
                val text = listOf(msg.callsignFrom, msg.callsignTo, msg.maidenGrid, msg.extraInfo)
                    .filter { it.isNotBlank() }.joinToString(" ")
                results += DecodeResult(
                    mode = mode,
                    utcMillis = slotStartUtcMillis,
                    audioHz = msg.freq_hz,
                    snrDb = msg.snr,
                    dtSeconds = msg.time_sec,
                    text = text,
                )
            }
            return results
        } finally {
            if (mode == DigitalMode.FT2) native.DeleteDecoderFt2(handle) else native.DeleteDecoder(handle)
        }
    }

    override fun encode(request: EncodeRequest): TxWaveform {
        require(request.mode in modes)
        val packed = ByteArray(10)
        val packedOk = GenerateFT8.packFreeTextTo77(request.text, packed)
        require(packedOk >= 0) { "FT-family text cannot be packed" }

        val isFt8 = request.mode == DigitalMode.FT8
        val tones = ByteArray(if (isFt8) 79 else 105)
        if (isFt8) GenerateFT8.ft8_encode(packed, tones) else GenerateFT8.ft4_encode(packed, tones)
        val symbolPeriod = when (request.mode) {
            DigitalMode.FT8 -> 0.160f
            DigitalMode.FT4 -> 0.048f
            DigitalMode.FT2 -> 0.024f
            else -> error("unsupported")
        }
        val symbolBt = when (request.mode) {
            DigitalMode.FT8 -> 2.0f
            else -> 1.0f
        }
        val length = ceil(tones.size * symbolPeriod * request.sampleRate).toInt() + request.sampleRate / 10
        val out = FloatArray(length)
        GenerateFT8.synth_gfsk(
            tones, tones.size, symbolPeriod, symbolBt,
            request.audioHz, request.sampleRate, out, 0,
        )
        return TxWaveform(request.sampleRate, out, request.audioHz)
    }
}
