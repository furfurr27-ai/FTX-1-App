package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
import dev.n0png.fieldops.core.logbook.QsoRecord

object AwardsCenterApplicationServiceTests {
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
        cardsComeFromAuthoritativeLogbookAndPersistedEvidence()
        singleRecordIngestionRequiresExistingImmutableQso()
        explicitCallIsConsistencyCheckNotResolver()
        batchIngestionIsAtomicAcrossAllRecords()
        malformedExplicitAwardFieldRollsBackWholeBatch()
        evidenceConflictRollsBackWholeBatch()
        batchOrderingIsDeterministic()
        repeatedBatchIsIdempotent()
        lotwStyleParsedRecordFeedsProjectionWithoutNetwork()
        queryViewsFlowThroughApplicationService()
        sponsorStandingIsExplicitOnly()
        emptyBatchIsNoOp()
        qsoMutationDoesNotOccur()
        println("CP-0006F Awards Center application service tests: PASS assertions=$assertions")
    }

    private fun cardsComeFromAuthoritativeLogbookAndPersistedEvidence() {
        val logbook = logbook(
            qso(1, call = "K1AAA"),
            qso(2, call = "K2BBB"),
            qso(3, call = "K3CCC"),
            qso(4, call = "K4DDD"),
            qso(5, call = "K5EEE"),
            qso(6, call = "K6FFF"),
        )
        val evidence = InMemoryAwardEvidenceRepository(
            AwardEvidenceSnapshot(
                targets = listOf(
                    target(1, OfficialAwardTargetKind.CONTINENT, "AF"),
                    target(2, OfficialAwardTargetKind.CONTINENT, "AS"),
                    target(3, OfficialAwardTargetKind.CONTINENT, "EU"),
                    target(4, OfficialAwardTargetKind.CONTINENT, "NA"),
                    target(5, OfficialAwardTargetKind.CONTINENT, "OC"),
                    target(6, OfficialAwardTargetKind.CONTINENT, "SA"),
                ),
                confirmations = (1L..6L).map { AwardConfirmationEvidence(it, "LOTW") },
            )
        )
        val service = AwardsCenterApplicationService(logbook, evidence)

        val wac = service.cards().card("IARU_WAC_BASIC")
        eq(6, wac.workedCount, "service reads authoritative logbook WAC worked")
        eq(6, wac.confirmedCount, "service reads persisted confirmations")
        eq(true, wac.localThresholdMet, "service projects WAC threshold")
        eq(10000, wac.progress.basisPoints, "service projects WAC progress")

        logbook.save(qso(7, call = "K7GGG"))
        evidence.apply(
            AwardEvidenceBatch(
                targets = listOf(target(7, OfficialAwardTargetKind.DXCC_ENTITY, "291")),
                confirmations = listOf(AwardConfirmationEvidence(7, "LOTW")),
            )
        )
        val dxcc = service.cards().card("ARRL_DXCC_MIXED")
        eq(1, dxcc.workedCount, "service sees later authoritative logbook save")
        eq(1, dxcc.confirmedCount, "service sees later persisted evidence")
    }

    private fun singleRecordIngestionRequiresExistingImmutableQso() {
        val logbook = logbook(qso(10, call = "DL1ABC"))
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        val result = service.ingestResolvedAdifRecord(
            resolved(
                qsoId = 10,
                fields = mapOf(
                    "CALL" to "DL1ABC",
                    "DXCC" to "230",
                    "CONT" to "EU",
                    "LOTW_QSL_RCVD" to "Y",
                ),
            )
        )

        eq(setOf(10L), result.qsoIds, "single ingestion qso id")
        eq(1, result.recordsProcessed, "single ingestion record count")
        eq(2, result.writeResult.insertedTargets, "single ingestion target count")
        eq(1, result.writeResult.insertedConfirmations, "single ingestion confirmation count")
        eq(2, service.evidenceSnapshot().targets.size, "single ingestion persisted targets")

        val dxcc = service.cards().card("ARRL_DXCC_MIXED")
        eq(1, dxcc.confirmedCount, "single ingestion immediately feeds cards")

        val before = service.evidenceSnapshot()
        expectFailure("unknown immutable QSO id rejected") {
            service.ingestResolvedAdifRecord(
                resolved(
                    qsoId = 999,
                    fields = mapOf("CALL" to "K9NOPE", "DXCC" to "291"),
                )
            )
        }
        eq(before, service.evidenceSnapshot(), "unknown QSO writes nothing")
    }

    private fun explicitCallIsConsistencyCheckNotResolver() {
        val logbook = logbook(
            qso(20, call = "K1ABC"),
            qso(21, call = "K1ABC"),
        )
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        service.ingestResolvedAdifRecord(
            resolved(
                qsoId = 21,
                fields = mapOf("CALL" to "k1abc", "STATE" to "OR"),
            )
        )
        eq(
            setOf(21L),
            service.evidenceSnapshot().targets.mapTo(linkedSetOf()) { it.qsoId },
            "duplicate callsign does not resolve or redirect qso id",
        )

        val before = service.evidenceSnapshot()
        expectFailure("explicit CALL mismatch fails closed") {
            service.ingestResolvedAdifRecord(
                resolved(
                    qsoId = 20,
                    fields = mapOf("CALL" to "K2WRONG", "STATE" to "WA"),
                )
            )
        }
        eq(before, service.evidenceSnapshot(), "CALL mismatch commits nothing")

        service.ingestResolvedAdifRecord(
            resolved(
                qsoId = 20,
                fields = mapOf("DXCC" to "291"),
            )
        )
        checkThat(
            service.evidenceSnapshot().targets.any {
                it.qsoId == 20L && it.kind == OfficialAwardTargetKind.DXCC_ENTITY
            },
            "CALL is optional after immutable QSO id is already resolved",
        )
    }

    private fun batchIngestionIsAtomicAcrossAllRecords() {
        val logbook = logbook(
            qso(30, call = "K1AAA"),
            qso(31, call = "K2BBB"),
        )
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        expectFailure("unknown QSO anywhere in batch rejects whole batch") {
            service.ingestResolvedAdifRecords(
                listOf(
                    resolved(30, mapOf("CALL" to "K1AAA", "CONT" to "NA")),
                    resolved(999, mapOf("CALL" to "K9NOPE", "CONT" to "EU")),
                    resolved(31, mapOf("CALL" to "K2BBB", "CONT" to "AS")),
                )
            )
        }
        eq(AwardEvidenceSnapshot(), service.evidenceSnapshot(), "unknown-QSO batch remains atomic")

        expectFailure("CALL mismatch anywhere in batch rejects whole batch") {
            service.ingestResolvedAdifRecords(
                listOf(
                    resolved(30, mapOf("CALL" to "K1AAA", "CONT" to "NA")),
                    resolved(31, mapOf("CALL" to "K9WRONG", "CONT" to "AS")),
                )
            )
        }
        eq(AwardEvidenceSnapshot(), service.evidenceSnapshot(), "CALL-mismatch batch remains atomic")
    }

    private fun malformedExplicitAwardFieldRollsBackWholeBatch() {
        val logbook = logbook(
            qso(40, call = "K1AAA"),
            qso(41, call = "K2BBB"),
        )
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        expectFailure("malformed explicit field rejects entire application-service batch") {
            service.ingestResolvedAdifRecords(
                listOf(
                    resolved(40, mapOf("CALL" to "K1AAA", "STATE" to "OR")),
                    resolved(41, mapOf("CALL" to "K2BBB", "IOTA" to "not-an-iota")),
                )
            )
        }
        eq(0, service.evidenceSnapshot().targets.size, "malformed field batch writes no earlier target")
        eq(0, service.evidenceSnapshot().confirmations.size, "malformed field batch writes no confirmations")
    }

    private fun evidenceConflictRollsBackWholeBatch() {
        val logbook = logbook(
            qso(50, call = "K1AAA"),
            qso(51, call = "K2BBB"),
        )
        val evidence = InMemoryAwardEvidenceRepository(
            AwardEvidenceSnapshot(
                targets = listOf(target(50, OfficialAwardTargetKind.US_STATE, "OR"))
            )
        )
        val service = AwardsCenterApplicationService(logbook, evidence)
        val before = service.evidenceSnapshot()

        expectFailure("existing single-valued evidence conflict rejects combined batch") {
            service.ingestResolvedAdifRecords(
                listOf(
                    resolved(51, mapOf("CALL" to "K2BBB", "DXCC" to "291")),
                    resolved(50, mapOf("CALL" to "K1AAA", "STATE" to "WA"), source = source("OTHER")),
                )
            )
        }

        eq(before, service.evidenceSnapshot(), "repository conflict rolls back unrelated converted evidence")
    }

    private fun batchOrderingIsDeterministic() {
        fun buildService(): Pair<AwardsCenterApplicationService, InMemoryAwardEvidenceRepository> {
            val evidence = InMemoryAwardEvidenceRepository()
            return AwardsCenterApplicationService(
                logbook(
                    qso(60, call = "K1AAA"),
                    qso(61, call = "K2BBB"),
                    qso(62, call = "K3CCC"),
                ),
                evidence,
            ) to evidence
        }

        val records = listOf(
            resolved(62, mapOf("CALL" to "K3CCC", "CONT" to "EU"), source = source("SRC_C", "r3")),
            resolved(60, mapOf("CALL" to "K1AAA", "DXCC" to "291"), source = source("SRC_A", "r1")),
            resolved(61, mapOf("CALL" to "K2BBB", "STATE" to "OR"), source = source("SRC_B", "r2")),
        )

        val (a, repoA) = buildService()
        val resultA = a.ingestResolvedAdifRecords(records)

        val (b, repoB) = buildService()
        val resultB = b.ingestResolvedAdifRecords(records.reversed())

        eq(setOf(60L, 61L, 62L), resultA.qsoIds, "batch qso ids deterministic ascending")
        eq(resultA.qsoIds, resultB.qsoIds, "batch qso id result independent of input order")
        eq(repoA.snapshot(), repoB.snapshot(), "persisted evidence canonical regardless of input order")
        eq(3, resultA.writeResult.insertedTargets, "ordered batch target count")
        eq(3, resultB.writeResult.insertedTargets, "reversed batch target count")
    }

    private fun repeatedBatchIsIdempotent() {
        val logbook = logbook(
            qso(70, call = "K1AAA"),
            qso(71, call = "K2BBB"),
        )
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)
        val records = listOf(
            resolved(
                70,
                mapOf(
                    "CALL" to "K1AAA",
                    "DXCC" to "291",
                    "LOTW_QSL_RCVD" to "Y",
                ),
                source = source("IMPORT", "record-70"),
            ),
            resolved(
                71,
                mapOf(
                    "CALL" to "K2BBB",
                    "POTA_REF" to "US-0001;US-0002",
                ),
                source = source("IMPORT", "record-71"),
            ),
        )

        val first = service.ingestResolvedAdifRecords(records)
        eq(3, first.writeResult.insertedTargets, "first batch targets inserted")
        eq(1, first.writeResult.insertedConfirmations, "first batch confirmation inserted")

        val snapshot = service.evidenceSnapshot()
        val second = service.ingestResolvedAdifRecords(records)
        eq(0, second.writeResult.insertedTargets, "repeat batch inserts no targets")
        eq(3, second.writeResult.unchangedTargets, "repeat batch reports unchanged targets")
        eq(0, second.writeResult.insertedConfirmations, "repeat batch inserts no confirmation")
        eq(1, second.writeResult.unchangedConfirmations, "repeat batch reports unchanged confirmation")
        eq(snapshot, service.evidenceSnapshot(), "repeat batch leaves canonical state unchanged")
    }

    private fun lotwStyleParsedRecordFeedsProjectionWithoutNetwork() {
        val logbook = logbook(
            qso(80, call = "ZS1AAA"),
            qso(81, call = "JA1BBB"),
            qso(82, call = "DL1CCC"),
            qso(83, call = "K1DDD"),
            qso(84, call = "VK1EEE"),
            qso(85, call = "PY1FFF"),
        )
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        val continents = listOf("AF", "AS", "EU", "NA", "OC", "SA")
        val calls = listOf("ZS1AAA", "JA1BBB", "DL1CCC", "K1DDD", "VK1EEE", "PY1FFF")
        val resolvedRecords = continents.indices.map { index ->
            val text = buildString {
                append("<ADIF_VER:5>3.1.6<EOH>")
                append("<CALL:${calls[index].length}>${calls[index]}")
                append("<CONT:2>${continents[index]}")
                append("<LOTW_QSL_RCVD:1>Y")
                append("<EOR>")
            }
            val parsed = dev.n0png.fieldops.core.logbook.AdifCodec.parse(text)
            resolved(
                qsoId = 80L + index,
                fields = parsed.records.single(),
                source = source(
                    id = "LOTW_ADIF_FIXTURE",
                    reference = "lotw-record-${index + 1}",
                ),
            )
        }

        val result = service.ingestResolvedAdifRecords(resolvedRecords)
        eq(6, result.recordsProcessed, "LoTW-style parsed record batch count")
        eq(6, result.writeResult.insertedTargets, "LoTW-style explicit continents inserted")
        eq(6, result.writeResult.insertedConfirmations, "LoTW-style explicit confirmations inserted")

        val wac = service.cards().card("IARU_WAC_BASIC")
        eq(6, wac.confirmedCount, "LoTW-style ingestion feeds WAC confirmed count")
        eq(true, wac.localThresholdMet, "LoTW-style ingestion completes local WAC")
        eq(10000, wac.progress.basisPoints, "LoTW-style ingestion WAC progress")
        eq(OfficialAwardStanding.UNKNOWN, wac.sponsorStanding, "LoTW-style evidence does not invent sponsor standing")
        eq(null, wac.claimableNow, "LoTW-style local completion does not invent claimability")
    }

    private fun queryViewsFlowThroughApplicationService() {
        val logbook = logbook(
            qso(90, call = "K1AAA", band = "20m", mode = "CW", date = "20260101"),
            qso(91, call = "K2BBB", band = "40m", mode = "MFSK", submode = "FT8", date = "20260201"),
            qso(92, call = "K3CCC", band = "20m", mode = "MFSK", submode = "FT8", date = "20260301"),
        )
        val evidence = InMemoryAwardEvidenceRepository(
            AwardEvidenceSnapshot(
                targets = listOf(
                    target(90, OfficialAwardTargetKind.DXCC_ENTITY, "1"),
                    target(91, OfficialAwardTargetKind.DXCC_ENTITY, "2"),
                    target(92, OfficialAwardTargetKind.DXCC_ENTITY, "3"),
                ),
                confirmations = listOf(
                    AwardConfirmationEvidence(90, "LOTW"),
                    AwardConfirmationEvidence(91, "LOTW"),
                    AwardConfirmationEvidence(92, "LOTW"),
                ),
            )
        )
        val service = AwardsCenterApplicationService(logbook, evidence)

        val digital20 = service.cards(
            AwardsCenterQuery(
                modeView = AwardsCenterModeView.DIGITAL,
                band = "20M",
                dateRange = AwardsCenterDateRange("2026-02-15", "2026-12-31"),
            )
        ).card("ARRL_DXCC_MIXED")

        eq(1, digital20.confirmedCount, "application service forwards mode/band/date query")
        eq("20m", digital20.query.normalizedBand, "query band remains normalized")
        eq(AwardsCenterModeView.DIGITAL, digital20.query.modeView, "query mode retained")
    }

    private fun sponsorStandingIsExplicitOnly() {
        val logbook = InMemoryLogbookRepository()
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        val before = service.cards().card("ARRL_DXCC_MIXED")
        eq(OfficialAwardStanding.UNKNOWN, before.sponsorStanding, "initial sponsor standing unknown")

        val result = service.recordSponsorStanding(
            OfficialAwardStandingRecord(
                awardId = "ARRL_DXCC_MIXED",
                standing = OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
                sponsorReference = "explicit-fixture",
                recordedAtUtcMillis = 100L,
            )
        )
        eq(1, result.insertedSponsorStandings, "explicit sponsor standing stored")

        val after = service.cards().card("ARRL_DXCC_MIXED")
        eq(OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED, after.sponsorStanding, "explicit sponsor standing projected")
        eq(true, after.claimableNow, "explicit sponsor eligibility controls claimable-now")
        eq(false, after.localThresholdMet, "sponsor eligibility does not fabricate local threshold")

        expectFailure("unknown award sponsor standing rejected") {
            service.recordSponsorStanding(
                OfficialAwardStandingRecord(
                    awardId = "NOT_A_REAL_CATALOG_AWARD",
                    standing = OfficialAwardStanding.AWARDED,
                    sponsorReference = "bad",
                    recordedAtUtcMillis = 200L,
                )
            )
        }
        eq(1, service.evidenceSnapshot().sponsorStandings.size, "unknown award standing not persisted")
    }

    private fun emptyBatchIsNoOp() {
        val service = AwardsCenterApplicationService(
            logbook = InMemoryLogbookRepository(),
            evidenceRepository = InMemoryAwardEvidenceRepository(),
        )
        val result = service.ingestResolvedAdifRecords(emptyList())

        eq(0, result.recordsProcessed, "empty batch record count")
        eq(emptySet<Long>(), result.qsoIds, "empty batch qso ids")
        eq(AwardEvidenceApplyResult(), result.writeResult, "empty batch write result")
        eq(AwardEvidenceSnapshot(), service.evidenceSnapshot(), "empty batch leaves evidence empty")
    }

    private fun qsoMutationDoesNotOccur() {
        val qso = qso(
            110,
            call = "K1ABC",
            band = "40m",
            mode = "MFSK",
            submode = "FT8",
            date = "20261006",
        )
        val before = qso.copy()
        val logbook = logbook(qso)
        val service = AwardsCenterApplicationService(
            logbook,
            InMemoryAwardEvidenceRepository(),
        )

        service.ingestResolvedAdifRecord(
            resolved(
                110,
                mapOf(
                    "CALL" to "K1ABC",
                    "DXCC" to "291",
                    "CONT" to "NA",
                    "LOTW_QSL_RCVD" to "Y",
                ),
            )
        )
        service.cards(AwardsCenterQuery(modeView = AwardsCenterModeView.DIGITAL))

        eq(before, logbook.get(110), "ingestion/projection never mutate authoritative QSO")
        eq("MFSK", logbook.get(110)!!.mode, "exact mode preserved")
        eq("FT8", logbook.get(110)!!.submode, "exact submode preserved")
    }

    private fun logbook(vararg qsos: QsoRecord): InMemoryLogbookRepository =
        InMemoryLogbookRepository().also { repository ->
            qsos.forEach(repository::save)
        }

    private fun resolved(
        qsoId: Long,
        fields: Map<String, String>,
        source: AwardAdifImportSource = source("ADIF_FIXTURE", "qso-$qsoId"),
    ) = ResolvedAwardAdifRecord(
        qsoId = qsoId,
        fields = fields,
        source = source,
    )

    private fun source(
        id: String,
        reference: String,
    ) = AwardAdifImportSource(
        sourceId = id,
        sourceVersion = "fixture-v1",
        sourceUrl = "https://example.invalid/$id",
        retrievedAtUtcMillis = 123L,
        recordReference = reference,
    )

    private fun target(
        qsoId: Long,
        kind: OfficialAwardTargetKind,
        value: String,
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = kind,
        value = value,
        provenance = AwardTargetProvenance(
            sourceId = "SERVICE_FIXTURE",
            sourceVersion = "v1",
            sourceUrl = "https://example.invalid/service-fixture",
            reference = "qso-$qsoId",
            retrievedAtUtcMillis = 123L,
        ),
    )

    private fun qso(
        id: Long,
        call: String = "W1AW",
        band: String = "20m",
        mode: String = "CW",
        submode: String? = null,
        date: String = "20261006",
    ) = QsoRecord(
        id = id,
        call = call,
        stationCallsign = "N0PNG",
        qsoDate = date,
        timeOn = "120000",
        band = band,
        mode = mode,
        submode = submode,
        sourceProvider = if (submode != null) "SYNTHETIC" else null,
    )

    private fun List<AwardsCenterAwardCard>.card(id: String): AwardsCenterAwardCard =
        single { it.awardId == id }
}
