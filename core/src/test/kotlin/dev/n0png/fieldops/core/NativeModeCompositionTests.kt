package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.digital.DigitalSlotClock
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.Streaming12kDspEngine
import dev.n0png.fieldops.core.dsp.WeakSignalCoordinator
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import dev.n0png.fieldops.core.radio.CatTransport
import dev.n0png.fieldops.core.radio.DelayProvider
import dev.n0png.fieldops.core.radio.Ftx1RadioSession
import dev.n0png.fieldops.core.radio.RadioModeArbiter
import dev.n0png.fieldops.core.radio.TxAudioPort

object NativeModeCompositionTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private class FakeWindowed : WindowedDspEngine {
        override val modes = setOf(DigitalMode.FT4)
        var decodeCalls = 0
        var lastSamples = 0
        var lastSlot = -1L

        override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult> {
            decodeCalls++
            lastSamples = samples12k.size
            lastSlot = slotStartUtcMillis
            return listOf(DecodeResult(mode, slotStartUtcMillis, text = "FT4 WINDOW"))
        }

        override fun encode(request: EncodeRequest): TxWaveform =
            error("not used")
    }

    private class FakeStreaming : Streaming12kDspEngine {
        override val mode = DigitalMode.JS8
        var startCount = 0
        var stopCount = 0
        var acceptedSamples = 0
        private var onDecode: ((DecodeResult) -> Unit)? = null

        override fun start(onDecode: (DecodeResult) -> Unit) {
            startCount++
            this.onDecode = onDecode
        }

        override fun stop() {
            stopCount++
            onDecode = null
        }

        override fun accept12k(block: TimedPcmBlock) {
            acceptedSamples += block.samples.size
            onDecode?.invoke(DecodeResult(DigitalMode.JS8, block.utcStartNanos / 1_000_000L, text = "JS8 STREAM"))
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        testSharedReceiveComposition()
        testSlotMetadata()
        testArbiterAcrossAllModeOwners()
        testCommonRadioSessionAcrossAllModeOwners()
        println("Native-mode composition regression: PASS assertions=" + assertions)
    }

    private fun testSharedReceiveComposition() {
        val audio = SharedAudioPipeline()
        val windowed = FakeWindowed()
        val streaming = FakeStreaming()
        val decodes = mutableListOf<DecodeResult>()
        var raw48 = 0

        audio.add48kConsumer { raw48 += it.samples.size }
        val coordinator = WeakSignalCoordinator(
            audio = audio,
            windowedEngines = mapOf(DigitalMode.FT4 to windowed),
            streaming12kEngines = mapOf(DigitalMode.JS8 to streaming),
            onDecode = { decodes += it },
        )

        coordinator.selectMode(DigitalMode.JS8)
        audio.accept48k(TimedPcmBlock(100_000_000L, 48_000, FloatArray(4_800) { 0.1f }))
        checkThat(raw48 == 4_800, "raw 48 kHz branch did not receive the shared capture block")
        checkThat(streaming.startCount == 1, "JS8 continuous engine did not start")
        checkThat(streaming.acceptedSamples == 1_200, "48 -> 12 kHz shared branch ratio changed")
        checkThat(decodes.any { it.mode == DigitalMode.JS8 }, "JS8 continuous decode callback was not routed")

        val beforeSwitch = streaming.acceptedSamples
        coordinator.selectMode(DigitalMode.FT4)
        checkThat(streaming.stopCount == 1, "switching to slotted mode must stop JS8 streaming")

        val slotStartMillis = 750_000L
        audio.accept48k(
            TimedPcmBlock(
                utcStartNanos = slotStartMillis * 1_000_000L + 1_000_000L,
                sampleRate = 48_000,
                samples = FloatArray(360_000) { 0.05f },
            )
        )

        checkThat(streaming.acceptedSamples == beforeSwitch, "JS8 must not receive 12 kHz audio after FT4 selection")
        checkThat(windowed.decodeCalls == 1, "FT4 must decode exactly one complete 7.5-second window")
        checkThat(windowed.lastSamples == 90_000, "FT4 complete window must contain 90,000 samples at 12 kHz")
        checkThat(windowed.lastSlot == slotStartMillis, "FT4 slot UTC changed after shared FIR group-delay correction")
        checkThat(decodes.any { it.mode == DigitalMode.FT4 }, "FT4 windowed decode callback was not routed")

        coordinator.close()
    }

    private fun testSlotMetadata() {
        val ft8 = DigitalModes.profile(DigitalMode.FT8)
        val ft4 = DigitalModes.profile(DigitalMode.FT4)
        val ft2 = DigitalModes.profile(DigitalMode.FT2)
        val wspr = DigitalModes.profile(DigitalMode.WSPR)

        checkThat(ft8.cycleMillis == 15_000L, "FT8 cycle changed")
        checkThat(ft4.cycleMillis == 7_500L, "FT4 cycle changed")
        checkThat(ft2.cycleMillis == 3_750L, "FT2 cycle changed")
        checkThat(wspr.cycleMillis == 120_000L && wspr.slotPhaseMillis == 1_000L, "WSPR timing profile changed")
        checkThat(DigitalSlotClock.slotStartMillis(119_999L, wspr) == 1_000L, "WSPR even-minute phase logic changed")
        checkThat(DigitalSlotClock.nextSlotMillis(119_999L, wspr) == 121_000L, "WSPR next-slot logic changed")
    }

    private fun testArbiterAcrossAllModeOwners() {
        val owners = listOf(
            RadioModeArbiter.Owner.FT8,
            RadioModeArbiter.Owner.FT4,
            RadioModeArbiter.Owner.FT2,
            RadioModeArbiter.Owner.JS8,
            RadioModeArbiter.Owner.WSPR,
            RadioModeArbiter.Owner.APRS,
        )

        for (active in owners) {
            val arbiter = RadioModeArbiter()
            checkThat(arbiter.request(active) is RadioModeArbiter.Result.Granted, "could not grant owner " + active)
            checkThat(arbiter.beginTx(active) is RadioModeArbiter.Result.Granted, "could not begin TX for " + active)
            for (other in owners) {
                if (other == active) continue
                checkThat(arbiter.request(other) is RadioModeArbiter.Result.Denied, other.toString() + " stole TX from " + active)
                checkThat(arbiter.snapshot().owner == active, "denied owner request disturbed " + active)
            }
            checkThat(arbiter.endTx(active) is RadioModeArbiter.Result.Granted, "could not end TX for " + active)
            checkThat(arbiter.release(active) is RadioModeArbiter.Result.Granted, "could not release owner " + active)
            checkThat(arbiter.snapshot() == RadioModeArbiter.State(), "arbiter did not return to idle after " + active)
        }
    }

    private fun testCommonRadioSessionAcrossAllModeOwners() {
        val owners = listOf(
            RadioModeArbiter.Owner.FT8,
            RadioModeArbiter.Owner.FT4,
            RadioModeArbiter.Owner.FT2,
            RadioModeArbiter.Owner.JS8,
            RadioModeArbiter.Owner.WSPR,
            RadioModeArbiter.Owner.APRS,
        )

        for (owner in owners) {
            val commands = mutableListOf<String>()
            var openedRate: Int? = null
            var closed = false
            var written = 0
            val arbiter = RadioModeArbiter()
            val cat = object : CatTransport {
                override fun write(command: String) { commands += command }
                override fun query(command: String): String? = null
            }
            val audio = object : TxAudioPort {
                override fun open(sampleRate: Int) {
                    openedRate = sampleRate
                    closed = false
                }
                override fun write(samples: FloatArray) {
                    written += samples.size
                }
                override fun close() {
                    closed = true
                    openedRate = null
                }
            }
            val session = Ftx1RadioSession(arbiter, cat, audio, DelayProvider { })
            val rate = if (owner == RadioModeArbiter.Owner.APRS) 48_000 else 12_000
            session.transmit(owner, TxWaveform(rate, FloatArray(64) { 0.1f }, 1500f))

            checkThat(commands == listOf("TX1;", "TX0;"), owner.toString() + " did not use the common CAT PTT sequence")
            checkThat(written == 64, owner.toString() + " did not use the common TX audio port")
            checkThat(closed && openedRate == null, owner.toString() + " did not close common TX audio")
            checkThat(arbiter.snapshot() == RadioModeArbiter.State(), owner.toString() + " did not release common TX ownership")
        }
    }
}
