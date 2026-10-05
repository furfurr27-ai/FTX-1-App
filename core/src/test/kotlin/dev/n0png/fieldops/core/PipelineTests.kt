package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.aprs.*
import dev.n0png.fieldops.core.audio.*
import dev.n0png.fieldops.core.cat.Ftx1Cat
import dev.n0png.fieldops.core.cat.Ftx1SerialCatTransport
import dev.n0png.fieldops.core.cat.SerialBytePort
import dev.n0png.fieldops.core.digital.*
import dev.n0png.fieldops.core.dsp.SlotWindowAssembler
import dev.n0png.fieldops.core.radio.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object PipelineTests {
    var assertions = 0
        private set
    private fun checkThat(v: Boolean, msg: String = "assertion failed") { assertions++; check(v) { msg } }

    fun runAll() {
        testCrcKnownVector()
        testAfskRoundTrip()
        testAfskNoiseRoundTrip()
        testDecimator()
        testSharedFanout()
        testSlotAssembly()
        testRadioFailSafe()
        testSerializedCatTransport()
    }

    private fun testCrcKnownVector() {
        val crc = Ax25Fcs.compute("123456789".toByteArray())
        checkThat(crc == 0x906E, "CRC-16/X25 known vector")
    }

    private fun testAfskRoundTrip() {
        val frame = Ax25UiFrame.aprsText(Ax25Address("N0PNG"), "!5000.00N/00800.00E>FIELDOPS").encode()
        val audio = Bell202Afsk.modulateAx25(frame)
        val got = Bell202Afsk.demodulateAx25(audio)
        checkThat(got.any { it.contentEquals(frame) }, "AFSK clean round trip")
    }

    private fun testAfskNoiseRoundTrip() {
        val rnd = Random(0xF71)
        repeat(40) { n ->
            val info = ":TEST%02d   :PIPELINE %04d".format(n, rnd.nextInt(10_000))
            val frame = Ax25UiFrame.aprsText(Ax25Address("N0PNG", 7), info).encode()
            val audio = Bell202Afsk.modulateAx25(frame, preambleFlags = 45)
            for (i in audio.indices) audio[i] += (rnd.nextDouble(-0.055, 0.055)).toFloat()
            val got = Bell202Afsk.demodulateAx25(audio)
            checkThat(got.any { it.contentEquals(frame) }, "AFSK noisy round trip $n")
        }
    }

    private fun rms(v: FloatArray, skip: Int = 0): Double {
        var s = 0.0; var n = 0
        for (i in skip until v.size) { s += v[i] * v[i]; n++ }
        return sqrt(s / n.coerceAtLeast(1))
    }

    private fun testDecimator() {
        fun tone(hz: Double): FloatArray = FloatArray(48_000) { i -> sin(2 * PI * hz * i / 48_000.0).toFloat() }
        val d1 = FirDecimator4(); val pass = d1.process(tone(1_000.0)).samples
        val d2 = FirDecimator4(); val stop = d2.process(tone(10_000.0)).samples
        val passR = rms(pass, 200)
        val stopR = rms(stop, 200)
        checkThat(passR > 0.55, "1 kHz passband unexpectedly low: $passR")
        checkThat(stopR < passR / 20.0, "10 kHz alias rejection insufficient: pass=$passR stop=$stopR")
    }

    private fun testSharedFanout() {
        val p = SharedAudioPipeline()
        var raw = 0; var weak = 0
        p.add48kConsumer { raw += it.samples.size }
        p.add12kConsumer { weak += it.samples.size }
        p.accept48k(TimedPcmBlock(1_000_000_000L, 48_000, FloatArray(4_800)))
        checkThat(raw == 4_800)
        checkThat(weak == 1_200)
    }

    private fun testSlotAssembly() {
        val profile = DigitalModes.profile(DigitalMode.FT4)
        val windows = mutableListOf<SlotWindowAssembler.SlotWindow>()
        val a = SlotWindowAssembler(profile, 12_000) { windows += it }
        val startMs = 7_500L * 100
        val total = 90_000
        var p = 0
        var seq = 0L
        while (p < total) {
            val n = minOf(1_777, total - p)
            val t = startMs * 1_000_000L + p.toLong() * 1_000_000_000L / 12_000L
            a.accept(TimedPcmBlock(t, 12_000, FloatArray(n) { 0.1f }, seq++))
            p += n
        }
        checkThat(windows.size == 1, "expected one FT4 window, got ${windows.size}")
        checkThat(windows[0].complete)
        checkThat(windows[0].samples.size == 90_000)
    }

    private fun testRadioFailSafe() {
        val commands = mutableListOf<String>()
        val cat = object : CatTransport {
            override fun write(command: String) { commands += command }
            override fun query(command: String) = null
        }
        var opened = false; var closed = false
        val audio = object : TxAudioPort {
            override fun open(sampleRate: Int) { opened = true }
            override fun write(samples: FloatArray) { throw IllegalStateException("injected audio failure") }
            override fun close() { closed = true }
        }
        val arb = RadioModeArbiter()
        val session = Ftx1RadioSession(arb, cat, audio, DelayProvider { })
        runCatching { session.transmit(RadioModeArbiter.Owner.FT8, TxWaveform(12_000, FloatArray(100), 1_500f)) }
        checkThat(opened && closed)
        checkThat(commands.first() == Ftx1Cat.pttOn())
        checkThat(commands.last() == Ftx1Cat.pttOff(), "TX0 must be attempted in finally")
        checkThat(arb.snapshot().tx == RadioModeArbiter.Tx.RX)
    }
    private fun testSerializedCatTransport() {
        val writes = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>().apply {
            add("FA014250000;MD0C;".toByteArray())
        }
        val serial = object : SerialBytePort {
            override fun write(bytes: ByteArray) { writes += bytes.toString(Charsets.US_ASCII) }
            override fun read(buffer: ByteArray, timeoutMillis: Int): Int {
                val next = replies.removeFirstOrNull() ?: return 0
                next.copyInto(buffer, endIndex = minOf(next.size, buffer.size))
                return minOf(next.size, buffer.size)
            }
        }
        val cat = Ftx1SerialCatTransport(serial, queryTimeoutMillis = 20)
        val fa = cat.query("FA")
        val md = cat.query("MD0;")
        cat.write("TX0")
        checkThat(fa == "FA014250000;", "CAT must stop at first semicolon: $fa")
        checkThat(md == "MD0C;", "CAT mode reply mismatch: $md")
        checkThat(writes == listOf("FA;", "MD0;", "TX0;"), "CAT command normalization mismatch: $writes")
    }

}
