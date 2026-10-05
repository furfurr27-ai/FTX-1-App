package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.logbook.*

object LotwTransactionTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private class FakeSession(
        override val tq8Payload: ByteArray = byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3),
    ) : LotwSigningSession {
        override var state = LotwSigningSessionState.OPEN
            private set
        var commits = 0
        var rollbacks = 0
        var closes = 0

        override fun commit() {
            check(state == LotwSigningSessionState.OPEN)
            commits++
            state = LotwSigningSessionState.COMMITTED
        }

        override fun rollback() {
            check(state == LotwSigningSessionState.OPEN)
            rollbacks++
            state = LotwSigningSessionState.ROLLED_BACK
        }

        override fun close() {
            closes++
            if (state == LotwSigningSessionState.OPEN) rollback()
        }
    }

    private class FakeSigner(
        var failSigning: Boolean = false,
    ) : TransactionalLotwSigner {
        var beginCalls = 0
        var lastRequest: LotwSigningRequest? = null
        var lastSession: FakeSession? = null

        override fun importPkcs12(
            pkcs12: ByteArray,
            pkcs12Password: CharArray,
            privateKeyPassword: CharArray,
        ) = Unit

        override fun beginSigning(
            request: LotwSigningRequest,
            privateKeyPassword: CharArray,
        ): LotwSigningSession {
            beginCalls++
            if (failSigning) error("synthetic signer failure")
            lastRequest = request
            return FakeSession().also { lastSession = it }
        }
    }

    private class FakeTransport : LotwTransport {
        var uploadCalls = 0
        var uploadThrows = false
        var uploadResponse = LotwUploadResponse(true, "queued", 200)
        val reports = ArrayDeque<Any>()
        val queryParams = mutableListOf<Map<String, String>>()

        override fun uploadTq8(payload: ByteArray, filename: String): LotwUploadResponse {
            uploadCalls++
            if (uploadThrows) error("synthetic network failure")
            check(payload.isNotEmpty())
            return uploadResponse
        }

        override fun query(credentials: LotwCredentials, params: Map<String, String>): String {
            queryParams += params.toMap()
            val next = reports.removeFirstOrNull()
                ?: error("no scripted LoTW report")
            if (next is Throwable) throw next
            return next as String
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        successCommitsOnlyAfterEveryQsoAppears()
        httpSuccessWithoutAcceptanceRollsBack()
        networkFailureRollsBack()
        serverRejectionRollsBack()
        reportFailureRollsBack()
        signingFailureNeverUploads()
        batchValidationFailsBeforeSigning()
        manualAndDigitalUseOneQueue()
        println("LoTW transaction tests: PASS assertions=$assertions")
    }

    private fun successCommitsOnlyAfterEveryQsoAppears() {
        val qsos = sampleBatch()
        val transport = FakeTransport()
        transport.reports += acceptedReport(listOf(qsos[0]))
        transport.reports += acceptedReport(qsos)
        val signer = FakeSigner()
        val delays = mutableListOf<Long>()
        val manager = LotwSyncManager(transport)

        val result = manager.uploadTransactional(
            qsos = qsos,
            signer = signer,
            context = context(acceptedSince = "2026-10-05 13:00:00"),
            privateKeyPassword = "key-secret".toCharArray(),
            acceptancePolicy = LotwAcceptancePolicy(attempts = 2, initialDelayMillis = 7, retryDelayMillis = 11),
            verificationDelay = LotwVerificationDelay { delays += it },
        )

        eq(LotwTransactionOutcome.ACCEPTED, result.outcome, "transaction outcome")
        checkThat(result.committed, "accepted transaction must report committed")
        checkThat(result.qsos.all { it.lotwUpload == LotwUploadState.ACCEPTED }, "all batch QSOs must be accepted atomically")
        eq(1, signer.beginCalls, "signer begin count")
        eq(1, transport.uploadCalls, "TQ8 upload count")
        eq(2, transport.queryParams.size, "acceptance query retry count")
        eq(listOf(7L, 11L), delays, "acceptance delay sequence")
        eq("2026-10-05 13:00:00", transport.queryParams.first()["qso_qsorxsince"], "acceptance cursor")
        eq("no", transport.queryParams.first()["qso_qsl"], "acceptance query type")
        eq(1, signer.lastSession!!.commits, "TrustedQSL commit count")
        eq(0, signer.lastSession!!.rollbacks, "TrustedQSL rollback count")
        checkThat(signer.lastSession!!.closes >= 1, "signing session must be closed")
        eq("Home", signer.lastRequest!!.stationLocationName, "explicit station location")
        eq("N0PNG", signer.lastRequest!!.expectedStationCallsign, "station callsign binding")
        eq(230, signer.lastRequest!!.expectedDxcc, "station DXCC binding")
        checkThat(result.qsos.all { !it.lotwRecordId.isNullOrBlank() }, "accepted records must retain LoTW record ids")
    }

    private fun httpSuccessWithoutAcceptanceRollsBack() {
        val qsos = sampleBatch()
        val transport = FakeTransport()
        transport.reports += acceptedReport(listOf(qsos[0]))
        transport.reports += acceptedReport(listOf(qsos[0]))
        val signer = FakeSigner()

        val result = LotwSyncManager(transport).uploadTransactional(
            qsos,
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(attempts = 2, initialDelayMillis = 0, retryDelayMillis = 0),
            LotwVerificationDelay { },
        )

        eq(LotwTransactionOutcome.VERIFICATION_FAILED, result.outcome, "partial acceptance outcome")
        checkThat(!result.committed, "partial acceptance must not commit")
        checkThat(result.qsos.all { it.lotwUpload == LotwUploadState.SUBMITTED }, "HTTP success without full LoTW acceptance must stay SUBMITTED")
        eq(0, signer.lastSession!!.commits, "partial acceptance commit count")
        eq(1, signer.lastSession!!.rollbacks, "partial acceptance rollback count")
    }

    private fun networkFailureRollsBack() {
        val transport = FakeTransport().apply { uploadThrows = true }
        val signer = FakeSigner()
        val result = LotwSyncManager(transport).uploadTransactional(
            sampleBatch(),
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(attempts = 1, initialDelayMillis = 0, retryDelayMillis = 0),
            LotwVerificationDelay { },
        )

        eq(LotwTransactionOutcome.UPLOAD_FAILED, result.outcome, "network failure outcome")
        checkThat(result.qsos.all { it.lotwUpload == LotwUploadState.QUEUED }, "network failure must leave QSOs queued")
        eq(1, signer.lastSession!!.rollbacks, "network failure rollback count")
        eq(0, signer.lastSession!!.commits, "network failure commit count")
    }

    private fun serverRejectionRollsBack() {
        val transport = FakeTransport().apply {
            uploadResponse = LotwUploadResponse(false, "rejected", 200)
        }
        val signer = FakeSigner()
        val result = LotwSyncManager(transport).uploadTransactional(
            sampleBatch(),
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(attempts = 1, initialDelayMillis = 0, retryDelayMillis = 0),
            LotwVerificationDelay { },
        )

        eq(LotwTransactionOutcome.UPLOAD_REJECTED, result.outcome, "server rejection outcome")
        checkThat(result.qsos.all { it.lotwUpload == LotwUploadState.REJECTED }, "server rejection must mark batch REJECTED")
        eq(1, signer.lastSession!!.rollbacks, "server rejection rollback count")
        eq(0, transport.queryParams.size, "server rejection must not query acceptance")
    }

    private fun reportFailureRollsBack() {
        val transport = FakeTransport()
        transport.reports += IllegalStateException("synthetic report failure")
        transport.reports += IllegalStateException("synthetic report failure")
        val signer = FakeSigner()
        val result = LotwSyncManager(transport).uploadTransactional(
            sampleBatch(),
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(attempts = 2, initialDelayMillis = 0, retryDelayMillis = 0),
            LotwVerificationDelay { },
        )

        eq(LotwTransactionOutcome.VERIFICATION_FAILED, result.outcome, "report failure outcome")
        checkThat(result.qsos.none { it.lotwUpload == LotwUploadState.ACCEPTED }, "report failure must never infer acceptance")
        eq(1, signer.lastSession!!.rollbacks, "report failure rollback count")
    }

    private fun signingFailureNeverUploads() {
        val transport = FakeTransport()
        val signer = FakeSigner(failSigning = true)
        val result = LotwSyncManager(transport).uploadTransactional(
            sampleBatch(),
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(attempts = 1, initialDelayMillis = 0, retryDelayMillis = 0),
            LotwVerificationDelay { },
        )

        eq(LotwTransactionOutcome.SIGNING_FAILED, result.outcome, "signing failure outcome")
        eq(0, transport.uploadCalls, "signing failure must not upload")
        checkThat(result.qsos.all { it.lotwUpload == LotwUploadState.QUEUED }, "signing failure must leave QSOs queued")
    }

    private fun batchValidationFailsBeforeSigning() {
        val signer = FakeSigner()
        val manager = LotwSyncManager(FakeTransport())
        val base = sampleBatch().first()

        var mixedCallRejected = false
        try {
            manager.uploadTransactional(
                listOf(base, base.copy(id = 99, stationCallsign = "N0PNG/P", call = "K1ABC")),
                signer,
                context(),
                "key-secret".toCharArray(),
                LotwAcceptancePolicy(1, 0, 0),
                LotwVerificationDelay { },
            )
        } catch (_: IllegalArgumentException) {
            mixedCallRejected = true
        }
        checkThat(mixedCallRejected, "mixed station callsigns must fail before signing")
        eq(0, signer.beginCalls, "mixed station callsign batch reached signer")

        var ambiguousRejected = false
        try {
            manager.uploadTransactional(
                listOf(base, base.copy(id = 100, mode = "SSB")),
                signer,
                context(),
                "key-secret".toCharArray(),
                LotwAcceptancePolicy(1, 0, 0),
                LotwVerificationDelay { },
            )
        } catch (_: IllegalArgumentException) {
            ambiguousRejected = true
        }
        checkThat(ambiguousRejected, "ambiguous acceptance keys must fail before signing")
        eq(0, signer.beginCalls, "ambiguous batch reached signer")
    }

    private fun manualAndDigitalUseOneQueue() {
        val qsos = sampleBatch()
        val queue = LotwUploadQueue()
        val ssb = queue.enqueue(qsos[0], "home")
        val cw = queue.enqueue(qsos[1], "home")
        val digital = queue.enqueue(qsos[2], "home")

        eq(LotwUploadState.QUEUED, ssb.qso.lotwUpload, "SSB queue state")
        eq(LotwUploadState.QUEUED, cw.qso.lotwUpload, "CW queue state")
        eq(LotwUploadState.QUEUED, digital.qso.lotwUpload, "digital queue state")
        eq(setOf("SSB", "CW", "FT8"), queue.pending("home").map { it.qso.mode }.toSet(), "shared queue mode set")
        eq(3, queue.size(), "shared queue size")

        val transport = FakeTransport().apply { reports += acceptedReport(qsos) }
        val signer = FakeSigner()
        val result = LotwSyncManager(transport).uploadTransactional(
            queue.pending("home").map { it.qso },
            signer,
            context(),
            "key-secret".toCharArray(),
            LotwAcceptancePolicy(1, 0, 0),
            LotwVerificationDelay { },
        )
        queue.applyResult(result)
        eq(LotwTransactionOutcome.ACCEPTED, result.outcome, "shared queue transaction outcome")
        eq(0, queue.size(), "accepted manual/digital QSOs must leave the same queue")
    }

    private fun context(acceptedSince: String? = null) = LotwUploadContext(
        stationProfile = LotwStationProfile(
            id = "home",
            name = "Home",
            stationCallsign = "N0PNG",
            gridSquare = "JN49",
            dxcc = 230,
            cqZone = 14,
            ituZone = 28,
            country = "Germany",
        ),
        tqslStationLocationName = "Home",
        credentials = LotwCredentials("n0png", "web-secret"),
        acceptedSince = acceptedSince,
    )

    private fun sampleBatch(): List<QsoRecord> = listOf(
        QsoRecord(
            id = 1,
            call = "W1AW",
            stationCallsign = "N0PNG",
            qsoDate = "20261005",
            timeOn = "140100",
            band = "40m",
            mode = "SSB",
            myGridSquare = "JN49",
            myDxcc = 230,
        ),
        QsoRecord(
            id = 2,
            call = "K1ABC",
            stationCallsign = "N0PNG",
            qsoDate = "20261005",
            timeOn = "140300",
            band = "20m",
            mode = "CW",
            myGridSquare = "JN49",
            myDxcc = 230,
        ),
        QsoRecord(
            id = 3,
            call = "DL1XYZ",
            stationCallsign = "N0PNG",
            qsoDate = "20261005",
            timeOn = "140500",
            band = "15m",
            mode = "FT8",
            myGridSquare = "JN49",
            myDxcc = 230,
        ),
    )

    private fun acceptedReport(qsos: List<QsoRecord>): String = buildString {
        append("<PROGRAMID:4>LoTW\n")
        append("<APP_LoTW_LASTQSORX:19>2026-10-05 14:10:00\n")
        append("<EOH>\n")
        for (q in qsos) {
            field("STATION_CALLSIGN", q.stationCallsign)
            field("CALL", q.call)
            field("QSO_DATE", q.qsoDate)
            field("TIME_ON", q.timeOn)
            field("BAND", q.band)
            // Deliberately normalize/remap a mode to prove acceptance matching
            // does not depend on LoTW preserving the local mode string.
            field("MODE", if (q.mode == "SSB") "PHONE" else q.mode)
            field("APP_LoTW_RXQSO", "2026-10-05 14:09:00")
            field("APP_LoTW_RECORDID", "RID-" + q.id)
            append("<EOR>\n")
        }
    }

    private fun StringBuilder.field(name: String, value: String) {
        append('<').append(name).append(':').append(value.length).append('>').append(value).append('\n')
    }
}
