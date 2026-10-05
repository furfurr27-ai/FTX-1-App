package dev.n0png.fieldops.android.dsp

import com.k1af.ft8af.ft8listener.FT8SignalListener
import com.k1af.ft8af.ft8transmit.GenerateFT8
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.EncodeRequest
import kotlin.math.abs

object FtFamilyRegressionTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "usage: FtFamilyRegressionTests FT8|FT4|FT2" }
        val mode = DigitalMode.valueOf(args[0])
        require(mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2))

        FT8SignalListener.lastInitMode = ""
        FT8SignalListener.deleteCount = 0
        GenerateFT8.lastEncoder = ""

        val engine = FtFamilyNativeEngine()
        checkThat(engine.modes == setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2), "FT-family engine mode set changed")

        val slot = 1_800_000_000_000L
        val decoded = engine.decode(mode, slot, FloatArray(12_000) { 0.01f })
        checkThat(decoded.size == 1, "$mode fake-native decode must return one mapped result")
        val result = decoded.single()
        checkThat(result.mode == mode, "$mode decode result mode mismatch")
        checkThat(result.utcMillis == slot, "$mode decode must preserve slot UTC")
        checkThat(result.text.contains("K1JT") && result.text.contains("N0PNG"), "$mode decode text mapping changed")
        checkThat(result.text.contains(mode.name), "$mode decode fixture identity missing")
        checkThat(result.audioHz == 1500f, "$mode decode audio frequency mapping changed")
        checkThat(result.snrDb == -12, "$mode decode SNR mapping changed")
        checkThat(FT8SignalListener.lastInitMode == mode.name, "$mode selected the wrong decoder entry point")
        checkThat(FT8SignalListener.deleteCount == 1, "$mode decoder handle must be deleted exactly once")

        val waveform = engine.encode(
            EncodeRequest(
                mode = mode,
                text = "N0PNG TEST",
                audioHz = 1500f,
                sampleRate = 12_000,
            )
        )

        val expectedToneCount = if (mode == DigitalMode.FT8) 79 else 105
        val expectedEncoder = if (mode == DigitalMode.FT8) "FT8" else "FT4"
        val expectedPeriod = when (mode) {
            DigitalMode.FT8 -> 0.160f
            DigitalMode.FT4 -> 0.048f
            DigitalMode.FT2 -> 0.024f
            else -> error("unsupported")
        }
        val expectedBt = if (mode == DigitalMode.FT8) 2.0f else 1.0f
        val expectedLength = kotlin.math.ceil(expectedToneCount * expectedPeriod * 12_000.0).toInt() + 1_200

        checkThat(GenerateFT8.lastEncoder == expectedEncoder, "$mode selected the wrong channel encoder")
        checkThat(GenerateFT8.lastToneCount == expectedToneCount, "$mode tone count changed")
        checkThat(abs(GenerateFT8.lastSymbolPeriod - expectedPeriod) < 1e-6f, "$mode symbol period changed")
        checkThat(abs(GenerateFT8.lastSymbolBt - expectedBt) < 1e-6f, "$mode Gaussian BT changed")
        checkThat(GenerateFT8.lastSampleRate == 12_000, "$mode synthesis sample rate changed")
        checkThat(abs(GenerateFT8.lastAudioHz - 1500f) < 1e-6f, "$mode synthesis audio frequency changed")
        checkThat(waveform.sampleRate == 12_000, "$mode waveform sample rate changed")
        checkThat(waveform.samples.size == expectedLength, "$mode waveform length changed: " + waveform.samples.size + " != " + expectedLength)
        checkThat(waveform.nominalAudioHz == 1500f, "$mode nominal audio metadata changed")
        checkThat(waveform.samples.any { abs(it) > 0.2f }, "$mode host fixture synthesis produced no audio")

        println("$mode FT-family regression: PASS assertions=$assertions")
    }
}
