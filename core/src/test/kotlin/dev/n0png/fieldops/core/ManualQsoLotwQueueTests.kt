package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.logbook.*
import java.time.Instant

object ManualQsoLotwQueueTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        checkThat(runCatching(block).isFailure, message)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        disabledPolicyLeavesLocalOnly()
        enabledManualAndDigitalShareQueue()
        enqueueIsIdempotentByImmutableQsoId()
        queueFailureDoesNotLoseLocalQso()
        policyEligibilityIsExplicit()
        enabledPolicyRequiresLocalQueue()
        println("CP-0005B manual-QSO LoTW queue tests: PASS assertions=$assertions")
    }

    private fun disabledPolicyLeavesLocalOnly() {
        val repo = InMemoryLogbookRepository()
        val queue = LotwUploadQueue()
        val logger = FastQsoLogger(
            repository = repo,
            idSource = SequentialQsoIdSource(1000),
            clock = UtcMillisClock { epoch("2026-10-06T10:00:00Z") },
            lotwQueue = queue,
            lotwPolicy = LotwLoggerPolicy.DISABLED,
        )

        val saved = logger.logManual(
            ManualQsoInput(call = "W1AW", mode = ManualQsoMode.SSB),
            session(),
            QsoRadioContext(14_200_000L, "USB"),
        )

        eq(LotwUploadState.NOT_UPLOADED, saved.lotwUpload, "disabled policy returned state")
        eq(LotwUploadState.NOT_UPLOADED, repo.get(saved.id)!!.lotwUpload, "disabled policy repository state")
        eq(0, queue.size(), "disabled policy queue size")
        eq(1, repo.all().size, "disabled policy local save count")
    }

    private fun enabledManualAndDigitalShareQueue() {
        val repo = InMemoryLogbookRepository()
        val queue = LotwUploadQueue()
        val logger = FastQsoLogger(
            repository = repo,
            idSource = SequentialQsoIdSource(2000),
            lotwQueue = queue,
            lotwPolicy = LotwLoggerPolicy.AUTO_MANUAL_AND_DIGITAL,
        )
        val s = session()

        val ssb = logger.logManual(
            ManualQsoInput(
                call = "W1AW",
                mode = ManualQsoMode.SSB,
                startedUtcMillis = epoch("2026-10-06T10:10:00Z"),
            ),
            s,
            QsoRadioContext(14_200_000L, "USB"),
        )
        val cw = logger.logManual(
            ManualQsoInput(
                call = "K1ABC",
                mode = ManualQsoMode.CW,
                startedUtcMillis = epoch("2026-10-06T10:11:00Z"),
            ),
            s,
            QsoRadioContext(7_030_000L, "CW"),
        )
        val ft8 = logger.logDigital(
            DigitalCompletedContact(
                providerId = "FT8AF",
                call = "DL1XYZ",
                adifMode = "MFSK",
                adifSubmode = "FT8",
                startedUtcMillis = epoch("2026-10-06T10:12:00Z"),
                frequencyHz = 7_074_000L,
                radioMode = "DATA-U",
            ),
            s,
        )

        eq(3, repo.all().size, "enabled policy local save count")
        eq(3, queue.size(), "enabled policy shared queue size")
        checkThat(listOf(ssb, cw, ft8).all { it.lotwUpload == LotwUploadState.QUEUED }, "enabled logger must return queued state")
        checkThat(repo.all().all { it.lotwUpload == LotwUploadState.QUEUED }, "authoritative local records must mirror queued state")
        eq(setOf(2000L, 2001L, 2002L), queue.pending("home").map { it.qso.id }.toSet(), "shared queue ids")
        eq(setOf("SSB", "CW", "MFSK"), queue.pending("home").map { it.qso.mode }.toSet(), "shared manual/digital queue modes")

        for (entry in queue.pending("home")) {
            eq("home", entry.stationProfileId, "queue station profile binding")
            eq("home", entry.qso.stationProfileId, "QSO station profile binding")
            eq("session-queue", entry.qso.sessionId, "QSO session binding")
            eq("N0PNG", entry.qso.stationCallsign, "QSO station callsign binding")
        }

        eq("FT8", queue.get(ft8.id)!!.qso.submode, "digital submode survives auto-queue")
        eq("FT8AF", queue.get(ft8.id)!!.qso.sourceProvider, "digital provider survives auto-queue")
    }

    private fun enqueueIsIdempotentByImmutableQsoId() {
        val queue = LotwUploadQueue()
        val qso = standaloneQso(3000)
        val first = queue.enqueue(qso, "home")
        val second = queue.enqueue(qso, "home")

        eq(1, queue.size(), "idempotent repeated enqueue size")
        eq(first, second, "idempotent repeated enqueue entry")
        eq(0, second.attempt, "idempotent enqueue attempt count")

        queue.applyResult(
            LotwTransactionalUploadResult(
                outcome = LotwTransactionOutcome.UPLOAD_FAILED,
                qsos = listOf(first.qso.copy(lotwUpload = LotwUploadState.QUEUED)),
            )
        )
        val afterFailure = queue.get(qso.id)!!
        eq(1, afterFailure.attempt, "failed transaction increments attempt")

        val repeated = queue.enqueue(qso, "home")
        eq(1, repeated.attempt, "re-enqueue must not reset attempt")
        eq(1, queue.size(), "re-enqueue after failure size")

        expectFailure("same immutable id with different contact must fail") {
            queue.enqueue(qso.copy(call = "K9DIFF"), "home")
        }
        expectFailure("same immutable id with different station profile must fail") {
            queue.enqueue(qso, "portable")
        }
        eq(1, queue.size(), "conflicting enqueue must not alter queue")
    }

    private fun queueFailureDoesNotLoseLocalQso() {
        val repo = InMemoryLogbookRepository()
        val failures = mutableListOf<String>()
        val failingQueue = LotwQueueSink { _, _ ->
            error("synthetic-local-queue-failure")
        }
        val logger = FastQsoLogger(
            repository = repo,
            idSource = SequentialQsoIdSource(4000),
            lotwQueue = failingQueue,
            lotwPolicy = LotwLoggerPolicy.AUTO_MANUAL_AND_DIGITAL,
            lotwQueueFailureHandler = LotwQueueFailureHandler { qso, error ->
                failures += qso.id.toString() + ":" + error.message
            },
        )

        val saved = logger.logManual(
            ManualQsoInput(
                call = "G0ABC",
                mode = ManualQsoMode.CW,
                startedUtcMillis = epoch("2026-10-06T10:20:00Z"),
            ),
            session(),
            QsoRadioContext(14_050_000L, "CW"),
        )

        eq(1, repo.all().size, "queue failure must not undo local save")
        eq(saved.id, repo.all().single().id, "queue failure saved immutable id")
        eq(LotwUploadState.NOT_UPLOADED, saved.lotwUpload, "queue failure returned local-only state")
        eq(LotwUploadState.NOT_UPLOADED, repo.get(saved.id)!!.lotwUpload, "queue failure repository state")
        eq(1, failures.size, "queue failure must be surfaced separately")
        checkThat(failures.single().contains("synthetic-local-queue-failure"), "queue failure detail missing")
    }

    private fun policyEligibilityIsExplicit() {
        val policy = LotwLoggerPolicy.AUTO_MANUAL_AND_DIGITAL
        checkThat(policy.shouldAutoQueue(standaloneQso(5000, origin = QsoOrigin.MANUAL, mode = "SSB")), "manual SSB should auto-queue")
        checkThat(policy.shouldAutoQueue(standaloneQso(5001, origin = QsoOrigin.MANUAL, mode = "CW")), "manual CW should auto-queue")
        checkThat(
            policy.shouldAutoQueue(
                standaloneQso(
                    5002,
                    origin = QsoOrigin.DIGITAL_AUTO,
                    mode = "MFSK",
                    submode = "FT8",
                    sourceProvider = "FT8AF",
                )
            ),
            "completed digital QSO should auto-queue",
        )
        checkThat(!policy.shouldAutoQueue(standaloneQso(5003, origin = QsoOrigin.LEGACY, mode = "SSB")), "legacy import must not auto-queue")
        checkThat(!policy.shouldAutoQueue(standaloneQso(5004, origin = QsoOrigin.IMPORTED, mode = "CW")), "imported QSO must not auto-queue")
        checkThat(
            !policy.shouldAutoQueue(
                standaloneQso(
                    5005,
                    origin = QsoOrigin.DIGITAL_AUTO,
                    mode = "MFSK",
                    submode = "FT8",
                    sourceProvider = null,
                )
            ),
            "digital QSO without provider evidence must not auto-queue",
        )
        checkThat(!LotwLoggerPolicy.DISABLED.shouldAutoQueue(standaloneQso(5006, origin = QsoOrigin.MANUAL, mode = "SSB")), "disabled policy must always refuse")
    }

    private fun enabledPolicyRequiresLocalQueue() {
        expectFailure("enabled logger policy without local queue must fail configuration") {
            FastQsoLogger(
                repository = InMemoryLogbookRepository(),
                idSource = SequentialQsoIdSource(6000),
                lotwPolicy = LotwLoggerPolicy.AUTO_MANUAL_AND_DIGITAL,
            )
        }
    }

    private fun session() = OperatingSession(
        id = "session-queue",
        stationProfileId = "home",
        stationCallsign = "N0PNG",
        startedUtcMillis = epoch("2026-10-06T09:00:00Z"),
        stationLocation = QsoLocationSnapshot(gridSquare = "JN49"),
    )

    private fun standaloneQso(
        id: Long,
        origin: QsoOrigin = QsoOrigin.MANUAL,
        mode: String = "SSB",
        submode: String? = null,
        sourceProvider: String? = if (origin == QsoOrigin.DIGITAL_AUTO) "TEST" else null,
    ) = QsoRecord(
        id = id,
        call = "W1AW",
        stationCallsign = "N0PNG",
        qsoDate = "20261006",
        timeOn = "103000",
        band = "20m",
        mode = mode,
        submode = submode,
        radioMode = if (mode == "CW") "CW" else if (origin == QsoOrigin.DIGITAL_AUTO) "DATA-U" else "USB",
        frequencyHz = 14_074_000L,
        stationProfileId = "home",
        sessionId = "session-queue",
        origin = origin,
        sourceProvider = sourceProvider,
    )

    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
