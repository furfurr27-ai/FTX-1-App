package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.aprs.*
import dev.n0png.fieldops.core.radio.RadioModeArbiter
import kotlin.random.Random

object AprsRegressionTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        testKiss()
        testAx25AndPosition()
        testBell202()
        testStreamingEngineAcrossCallbacks()
        testSmartBeaconing()
        testAprsCannotStealTxOwner()
        println("APRS regression: PASS assertions=" + assertions)
    }

    private fun testKiss() {
        val known = byteArrayOf(0x01, 0xC0.toByte(), 0x02, 0xDB.toByte(), 0x03)
        val roundTrip = KissCodec.decodeDataFrames(KissCodec.encodeData(known))
        checkThat(roundTrip.size == 1 && roundTrip.single().contentEquals(known), "KISS reserved-byte round trip failed")

        val random = Random(0x41505253)
        repeat(2_000) { i ->
            val raw = ByteArray(random.nextInt(0, 320)) { random.nextInt(0, 256).toByte() }
            val decoded = KissCodec.decodeDataFrames(KissCodec.encodeData(raw))
            checkThat(decoded.size == 1 && decoded.single().contentEquals(raw), "KISS deterministic fuzz failed at " + i)
        }
    }

    private fun testAx25AndPosition() {
        val position = AprsPosition.encode(50.0, 8.0, comment = "FIELDOPS")
        checkThat(position == "!5000.00N/00800.00E>FIELDOPS", "APRS position encoding changed")

        val frame = Ax25UiFrame.aprsText(
            source = Ax25Address("N0PNG", 7),
            information = position,
        ).encode()

        checkThat(frame.size > 30, "AX.25 APRS UI frame unexpectedly short")
        checkThat((frame[6].toInt() and 1) == 0, "destination must not terminate address list")
        checkThat((frame[13].toInt() and 1) == 0, "source must not terminate default path")
        checkThat((frame[27].toInt() and 1) == 1, "last default digipeater must terminate address list")
        checkThat((frame[28].toInt() and 0xff) == 0x03, "AX.25 UI control byte changed")
        checkThat((frame[29].toInt() and 0xff) == 0xf0, "AX.25 no-layer-3 PID changed")
        checkThat(Ax25Fcs.compute("123456789".toByteArray()) == 0x906E, "CRC-16/X25 known vector changed")
    }

    private fun testBell202() {
        val frame = Ax25UiFrame.aprsText(
            source = Ax25Address("N0PNG"),
            information = "!5000.00N/00800.00E>FIELDOPS",
        ).encode()

        val clean = Bell202Afsk.modulateAx25(frame, preambleFlags = 45)
        checkThat(clean.isNotEmpty(), "Bell-202 modulator produced no samples")
        checkThat(Bell202Afsk.demodulateAx25(clean).any { it.contentEquals(frame) }, "clean Bell-202 round trip failed")

        val random = Random(0xB202)
        repeat(24) { n ->
            val audio = Bell202Afsk.modulateAx25(frame, preambleFlags = 45)
            for (i in audio.indices) {
                audio[i] += random.nextDouble(-0.04, 0.04).toFloat()
            }
            checkThat(Bell202Afsk.demodulateAx25(audio).any { it.contentEquals(frame) }, "noisy Bell-202 round trip failed at " + n)
        }
    }

    private fun testStreamingEngineAcrossCallbacks() {
        val frame = Ax25UiFrame.aprsText(
            source = Ax25Address("N0PNG", 7),
            information = ":TEST     :CP0002E",
        ).encode()
        val audio = Bell202Afsk.modulateAx25(frame, preambleFlags = 45)
        val engine = AprsAfskEngine()
        val decoded = mutableListOf<ByteArray>()

        var p = 0
        var chunk = 0
        while (p < audio.size) {
            val n = minOf(if (chunk++ % 2 == 0) 4_777 else 7_123, audio.size - p)
            engine.accept48k(
                utcStartNanos = 1_800_000_000_000_000_000L + p.toLong() * 1_000_000_000L / 48_000L,
                samples48k = audio.copyOfRange(p, p + n),
            ).mapNotNullTo(decoded) { it.raw }
            p += n
        }

        checkThat(decoded.any { it.contentEquals(frame) }, "streaming APRS engine lost a frame crossing callbacks")
        val duplicate = engine.accept48k(1_800_000_100_000_000_000L, audio)
        checkThat(duplicate.none { it.raw?.contentEquals(frame) == true }, "APRS duplicate suppression changed")
    }

    private fun testSmartBeaconing() {
        val sb = SmartBeaconing()
        checkThat(sb.speedRateSeconds(0.0) == 1200, "slow SmartBeaconing interval changed")
        checkThat(sb.speedRateSeconds(100.0 / 3.6) == 60, "fast SmartBeaconing interval changed")
        checkThat(sb.bearingDelta(350.0, 10.0) == 20.0, "bearing wrap calculation changed")

        val first = SmartBeaconing.Fix(50.0, 8.0, 0L, 0.0, 0.0)
        checkThat(sb.shouldBeacon(null, first), "first SmartBeaconing fix must beacon")
        val soon = SmartBeaconing.Fix(50.00001, 8.0, 5_000L, 0.1, 0.0)
        checkThat(!sb.shouldBeacon(first, soon), "stationary APRS must not beacon too early")
        checkThat(sb.shouldBeacon(first, soon.copy(timeMillis = 1_201_000L)), "slow-rate APRS beacon deadline changed")
    }

    private fun testAprsCannotStealTxOwner() {
        val arbiter = RadioModeArbiter()
        checkThat(arbiter.request(RadioModeArbiter.Owner.WSPR) is RadioModeArbiter.Result.Granted, "fixture WSPR ownership failed")
        checkThat(arbiter.beginTx(RadioModeArbiter.Owner.WSPR) is RadioModeArbiter.Result.Granted, "fixture WSPR TX failed")
        checkThat(arbiter.request(RadioModeArbiter.Owner.APRS) is RadioModeArbiter.Result.Denied, "APRS must not steal a transmitting owner")
        checkThat(arbiter.snapshot().owner == RadioModeArbiter.Owner.WSPR, "APRS denial disturbed current owner")
        checkThat(arbiter.snapshot().tx == RadioModeArbiter.Tx.TRANSMITTING, "APRS denial disturbed active TX")
    }
}
