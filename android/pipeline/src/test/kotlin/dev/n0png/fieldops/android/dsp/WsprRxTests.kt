package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.EncodeRequest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

object WsprRxTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bridge = WsprJniBridge()
        testNativeFixtureSymbols(bridge)
        testEndToEndReceive(bridge)
        testTxRemainsUnavailable(bridge)
        println("WSPR RX tests: PASS assertions=$assertions")
    }

    private fun testNativeFixtureSymbols(bridge: WsprJniBridge) {
        val symbols = bridge.encodeSymbolsForSelfTest(FIXTURE_MESSAGE)
        checkThat(symbols.size == 162, "native WSPR fixture must contain 162 symbols")
        checkThat(symbols.all { it.toInt() in 0..3 }, "all WSPR channel symbols must be in 0..3")
        checkThat(symbols.toSet().size >= 3, "fixture should exercise multiple WSPR tones")
    }

    private fun testEndToEndReceive(bridge: WsprJniBridge) {
        val symbols = bridge.encodeSymbolsForSelfTest(FIXTURE_MESSAGE)
        val samples12k = synthesizeReal12k(symbols)
        checkThat(samples12k.size == WsprRxFrontEnd.INPUT_SAMPLES, "fixture must be one complete WSPR slot")

        val frontEnd = WsprRxFrontEnd()
        val iq = frontEnd.convert(samples12k)
        checkThat(iq.sampleRate == 375, "WSPR native decoder input must be 375 sps")
        checkThat(iq.i.size == 45_000 && iq.q.size == 45_000, "WSPR native decoder window must be 45,000 I/Q pairs")
        checkThat(iq.i.any { abs(it) > 1e-4f }, "downconverted I channel must contain signal energy")
        checkThat(iq.q.any { abs(it) > 1e-4f }, "downconverted Q channel must contain signal energy")

        val native = bridge.decode375(iq.i, iq.q)
        checkThat(native.isNotEmpty(), "pinned native decoder must recover the synthesized fixture")
        val decoded = native.firstOrNull { it.call.trim() == "K1JT" }
            ?: error("native WSPR decode did not include K1JT: $native")
        checkThat(decoded.grid.trim() == "FN20", "decoded grid mismatch: ${decoded.grid}")
        checkThat(decoded.powerDbm.trim() == "20", "decoded power mismatch: ${decoded.powerDbm}")
        checkThat(decoded.message.contains("K1JT"), "decoded message must contain K1JT")
        checkThat(abs(decoded.audioHz - FIXTURE_AUDIO_HZ.toFloat()) < 2.0f, "decoded audio frequency mismatch: ${decoded.audioHz}")
        checkThat(abs(decoded.dtSeconds) < 0.5f, "decoded DT should stay near the upstream 2-second fixture reference: ${decoded.dtSeconds}")

        val slotStart = 1_800_000_000_000L
        val adapter = WsprEngineAdapter(bridge)
        val mapped = adapter.decode(DigitalMode.WSPR, slotStart, samples12k)
        checkThat(mapped.isNotEmpty(), "FieldOps WSPR adapter must expose the native decode")
        val result = mapped.firstOrNull { it.text.contains("K1JT") }
            ?: error("FieldOps WSPR result did not contain K1JT: $mapped")
        checkThat(result.mode == DigitalMode.WSPR, "FieldOps result mode mismatch")
        checkThat(result.utcMillis == slotStart, "FieldOps result must retain the slot start UTC")
        checkThat(result.audioHz != null && abs(result.audioHz!! - FIXTURE_AUDIO_HZ.toFloat()) < 2.0f, "FieldOps audioHz mismatch")
        checkThat(result.text.contains("FN20"), "FieldOps result must retain decoded locator")
        checkThat(result.text.contains("20"), "FieldOps result must retain decoded power")
    }

    private fun testTxRemainsUnavailable(bridge: WsprJniBridge) {
        val adapter = WsprEngineAdapter(bridge)
        var rejected = false
        try {
            adapter.encode(
                EncodeRequest(
                    mode = DigitalMode.WSPR,
                    text = "K1JT FN20 20",
                    audioHz = 1500f,
                    sampleRate = 12_000,
                )
            )
        } catch (_: UnsupportedOperationException) {
            rejected = true
        }
        checkThat(rejected, "CP-0002C must not expose WSPR TX waveform generation")
    }

    private fun synthesizeReal12k(symbols: ByteArray): FloatArray {
        val out = FloatArray(WsprRxFrontEnd.INPUT_SAMPLES)
        val samplesPerSymbol = 8_192
        val start = 2 * WsprRxFrontEnd.INPUT_RATE
        val toneSpacing = 375.0 / 256.0
        var phase = 0.0
        var noiseState = 0x1234ABCDL

        fun noise(): Float {
            noiseState = (1664525L * noiseState + 1013904223L) and 0xffffffffL
            val unit = ((noiseState ushr 8) and 0x00ffffffL).toDouble() / 0x01000000L.toDouble()
            return ((unit - 0.5) * 0.04).toFloat()
        }

        for (i in out.indices) out[i] = noise()

        var cursor = start
        for (symbol in symbols) {
            val frequency = FIXTURE_AUDIO_HZ + (symbol.toInt() - 1.5) * toneSpacing
            val step = 2.0 * PI * frequency / WsprRxFrontEnd.INPUT_RATE.toDouble()
            repeat(samplesPerSymbol) {
                if (cursor >= out.size) error("WSPR fixture overran 120-second slot")
                out[cursor] += (0.8 * cos(phase)).toFloat()
                cursor++
                phase += step
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
            }
        }

        checkThat(cursor - start == 1_327_104, "WSPR fixture must occupy exactly 110.592 seconds")
        return out
    }

    private const val FIXTURE_MESSAGE = "K1JT FN20QI 20"
    private const val FIXTURE_AUDIO_HZ = 1550.0
}
