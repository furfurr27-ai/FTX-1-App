package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.radio.CatTransport
import dev.n0png.fieldops.core.radio.DelayProvider
import dev.n0png.fieldops.core.radio.Ftx1RadioSession
import dev.n0png.fieldops.core.radio.RadioModeArbiter
import dev.n0png.fieldops.core.radio.TxAudioPort
import kotlin.math.abs

object WsprTxTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bridge = WsprJniBridge()
        val engine = WsprEngineAdapter(bridge)
        testWaveformInvariants(engine, bridge)
        testGuardedSuccessfulTx(engine)
        testCompetingOwnerCannotBeStolen(engine)
        testCancelCollapsesToRx(engine)
        testAudioFailureCollapsesToRx(engine)
        testEncodeFailureNeverKeys(engine)
        println("WSPR TX tests: PASS assertions=" + assertions)
    }

    private data class Fixture(
        val arbiter: RadioModeArbiter,
        val commands: MutableList<String>,
        val audio: FakeAudio,
        val controller: WsprTxController,
    )

    private class FakeAudio(
        private val failOnWrite: Int? = null,
    ) : TxAudioPort {
        var openedRate: Int? = null
        var closed = false
        var writeCalls = 0
        var writtenSamples = 0

        override fun open(sampleRate: Int) {
            check(openedRate == null)
            openedRate = sampleRate
            closed = false
        }

        override fun write(samples: FloatArray) {
            writeCalls++
            if (failOnWrite == writeCalls) error("injected-wspr-audio-failure")
            check(openedRate != null && !closed)
            writtenSamples += samples.size
        }

        override fun close() {
            closed = true
            openedRate = null
        }
    }

    private fun fixture(
        engine: WsprEngineAdapter,
        failOnWrite: Int? = null,
        chunkSamples: Int = 32_768,
    ): Fixture {
        val arbiter = RadioModeArbiter()
        val commands = mutableListOf<String>()
        val cat = object : CatTransport {
            override fun write(command: String) {
                commands += command
            }
            override fun query(command: String): String? = null
        }
        val audio = FakeAudio(failOnWrite)
        val radio = Ftx1RadioSession(
            arbiter = arbiter,
            cat = cat,
            txAudio = audio,
            delay = DelayProvider { },
        )
        val controller = WsprTxController(
            wspr = engine,
            radio = radio,
            policy = Ftx1RadioSession.TxPolicy(pttLeadMillis = 100, tailMillis = 40),
            chunkSamples = chunkSamples,
        )
        return Fixture(arbiter, commands, audio, controller)
    }

    private fun request(
        text: String = FIXTURE_MESSAGE,
        audioHz: Float = FIXTURE_AUDIO_HZ,
        sampleRate: Int = 12_000,
    ) = EncodeRequest(
        mode = DigitalMode.WSPR,
        text = text,
        audioHz = audioHz,
        sampleRate = sampleRate,
    )

    private fun testWaveformInvariants(engine: WsprEngineAdapter, bridge: WsprJniBridge) {
        val symbols = bridge.encodeSymbols(FIXTURE_MESSAGE)
        checkThat(symbols.size == 162, "pinned encoder must return 162 channel symbols")
        checkThat(symbols.all { it.toInt() in 0..3 }, "pinned WSPR symbols must remain in 0..3")

        val waveform = engine.encode(request())
        checkThat(waveform.sampleRate == 12_000, "WSPR production waveform must be 12 kHz")
        checkThat(waveform.samples.size == 1_327_104, "WSPR production waveform must contain exactly 1,327,104 samples")
        checkThat(abs(WsprTxWaveformSynthesizer.DURATION_SECONDS - 110.592) < 1e-9, "WSPR duration must be exactly 110.592 seconds")
        checkThat(WsprTxWaveformSynthesizer.SAMPLES_PER_SYMBOL == 8_192, "WSPR must use 8,192 samples per symbol at 12 kHz")
        checkThat(abs(WsprTxWaveformSynthesizer.TONE_SPACING_HZ - 1.46484375) < 1e-12, "WSPR tone spacing mismatch")
        checkThat(abs(WsprTxWaveformSynthesizer.toneHz(1500.0, 1) - WsprTxWaveformSynthesizer.toneHz(1500.0, 0) - 1.46484375) < 1e-12, "adjacent WSPR tones must be exactly one spacing apart")
        checkThat(waveform.samples.maxOf { abs(it) } <= 0.80001f, "WSPR waveform must remain normalized")
        checkThat(waveform.samples.any { abs(it) > 0.79f }, "WSPR waveform should reach its configured 0.8 amplitude")

        val slot = FloatArray(WsprRxFrontEnd.INPUT_SAMPLES)
        val start = 2 * WsprRxFrontEnd.INPUT_RATE
        waveform.samples.copyInto(slot, destinationOffset = start)
        checkThat(start + waveform.samples.size < slot.size, "production waveform must fit inside the 120-second decode fixture")

        val iq = WsprRxFrontEnd().convert(slot)
        val decoded = bridge.decode375(iq.i, iq.q)
        val hit = decoded.firstOrNull { it.call.trim() == "K1JT" }
            ?: error("production WSPR waveform did not decode through pinned native RX: " + decoded)
        checkThat(hit.grid.trim() == "FN20", "production TX/RX round-trip grid mismatch")
        checkThat(hit.powerDbm.trim() == "20", "production TX/RX round-trip power mismatch")
        checkThat(abs(hit.audioHz - FIXTURE_AUDIO_HZ) < 2.0f, "production TX/RX round-trip frequency mismatch")
    }

    private fun testGuardedSuccessfulTx(engine: WsprEngineAdapter) {
        val f = fixture(engine)
        checkThat(f.controller.start(request()), "WSPR TX should acquire the guarded radio path")
        checkThat(f.commands == listOf("TX1;"), "WSPR start must key only through FieldOps CAT")
        checkThat(f.audio.openedRate == 12_000, "WSPR TX audio must open at the waveform's 12 kHz domain")
        checkThat(f.audio.writtenSamples == 0, "PTT lead must complete before WSPR audio is written")
        checkThat(f.arbiter.snapshot().owner == RadioModeArbiter.Owner.WSPR, "WSPR must own the radio while keyed")
        checkThat(f.arbiter.snapshot().tx == RadioModeArbiter.Tx.TRANSMITTING, "arbiter must show WSPR TX")

        while (f.controller.snapshot().phase != WsprTxController.Phase.IDLE) {
            val s = f.controller.pump(8)
            checkThat(s.phase != WsprTxController.Phase.FAILED, "normal WSPR TX must not fail")
        }

        checkThat(f.audio.writtenSamples == 1_327_104, "guarded path must carry the complete WSPR waveform")
        checkThat(f.commands.last() == "TX0;", "normal WSPR completion must send TX0")
        checkThat(f.audio.closed, "normal WSPR completion must close TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "normal WSPR completion must release radio ownership")
    }

    private fun testCompetingOwnerCannotBeStolen(engine: WsprEngineAdapter) {
        val f = fixture(engine)
        checkThat(f.arbiter.request(RadioModeArbiter.Owner.JS8) is RadioModeArbiter.Result.Granted, "fixture must grant JS8 owner")
        checkThat(f.arbiter.beginTx(RadioModeArbiter.Owner.JS8) is RadioModeArbiter.Result.Granted, "fixture must place JS8 in TX")

        val accepted = f.controller.start(request())
        checkThat(!accepted, "WSPR must not start while another mode is transmitting")
        checkThat(f.controller.snapshot().phase == WsprTxController.Phase.FAILED, "competing-owner refusal should be explicit")
        checkThat(f.commands.isEmpty(), "WSPR must not issue CAT commands when another owner holds TX")
        checkThat(f.audio.openedRate == null, "WSPR must not open TX audio when ownership is denied")
        checkThat(f.arbiter.snapshot().owner == RadioModeArbiter.Owner.JS8, "WSPR must not steal the competing owner")
        checkThat(f.arbiter.snapshot().tx == RadioModeArbiter.Tx.TRANSMITTING, "WSPR denial must not disturb the competing TX")
    }

    private fun testCancelCollapsesToRx(engine: WsprEngineAdapter) {
        val f = fixture(engine, chunkSamples = 16_384)
        checkThat(f.controller.start(request()), "WSPR cancel fixture should start")
        f.controller.pump(1)
        checkThat(f.audio.writtenSamples in 1 until 1_327_104, "cancel fixture must have only partial audio written")
        f.controller.cancel()

        checkThat(f.commands.last() == "TX0;", "WSPR cancel must force TX0")
        checkThat(f.audio.closed, "WSPR cancel must close TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "WSPR cancel must release ownership")
        checkThat(f.controller.snapshot().phase == WsprTxController.Phase.IDLE, "WSPR cancel must return controller to IDLE")
    }

    private fun testAudioFailureCollapsesToRx(engine: WsprEngineAdapter) {
        val f = fixture(engine, failOnWrite = 2, chunkSamples = 8_192)
        checkThat(f.controller.start(request()), "WSPR failure fixture should start")
        val state = f.controller.pump(4)

        checkThat(state.phase == WsprTxController.Phase.FAILED, "audio write failure must mark WSPR TX failed")
        checkThat(state.lastFailure?.contains("audio-write-failed") == true, "WSPR failure reason should identify audio write")
        checkThat(f.commands.last() == "TX0;", "WSPR audio failure must force TX0")
        checkThat(f.audio.closed, "WSPR audio failure must close TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "WSPR audio failure must release ownership")
        checkThat(f.audio.writtenSamples < 1_327_104, "failed WSPR TX must not report a complete waveform")
    }

    private fun testEncodeFailureNeverKeys(engine: WsprEngineAdapter) {
        val f = fixture(engine)
        val accepted = f.controller.start(request(text = "K1JT", sampleRate = 12_000))
        checkThat(!accepted, "invalid WSPR message must fail before TX")
        checkThat(f.controller.snapshot().phase == WsprTxController.Phase.FAILED, "encode failure should be explicit")
        checkThat(f.commands.isEmpty(), "encode failure must never assert CAT PTT")
        checkThat(f.audio.openedRate == null, "encode failure must never open TX audio")
        checkThat(f.arbiter.snapshot() == RadioModeArbiter.State(), "encode failure must not acquire radio ownership")
    }

    private const val FIXTURE_MESSAGE = "K1JT FN20QI 20"
    private const val FIXTURE_AUDIO_HZ = 1550f
}
