package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.logbook.*

object LotwTests {
    var assertions = 0
        private set

    private fun checkThat(v: Boolean, m: () -> String = { "LoTW assertion failed" }) {
        assertions++
        if (!v) error(m())
    }
    private fun <T> eq(e: T, a: T) = checkThat(e == a) { "LoTW expected=$e actual=$a" }

    fun runAll() {
        val q = QsoRecord(
            id = 1, call = "W1AW", stationCallsign = "N0PNG", qsoDate = "20261003",
            timeOn = "201530", band = "40m", mode = "FT8", freqMhz = "7.07400",
            myGridSquare = "JN49", myDxcc = 230, myCqZone = 14, myItuZone = 28
        )
        val adif = AdifCodec.export(listOf(q))
        checkThat(adif.contains("<CALL:4>W1AW"))
        checkThat(adif.contains("<STATION_CALLSIGN:5>N0PNG"))
        checkThat(adif.contains("<MY_GRIDSQUARE:4>JN49"))

        val reportText = """
            <PROGRAMID:4>LoTW
            <APP_LoTW_LASTQSORX:19>2026-10-03 20:20:00
            <EOH>
            <STATION_CALLSIGN:5>N0PNG<CALL:4>W1AW<QSO_DATE:8>20261003<TIME_ON:6>201530<BAND:3>40m<MODE:3>FT8<APP_LoTW_RXQSO:19>2026-10-03 20:19:00<EOR>
        """.trimIndent()
        val report = AdifCodec.parse(reportText)
        eq("2026-10-03 20:20:00", report.lastQsoRx)
        eq(1, report.records.size)
        eq("W1AW", report.records.single()["CALL"])

        val qslText = """
            <PROGRAMID:4>LoTW
            <APP_LoTW_LASTQSL:19>2026-10-03 20:21:00
            <EOH>
            <STATION_CALLSIGN:5>N0PNG<CALL:4>W1AW<QSO_DATE:8>20261003<TIME_ON:6>201501<BAND:3>40m<MODE:4>MFSK<QSL_RCVD:1>Y<QSLRDATE:8>20261003<APP_LoTW_RXQSL:19>2026-10-03 20:21:00<EOR>
        """.trimIndent()
        val fake = object : LotwTransport {
            override fun uploadTq8(payload: ByteArray, filename: String) = LotwUploadResponse(true, "queued", 200)
            override fun query(credentials: LotwCredentials, params: Map<String, String>): String =
                if (params["qso_qsl"] == "yes") qslText else reportText
        }
        val mgr = LotwSyncManager(fake)
        val signer = object : LotwSigner {
            override fun signAdif(adif: String, stationProfileId: String): ByteArray = byteArrayOf(0x1f, 0x8b.toByte(), 1, 2)
        }
        eq(true, mgr.upload(listOf(q), signer, "home").accepted)
        val accepted = mgr.fetchAccepted(LotwCredentials("n0png", "secret"), "2026-10-01")
        val confirmed = mgr.fetchConfirmations(LotwCredentials("n0png", "secret"), "2026-10-01")
        val (updated, summary) = mgr.reconcile(listOf(q), accepted, confirmed)
        eq(LotwUploadState.ACCEPTED, updated.single().lotwUpload)
        eq(LotwConfirmationState.CONFIRMED, updated.single().lotwConfirmation)
        eq(1, summary.acceptedNow)
        eq(1, summary.confirmedNow)
        eq("2026-10-03 20:20:00", summary.cursor.lastQsoRx)
        eq("2026-10-03 20:21:00", summary.cursor.lastQsl)

        // Multiple station callsigns must never be signed into one upload batch.
        var rejected = false
        try {
            mgr.upload(listOf(q, q.copy(id = 2, stationCallsign = "N0PNG/P")), signer, "home")
        } catch (_: IllegalArgumentException) { rejected = true }
        checkThat(rejected)

        eq(30_000L, LotwSyncPolicy.VERIFY_AFTER_UPLOAD_MILLIS)
        eq(300_000L, LotwSyncPolicy.retryDelayMillis(0))
        eq(600_000L, LotwSyncPolicy.retryDelayMillis(1))
        eq(LotwSyncPolicy.MAX_RETRY_DELAY_MILLIS, LotwSyncPolicy.retryDelayMillis(8))

        val profile = LotwStationProfile("home", "Home", "N0PNG", "JN49", 230, 14, 28, "Germany")
        eq("N0PNG", profile.stationCallsign)
    }
}
