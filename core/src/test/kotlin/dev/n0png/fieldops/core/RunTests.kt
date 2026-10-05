package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.aprs.*
import dev.n0png.fieldops.core.cat.Ftx1Cat
import dev.n0png.fieldops.core.ft8.AutoSignalFollow
import dev.n0png.fieldops.core.digital.*
import dev.n0png.fieldops.core.radio.RadioModeArbiter
import kotlin.random.Random

private var assertions = 0
private fun checkThat(value: Boolean, lazy: () -> String = { "assertion failed" }) {
    assertions++
    if (!value) error(lazy())
}
private fun <T> eq(expected: T, actual: T) = checkThat(expected == actual) { "expected=$expected actual=$actual" }

fun main() {
    // FTX-1 CAT golden vectors from the Yaesu CAT format.
    eq("FA014250000;", Ftx1Cat.setMainFrequencyHz(14_250_000))
    eq("FA014250000;", Ftx1Cat.splitReplies("FA014250000;MD0C;")[0])
    eq(14_250_000L, Ftx1Cat.parseMainFrequency("FA014250000;"))
    eq("MD0C;", Ftx1Cat.setMode(Ftx1Cat.Vfo.MAIN, Ftx1Cat.Mode.DATA_U))
    eq("MD0;", Ftx1Cat.readMode(Ftx1Cat.Vfo.MAIN))
    eq("TX1;", Ftx1Cat.pttOn())
    eq("TX0;", Ftx1Cat.pttOff())
    eq("EX0601012;", Ftx1Cat.setAprsModem(Ftx1Cat.AprsModemSelect.MAIN))
    eq("EX0601020;", Ftx1Cat.setAprsModemType(Ftx1Cat.AprsModemType.AFSK_1200))
    eq("EX060105N0PNG-7;", Ftx1Cat.setAprsCallsign("n0png-7"))
    eq("EX0604012;", Ftx1Cat.setAprsDigiPath(Ftx1Cat.AprsDigiPath.WIDE1_1_WIDE2_1))
    eq("EX0701012;", Ftx1Cat.setAprsBeaconType(Ftx1Cat.AprsBeaconType.SMART))
    eq("EX0901133;", Ftx1Cat.setPreset1TxBpf(3))

    // KISS known escaping and randomized round trips.
    val payload = byteArrayOf(0x01, 0xC0.toByte(), 0x02, 0xDB.toByte(), 0x03)
    val kiss = KissCodec.encodeData(payload)
    checkThat(kiss.contentEquals(byteArrayOf(
        0xC0.toByte(), 0x00, 0x01, 0xDB.toByte(), 0xDC.toByte(), 0x02,
        0xDB.toByte(), 0xDD.toByte(), 0x03, 0xC0.toByte()
    ))) { "KISS escaping mismatch" }
    checkThat(KissCodec.decodeDataFrames(kiss).single().contentEquals(payload))

    val random = Random(0x4E30504E47) // deterministic N0PNG-ish seed
    repeat(10_000) {
        val n = random.nextInt(0, 400)
        val raw = ByteArray(n) { random.nextInt(0, 256).toByte() }
        val decoded = KissCodec.decodeDataFrames(KissCodec.encodeData(raw))
        checkThat(decoded.size == 1 && decoded[0].contentEquals(raw)) { "KISS fuzz failure at iteration $it" }
    }

    // AX.25 address and frame termination bit.
    val frame = Ax25UiFrame.aprsText(
        source = Ax25Address("N0PNG", 7),
        information = AprsPosition.encode(40.0, -105.0, comment = "TEST")
    ).encode()
    checkThat(frame.size > 30)
    // Destination extension bit clear, source clear, final path address set.
    checkThat((frame[6].toInt() and 1) == 0)
    checkThat((frame[13].toInt() and 1) == 0)
    checkThat((frame[27].toInt() and 1) == 1)
    eq(0x03, frame[28].toInt() and 0xFF)
    eq(0xF0, frame[29].toInt() and 0xFF)
    eq("!4000.00N/10500.00W>TEST", AprsPosition.encode(40.0, -105.0, comment = "TEST"))

    // SmartBeaconing interpolation and corner logic.
    val sb = SmartBeaconing()
    eq(1200, sb.speedRateSeconds(0.0))
    eq(60, sb.speedRateSeconds(100.0 / 3.6))
    checkThat(sb.bearingDelta(350.0, 10.0) == 20.0)
    val first = SmartBeaconing.Fix(40.0, -105.0, 0, 0.0, 0.0)
    checkThat(sb.shouldBeacon(null, first))
    val soon = SmartBeaconing.Fix(40.00001, -105.0, 5_000, 0.1, 0.0)
    checkThat(!sb.shouldBeacon(first, soon))
    val later = soon.copy(timeMillis = 1_201_000)
    checkThat(sb.shouldBeacon(first, later))

    // Multi-mode metadata and UTC slot scheduler.
    eq(15_000L, DigitalModes.profile(DigitalMode.FT8).cycleMillis)
    eq(7_500L, DigitalModes.profile(DigitalMode.FT4).cycleMillis)
    eq(3_750L, DigitalModes.profile(DigitalMode.FT2).cycleMillis)
    eq(30_000L, DigitalModes.profile(DigitalMode.JS8, Js8Speed.SLOW).cycleMillis)
    eq(15_000L, DigitalModes.profile(DigitalMode.JS8, Js8Speed.NORMAL).cycleMillis)
    eq(10_000L, DigitalModes.profile(DigitalMode.JS8, Js8Speed.FAST).cycleMillis)
    eq(6_000L, DigitalModes.profile(DigitalMode.JS8, Js8Speed.TURBO).cycleMillis)
    eq(120_000L, DigitalModes.profile(DigitalMode.WSPR).cycleMillis)
    eq(1_000L, DigitalModes.profile(DigitalMode.WSPR).slotPhaseMillis)
    eq(15_000L, DigitalModes.profile(DigitalMode.LQ8).cycleMillis)
    eq(7_500L, DigitalModes.profile(DigitalMode.LQ4).cycleMillis)
    eq(false, DigitalModes.profile(DigitalMode.LQ8).productionTarget)

    val ft4 = DigitalModes.profile(DigitalMode.FT4)
    eq(15_000L, DigitalSlotClock.slotStartMillis(15_999L, ft4))
    eq(22_500L, DigitalSlotClock.nextSlotMillis(15_999L, ft4))
    val wspr = DigitalModes.profile(DigitalMode.WSPR)
    eq(1_000L, DigitalSlotClock.slotStartMillis(119_999L, wspr))
    eq(121_000L, DigitalSlotClock.nextSlotMillis(119_999L, wspr))
    checkThat(DigitalSlotClock.progress(8_750L, ft4) in 0.0..<1.0)

    // Property checks: slot start <= t < next slot across all production profiles.
    val schedRandom = Random(0x46545831)
    val profiles = listOf(
        DigitalModes.profile(DigitalMode.FT8),
        DigitalModes.profile(DigitalMode.FT4),
        DigitalModes.profile(DigitalMode.FT2),
        DigitalModes.profile(DigitalMode.JS8, Js8Speed.SLOW),
        DigitalModes.profile(DigitalMode.JS8, Js8Speed.NORMAL),
        DigitalModes.profile(DigitalMode.JS8, Js8Speed.FAST),
        DigitalModes.profile(DigitalMode.JS8, Js8Speed.TURBO),
        DigitalModes.profile(DigitalMode.WSPR)
    )
    repeat(2_000) {
        val t = schedRandom.nextLong(0L, 4_000_000_000L)
        for (profile in profiles) {
            val start = DigitalSlotClock.slotStartMillis(t, profile)
            val next = DigitalSlotClock.nextSlotMillis(t, profile)
            checkThat(start <= t && t < next) { "slot invariant failed mode=${profile.mode} t=$t start=$start next=$next" }
            eq(profile.cycleMillis, next - start)
        }
    }

    // Auto signal follow safety rules.
    val follow = AutoSignalFollow(300, 2700)
    eq(true, follow.follow(AutoSignalFollow.State(1500, false, false), 900).moved)
    eq(false, follow.follow(AutoSignalFollow.State(1500, true, false), 900).moved)
    eq(false, follow.follow(AutoSignalFollow.State(1500, false, true), 900).moved)
    eq(false, follow.follow(AutoSignalFollow.State(1500, false, false), 2900).moved)

    // Radio ownership: no mode can steal the radio while another mode is transmitting.
    val arb = RadioModeArbiter()
    checkThat(arb.request(RadioModeArbiter.Owner.FT8) is RadioModeArbiter.Result.Granted)
    checkThat(arb.beginTx(RadioModeArbiter.Owner.FT8) is RadioModeArbiter.Result.Granted)
    checkThat(arb.request(RadioModeArbiter.Owner.FT4) is RadioModeArbiter.Result.Denied)
    checkThat(arb.request(RadioModeArbiter.Owner.JS8) is RadioModeArbiter.Result.Denied)
    checkThat(arb.request(RadioModeArbiter.Owner.WSPR) is RadioModeArbiter.Result.Denied)
    checkThat(arb.request(RadioModeArbiter.Owner.APRS) is RadioModeArbiter.Result.Denied)
    checkThat(arb.endTx(RadioModeArbiter.Owner.FT8) is RadioModeArbiter.Result.Granted)
    checkThat(arb.request(RadioModeArbiter.Owner.FT4) is RadioModeArbiter.Result.Granted)
    checkThat(arb.beginTx(RadioModeArbiter.Owner.FT4) is RadioModeArbiter.Result.Granted)
    checkThat(arb.endTx(RadioModeArbiter.Owner.FT4) is RadioModeArbiter.Result.Granted)
    checkThat(arb.request(RadioModeArbiter.Owner.JS8) is RadioModeArbiter.Result.Granted)
    checkThat(arb.request(RadioModeArbiter.Owner.WSPR) is RadioModeArbiter.Result.Granted)
    checkThat(arb.request(RadioModeArbiter.Owner.APRS) is RadioModeArbiter.Result.Granted)

    PipelineTests.runAll()
    LotwTests.runAll()
    println("PASS: $assertions assertions, including 10,000 deterministic KISS fuzz cases")
    println("Pipeline assertions: ${PipelineTests.assertions}")
    println("LoTW assertions: ${LotwTests.assertions}")
}
