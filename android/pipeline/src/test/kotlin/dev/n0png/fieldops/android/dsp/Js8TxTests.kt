package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.StreamingLinearResampler
import dev.n0png.fieldops.core.radio.CatTransport
import dev.n0png.fieldops.core.radio.DelayProvider
import dev.n0png.fieldops.core.radio.Ftx1RadioSession
import dev.n0png.fieldops.core.radio.RadioModeArbiter
import dev.n0png.fieldops.core.radio.TxAudioPort
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object Js8TxTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        testSuccessfulNativeStreamingTx()
        testAudioFailureCollapsesToRx()
        testCancelBeforeKeyingNeverAssertsPtt()
        testUnexpectedNativeAudioFailsClosed()
        testStreamingResamplerChunkInvariance()
        println("JS8 TX tests: PASS assertions=$assertions")
    }

    private data class Fixture(
        val callbacks: Js8EngineAdapter.Callbacks,
        val native: FakeNative,
        val adapter: Js8EngineAdapter,
        val arbiter: RadioModeArbiter,
        val commands: MutableList<String>,
        val audio: FakeAudio,
        val controller: Js8TxController,
    )

    private class FakeNative : Js8EngineAdapter.NativeEngine {
        lateinit var callbacks: Js8EngineAdapter.Callbacks
        var started = false
        var transmitting = false
        var audioActive = false
        var millisecondsUntilAudio = -1
        var stopTransmitCount = 0
        val readyHistory = mutableListOf<Boolean>()
        var accepted = true
        var lastRequest: Js8EngineAdapter.TxRequest? = null

        override fun start(): Boolean {
            started = true
            return true
        }

        override fun stop() {
            started = false
        }

        override fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean = true

        override fun transmitMessage(request: Js8EngineAdapter.TxRequest): Boolean {
            lastRequest = request
            if (accepted) transmitting = true
            return accepted
        }

        override fun stopTransmit() {
            stopTransmitCount++
            transmitting = false
            audioActive = false
        }

        override fun isTransmitting(): Boolean = transmitting
        override fun isTransmittingAudio(): Boolean = audioActive
        override fun txMillisecondsUntilAudio(): Int = millisecondsUntilAudio
        override fun setTransmitReady(ready: Boolean) {
            readyHistory += ready
        }

        override fun close() {
            started = false
        }
    }

    private class FakeAudio : TxAudioPort {
        var openedRate: Int? = null
        var closed = false
        var failWrites = false
        var writtenSamples = 0
        val chunks = mutableListOf<Int>()

        override fun open(sampleRate: Int) {
            check(openedRate == null)
            openedRate = sampleRate
            closed = false
        }

        override fun write(samples: FloatArray) {
            if (failWrites) error("injected-stream-write-failure")
            check(openedRate != null && !closed)
            writtenSamples += samples.size
            chunks += samples.size
        }

        override fun close() {
            closed = true
            openedRate = null
        }
    }

    private fun fixture(failWrites: Boolean = false): Fixture {
        val native = FakeNative()
        lateinit var callbacks: Js8EngineAdapter.Callbacks
        val adapter = Js8EngineAdapter(
            factory = Js8EngineAdapter.EngineFactory { cb ->
                callbacks = cb
                native.callbacks = cb
                native
            }
        )
        adapter.start { }

        val arbiter = RadioModeArbiter()
        val commands = mutableListOf<String>()
        val cat = object : CatTransport {
            override fun write(command: String) {
                commands += command
            }
            override fun query(command: String): String? = null
        }
        val audio = FakeAudio().also { it.failWrites = failWrites }
        val radio = Ftx1RadioSession(
            arbiter = arbiter,
            cat = cat,
            txAudio = audio,
            delay = DelayProvider { },
        )
        val controller = Js8TxController(
            js8 = adapter,
            radio = radio,
            outputSampleRate = 48_000,
            policy = Ftx1RadioSession.TxPolicy(pttLeadMillis = 100, tailMillis = 40),
            prekeyMarginMillis = 25,
        )
        return Fixture(callbacks, native, adapter, arbiter, commands, audio, controller)
    }

    private fun request() = Js8EngineAdapter.TxRequest(
        text = "N0PNG TEST",
        myCall = "N0PNG",
        myGrid = "JO40",
        selectedCall = "",
        submode = 0,
        audioFrequencyHz = 1500.0,
        txDelaySec = 0.0,
        forceIdentify = true,
        forceData = false,
    )

    private fun testSuccessfulNativeStreamingTx() {
        val f = fixture()
        f.native.millisecondsUntilAudio = 500
        checkThat(f.controller.queue(request()), "native TX request should be accepted")
        checkThat(f.commands.isEmpty(), "queueing JS8 must not assert PTT")

        f.controller.poll()
        checkThat(f.commands.isEmpty(), "PTT must remain off outside the pre-key window")

        f.native.millisecondsUntilAudio = 100
        val keyed = f.controller.poll()
        checkThat(keyed.phase == Js8TxController.Phase.KEYED, "controller should pre-key through FieldOps")
        checkThat(f.commands == listOf("TX1;"), "FieldOps must be the only CAT PTT path")
        checkThat(f.audio.openedRate == 48_000, "FTX-1 USB TX output must be opened at 48 kHz")
        checkThat(f.arbiter.snapshot().owner == RadioModeArbiter.Owner.JS8, "JS8 must own radio while keyed")
        checkThat(f.arbiter.snapshot().tx == RadioModeArbiter.Tx.TRANSMITTING, "arbiter must show TX")
        checkThat(f.native.readyHistory.last() == true, "native audio gate opens only after FieldOps PTT lead")

        f.native.audioActive = true
        val samples = ShortArray(1152) { i ->
            (sin(2.0 * PI * 1500.0 * i / 11520.0) * 12000.0).toInt().toShort()
        }
        f.callbacks.onTxAudio(samples, 11_520)
        checkThat(f.controller.snapshot().phase == Js8TxController.Phase.AUDIO, "TX PCM callback should enter AUDIO")
        checkThat(f.audio.writtenSamples > 4_700, "11.52 kHz JS8 PCM must be adapted toward 48 kHz")

        f.native.transmitting = false
        f.native.audioActive = false
        val done = f.controller.poll()
        checkThat(done.phase == Js8TxController.Phase.IDLE, "completed native session should return controller to IDLE")
        checkThat(f.commands.last() == "TX0;", "normal completion must send TX0")
        checkThat(f.audio.closed, "normal completion must close TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "normal completion must release radio ownership")
        checkThat(f.native.readyHistory.last() == false, "native TX gate must close at completion")
        checkThat(f.native.lastRequest?.text == "N0PNG TEST", "native engine request content mismatch")
        f.adapter.close()
    }

    private fun testAudioFailureCollapsesToRx() {
        val f = fixture(failWrites = true)
        f.native.millisecondsUntilAudio = 0
        checkThat(f.controller.queue(request()), "TX request should queue before injected audio failure")
        f.controller.poll()
        f.native.audioActive = true

        f.callbacks.onTxAudio(ShortArray(256) { 5000 }, 11_520)

        val state = f.controller.snapshot()
        checkThat(state.phase == Js8TxController.Phase.FAILED, "audio write failure must mark TX failed")
        checkThat(state.lastFailure?.contains("audio-write-failed") == true, "failure reason should identify audio write")
        checkThat(f.commands.last() == "TX0;", "audio failure must attempt TX0")
        checkThat(f.audio.closed, "audio failure must close TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "audio failure must release ownership")
        checkThat(f.native.stopTransmitCount >= 1, "audio failure must stop native TX")
        checkThat(f.native.readyHistory.last() == false, "audio failure must close native transmit gate")
        f.adapter.close()
    }

    private fun testCancelBeforeKeyingNeverAssertsPtt() {
        val f = fixture()
        f.native.millisecondsUntilAudio = 5_000
        checkThat(f.controller.queue(request()), "TX request should queue")
        f.controller.cancel()

        checkThat("TX1;" !in f.commands, "cancel before pre-key must never assert TX1")
        checkThat(f.commands.last() == "TX0;", "cancel must still force RX-safe TX0")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "cancel must leave arbiter fully released")
        checkThat(f.native.stopTransmitCount >= 1, "cancel must stop native transmission")
        checkThat(f.controller.snapshot().phase == Js8TxController.Phase.IDLE, "cancel should return to IDLE")
        f.adapter.close()
    }

    private fun testUnexpectedNativeAudioFailsClosed() {
        val f = fixture()
        f.native.millisecondsUntilAudio = 500
        checkThat(f.controller.queue(request()), "TX request should queue")
        f.native.audioActive = true
        val state = f.controller.poll()

        checkThat(state.phase == Js8TxController.Phase.FAILED, "audio-before-PTT invariant violation must fail closed")
        checkThat("TX1;" !in f.commands, "invariant failure must not assert PTT")
        checkThat(f.commands.last() == "TX0;", "invariant failure must force TX0")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "invariant failure must release ownership")
        f.adapter.close()
    }

    private fun testStreamingResamplerChunkInvariance() {
        val input = FloatArray(11_520) { i ->
            sin(2.0 * PI * 1500.0 * i / 11_520.0).toFloat()
        }

        val one = StreamingLinearResampler(11_520, 48_000)
        val oneOut = one.process(input) + one.flushHold()

        val split = StreamingLinearResampler(11_520, 48_000)
        val pieces = mutableListOf<Float>()
        var p = 0
        val chunkPattern = intArrayOf(17, 511, 23, 1024, 7, 333)
        var c = 0
        while (p < input.size) {
            val n = minOf(chunkPattern[c++ % chunkPattern.size], input.size - p)
            split.process(input.copyOfRange(p, p + n)).forEach { pieces += it }
            p += n
        }
        split.flushHold().forEach { pieces += it }
        val splitOut = pieces.toFloatArray()

        checkThat(oneOut.size == 48_000, "one second of 11.52 kHz must adapt to exactly 48,000 samples after flush")
        checkThat(splitOut.size == oneOut.size, "resampler output length must not depend on chunking")
        var maxError = 0f
        for (i in oneOut.indices) maxError = maxOf(maxError, abs(oneOut[i] - splitOut[i]))
        checkThat(maxError < 1e-6f, "resampler output must be chunk-boundary invariant; maxError=$maxError")
    }
}
