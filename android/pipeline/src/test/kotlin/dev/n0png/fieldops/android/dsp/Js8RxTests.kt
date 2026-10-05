package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.Streaming12kDspEngine
import dev.n0png.fieldops.core.dsp.WeakSignalCoordinator
import java.time.Instant

object Js8RxTests {
    private var assertions = 0
    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        testCallbackMappingAndPcmSubmission()
        testContinuousCoordinatorPath()
        testUtcRollover()
        println("JS8 RX tests: PASS assertions=$assertions")
    }

    private fun testCallbackMappingAndPcmSubmission() {
        val now = Instant.parse("2026-10-05T00:00:02Z").toEpochMilli()
        lateinit var callback: Js8EngineAdapter.Callbacks
        val submitted = mutableListOf<Pair<ShortArray, Long>>()
        var starts = 0
        var stops = 0
        var closes = 0
        val factory = Js8EngineAdapter.EngineFactory { cb ->
            callback = cb
            object : Js8EngineAdapter.NativeEngine {
                override fun start(): Boolean { starts++; return true }
                override fun stop() { stops++ }
                override fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean {
                    submitted += samples to timestampNs
                    return true
                }
                override fun close() { closes++ }
            }
        }

        val got = mutableListOf<DecodeResult>()
        val adapter = Js8EngineAdapter(factory, nowMillis = { now })
        adapter.start(got::add)
        val t0 = 123_456_789L
        adapter.accept12k(TimedPcmBlock(t0, 12_000, floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f), 7))

        checkThat(starts == 1, "native JS8 engine must start once")
        checkThat(submitted.size == 1, "continuous PCM block must be submitted immediately")
        checkThat(submitted[0].second == t0, "FieldOps capture timestamp must be forwarded")
        checkThat(submitted[0].first.first() == Short.MIN_VALUE, "-1.0 must map to PCM16 min")
        checkThat(submitted[0].first.last() == Short.MAX_VALUE, "+1.0 must map to PCM16 max")

        callback.onDecoded(235959, -12, 0.4f, 1530f, "N0PNG TEST", 4, 0.95f, 0, 15)
        checkThat(got.size == 1, "native callback must emit one FieldOps decode")
        val d = got.single()
        checkThat(d.mode == DigitalMode.JS8, "decode mode must be JS8")
        checkThat(d.text == "N0PNG TEST", "decode text mismatch")
        checkThat(d.snrDb == -12 && d.audioHz == 1530f && d.dtSeconds == 0.4f, "decode metrics mismatch")
        checkThat(
            d.utcMillis == Instant.parse("2026-10-04T23:59:59Z").toEpochMilli(),
            "HHMMSS rollover mapping mismatch",
        )

        adapter.stop()
        adapter.close()
        checkThat(stops == 1, "stop must reach native engine exactly once")
        checkThat(closes == 1, "close must destroy native engine")
    }

    private fun testContinuousCoordinatorPath() {
        val audio = SharedAudioPipeline()
        var starts = 0
        var stops = 0
        val blocks = mutableListOf<TimedPcmBlock>()
        val streaming = object : Streaming12kDspEngine {
            override val mode = DigitalMode.JS8
            override fun start(onDecode: (DecodeResult) -> Unit) { starts++ }
            override fun stop() { stops++ }
            override fun accept12k(block: TimedPcmBlock) { blocks += block }
        }
        val coordinator = WeakSignalCoordinator(
            audio = audio,
            windowedEngines = emptyMap(),
            streaming12kEngines = mapOf(DigitalMode.JS8 to streaming),
            onDecode = {},
        )
        coordinator.selectMode(DigitalMode.JS8)

        val firstStart = 15_000_000_000L
        audio.accept48k(TimedPcmBlock(firstStart, 48_000, FloatArray(4_800) { 0.1f }, 1))
        audio.accept48k(TimedPcmBlock(firstStart + 100_000_000L, 48_000, FloatArray(4_800) { 0.1f }, 2))

        checkThat(starts == 1, "JS8 stream must start on selection")
        checkThat(blocks.size == 2, "JS8 must receive each 12 kHz block without slot assembly")
        checkThat(blocks.all { it.sampleRate == 12_000 }, "JS8 must receive 12 kHz blocks")
        checkThat(blocks.all { it.samples.size == 1_200 }, "4,800 input samples must produce 1,200 JS8 samples")

        coordinator.close()
        checkThat(stops == 1, "coordinator close must stop active JS8 stream")
    }

    private fun testUtcRollover() {
        val now = Instant.parse("2026-10-05T23:59:58Z").toEpochMilli()
        val next = Js8EngineAdapter.nearestUtcMillis(5, now)
        checkThat(next == Instant.parse("2026-10-06T00:00:05Z").toEpochMilli(), "next-day UTC rollover mismatch")
        val invalid = Js8EngineAdapter.nearestUtcMillis(256199, now)
        checkThat(invalid == now, "invalid upstream UTC should fall back to callback time")
    }
}
