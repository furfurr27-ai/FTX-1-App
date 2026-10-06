package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.QsoRecord

object AwardEvidencePersistenceTests {
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
        targetWritesAreIdempotentAndConflictSafe()
        confirmationWritesAreIdempotentAndConflictSafe()
        sponsorStandingWritesAreHistoricalAndConflictSafe()
        snapshotCodecRoundTripsDeterministically()
        persistedRepositoryRestoresAcrossInstances()
        failedBackingStoreWriteDoesNotAdvanceState()
        explicitAdifTargetsImportWithProvenance()
        myStationFieldsAndCallsignDoNotCreateRemoteTargets()
        explicitPotaFieldsSupportDirectAndSigForms()
        confirmationImportIsConservative()
        parsedAdifRecordsFeedTheAdapter()
        importedEvidenceFeedsAwardsCenterWithoutQsoMutation()
        malformedExplicitAwardFieldsFailClosed()
        println("CP-0006E award evidence persistence tests: PASS assertions=$assertions")
    }

    private fun targetWritesAreIdempotentAndConflictSafe() {
        val repo = InMemoryAwardEvidenceRepository()
        val or = target(1, OfficialAwardTargetKind.US_STATE, "OR")

        val first = repo.apply(AwardEvidenceBatch(targets = listOf(or)))
        eq(1, first.insertedTargets, "first target inserted")
        eq(0, first.unchangedTargets, "first target no unchanged")
        eq(1, repo.targetEvidence().size, "target persisted in repository")

        val again = repo.apply(AwardEvidenceBatch(targets = listOf(or)))
        eq(0, again.insertedTargets, "idempotent target no second insert")
        eq(1, again.unchangedTargets, "idempotent target unchanged")
        eq(1, repo.targetEvidence().size, "idempotent target count stable")

        expectFailure("same provenance identity with changed retrieval time conflicts") {
            repo.apply(
                AwardEvidenceBatch(
                    targets = listOf(
                        or.copy(
                            provenance = or.provenance.copy(retrievedAtUtcMillis = 999L)
                        )
                    )
                )
            )
        }
        eq(1, repo.targetEvidence().size, "conflicting target write is atomic")

        expectFailure("conflicting single-valued state fails even with other provenance") {
            repo.apply(
                AwardEvidenceBatch(
                    targets = listOf(
                        target(1, OfficialAwardTargetKind.US_STATE, "WA", sourceId = "SECOND_SOURCE")
                    )
                )
            )
        }
        eq("OR", repo.targetEvidence().single().normalizedValue, "existing state retained after conflict")

        val potaBatch = repo.apply(
            AwardEvidenceBatch(
                targets = listOf(
                    target(2, OfficialAwardTargetKind.POTA_REFERENCE, "US-0001"),
                    target(2, OfficialAwardTargetKind.POTA_REFERENCE, "US-0002"),
                )
            )
        )
        eq(2, potaBatch.insertedTargets, "multiple POTA references inserted")
        eq(
            setOf("US-0001", "US-0002"),
            repo.targetEvidence()
                .filter { it.qsoId == 2L }
                .mapTo(linkedSetOf()) { it.normalizedValue },
            "multiple POTA references preserved",
        )

        val atomicRepo = InMemoryAwardEvidenceRepository()
        expectFailure("batch conflict rolls back preceding inserts") {
            atomicRepo.apply(
                AwardEvidenceBatch(
                    targets = listOf(
                        target(3, OfficialAwardTargetKind.DXCC_ENTITY, "291"),
                        target(3, OfficialAwardTargetKind.DXCC_ENTITY, "110", sourceId = "OTHER"),
                    )
                )
            )
        }
        eq(0, atomicRepo.targetEvidence().size, "failed target batch commits nothing")
    }

    private fun confirmationWritesAreIdempotentAndConflictSafe() {
        val repo = InMemoryAwardEvidenceRepository()
        val lotw = AwardConfirmationEvidence(
            qsoId = 10,
            source = "LOTW",
            reference = "LOTW_QSL_RCVD=Y;date=20261001",
            confirmedAtUtcMillis = 100L,
        )

        val first = repo.apply(AwardEvidenceBatch(confirmations = listOf(lotw)))
        eq(1, first.insertedConfirmations, "confirmation inserted")
        eq(1, repo.confirmations().size, "confirmation stored")

        val again = repo.apply(AwardEvidenceBatch(confirmations = listOf(lotw)))
        eq(1, again.unchangedConfirmations, "confirmation idempotent")
        eq(1, repo.confirmations().size, "confirmation count stable")

        expectFailure("same confirmation identity with different timestamp conflicts") {
            repo.apply(
                AwardEvidenceBatch(
                    confirmations = listOf(lotw.copy(confirmedAtUtcMillis = 200L))
                )
            )
        }
        eq(100L, repo.confirmations().single().confirmedAtUtcMillis, "confirmation conflict retains original")

        val paper = AwardConfirmationEvidence(
            qsoId = 10,
            source = "ADIF_QSL_RCVD",
            reference = "QSL_RCVD=Y",
        )
        repo.apply(AwardEvidenceBatch(confirmations = listOf(paper)))
        eq(2, repo.confirmations().size, "distinct confirmation sources coexist")
    }

    private fun sponsorStandingWritesAreHistoricalAndConflictSafe() {
        val repo = InMemoryAwardEvidenceRepository()
        val eligible = OfficialAwardStandingRecord(
            awardId = "ARRL_DXCC_MIXED",
            standing = OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
            sponsorReference = "eligible-1",
            recordedAtUtcMillis = 100L,
        )
        val submitted = OfficialAwardStandingRecord(
            awardId = "ARRL_DXCC_MIXED",
            standing = OfficialAwardStanding.SUBMITTED,
            sponsorReference = "submission-1",
            recordedAtUtcMillis = 200L,
        )

        val result = repo.apply(AwardEvidenceBatch(sponsorStandings = listOf(eligible, submitted)))
        eq(2, result.insertedSponsorStandings, "sponsor history inserted")
        eq(2, repo.sponsorStandings().size, "sponsor history retained")

        val again = repo.apply(AwardEvidenceBatch(sponsorStandings = listOf(submitted)))
        eq(1, again.unchangedSponsorStandings, "sponsor standing idempotent")

        expectFailure("same award/timestamp conflicting sponsor standing fails") {
            repo.apply(
                AwardEvidenceBatch(
                    sponsorStandings = listOf(
                        submitted.copy(
                            standing = OfficialAwardStanding.AWARDED,
                            sponsorReference = "award-1",
                        )
                    )
                )
            )
        }
        eq(2, repo.sponsorStandings().size, "sponsor conflict commits nothing")
    }

    private fun snapshotCodecRoundTripsDeterministically() {
        val snapshot = AwardEvidenceSnapshot(
            targets = listOf(
                target(20, OfficialAwardTargetKind.CONTINENT, "EU", reference = "row\t20"),
                target(20, OfficialAwardTargetKind.POTA_REFERENCE, "DE-0001", sourceId = "ÜNICODE_SOURCE"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(
                    qsoId = 20,
                    source = "LOTW",
                    reference = "confirmed;record=α",
                    confirmedAtUtcMillis = 123L,
                )
            ),
            sponsorStandings = listOf(
                OfficialAwardStandingRecord(
                    awardId = "IARU_WAC_BASIC",
                    standing = OfficialAwardStanding.AWARDED,
                    sponsorReference = "certificate/42",
                    recordedAtUtcMillis = 456L,
                )
            ),
        )

        val encoded = AwardEvidenceSnapshotCodec.encode(snapshot)
        checkThat(encoded.startsWith("FIELDOPS_AWARD_EVIDENCE\t1\n"), "snapshot header")
        checkThat(!encoded.contains("row\t20"), "raw tab-containing reference is encoded")
        val decoded = AwardEvidenceSnapshotCodec.decode(encoded)
        val reencoded = AwardEvidenceSnapshotCodec.encode(decoded)
        eq(encoded, reencoded, "snapshot encoding deterministic")
        eq(2, decoded.targets.size, "snapshot target count")
        eq(1, decoded.confirmations.size, "snapshot confirmation count")
        eq(1, decoded.sponsorStandings.size, "snapshot sponsor count")
        eq("row\t20", decoded.targets.first { it.kind == OfficialAwardTargetKind.CONTINENT }.provenance.reference, "tab reference round trip")
        eq("ÜNICODE_SOURCE", decoded.targets.first { it.kind == OfficialAwardTargetKind.POTA_REFERENCE }.provenance.sourceId, "unicode source round trip")
        eq("confirmed;record=α", decoded.confirmations.single().reference, "unicode confirmation round trip")

        expectFailure("invalid snapshot header") {
            AwardEvidenceSnapshotCodec.decode("NOT_FIELDOPS\t1\n")
        }
        expectFailure("unknown snapshot row") {
            AwardEvidenceSnapshotCodec.decode("FIELDOPS_AWARD_EVIDENCE\t1\nX\tbad\n")
        }
    }

    private fun persistedRepositoryRestoresAcrossInstances() {
        val store = InMemoryAwardEvidenceSnapshotStore()
        val first = PersistedAwardEvidenceRepository(store)
        first.apply(
            AwardEvidenceBatch(
                targets = listOf(
                    target(30, OfficialAwardTargetKind.DXCC_ENTITY, "291"),
                    target(30, OfficialAwardTargetKind.CONTINENT, "NA"),
                ),
                confirmations = listOf(
                    AwardConfirmationEvidence(30, "LOTW", "LOTW_QSL_RCVD=Y")
                ),
                sponsorStandings = listOf(
                    OfficialAwardStandingRecord(
                        "ARRL_DXCC_MIXED",
                        OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED,
                        "fixture",
                        100L,
                    )
                ),
            )
        )

        checkThat(store.serialized() != null, "persistent repository writes snapshot store")
        val second = PersistedAwardEvidenceRepository(store)
        eq(first.snapshot(), second.snapshot(), "new repository instance restores exact persisted state")
        eq(2, second.targetEvidence().size, "restored targets")
        eq(1, second.confirmations().size, "restored confirmations")
        eq(1, second.sponsorStandings().size, "restored sponsor standings")

        val idempotent = second.apply(
            AwardEvidenceBatch(
                targets = listOf(target(30, OfficialAwardTargetKind.DXCC_ENTITY, "291"))
            )
        )
        eq(1, idempotent.unchangedTargets, "restored repository remains idempotent")
    }

    private fun failedBackingStoreWriteDoesNotAdvanceState() {
        class FailingStore : AwardEvidenceSnapshotStore.Readable {
            var content: String? = null
            var failWrites = true

            override fun read(): String? = content

            override fun write(serializedSnapshot: String) {
                if (failWrites) error("simulated disk failure")
                content = serializedSnapshot
            }
        }

        val store = FailingStore()
        val repo = PersistedAwardEvidenceRepository(store)

        expectFailure("backing store failure propagates") {
            repo.apply(
                AwardEvidenceBatch(
                    targets = listOf(target(40, OfficialAwardTargetKind.US_STATE, "OR"))
                )
            )
        }
        eq(0, repo.targetEvidence().size, "failed store write does not advance memory state")
        eq(null, store.content, "failed store write leaves backing store unchanged")

        store.failWrites = false
        val retry = repo.apply(
            AwardEvidenceBatch(
                targets = listOf(target(40, OfficialAwardTargetKind.US_STATE, "OR"))
            )
        )
        eq(1, retry.insertedTargets, "whole batch can be retried after persistence failure")
        eq(1, repo.targetEvidence().size, "successful retry advances state")
    }

    private fun explicitAdifTargetsImportWithProvenance() {
        val adapter = AwardAdifEnrichmentAdapter()
        val source = importSource(reference = "file.adi#record-7")
        val batch = adapter.fromRecord(
            qsoId = 50,
            record = mapOf(
                "dxcc" to "291",
                "STATE" to "or",
                "CONT" to "NA",
                "IOTA" to "NA-065",
                "POTA_REF" to "US-0001",
                "CALL" to "K7ABC",
            ),
            source = source,
        )

        eq(5, batch.targets.size, "all explicit award target fields imported")
        eq(
            setOf(
                OfficialAwardTargetKind.DXCC_ENTITY,
                OfficialAwardTargetKind.US_STATE,
                OfficialAwardTargetKind.CONTINENT,
                OfficialAwardTargetKind.IOTA_GROUP,
                OfficialAwardTargetKind.POTA_REFERENCE,
            ),
            batch.targets.mapTo(linkedSetOf()) { it.kind },
            "expected explicit award target kinds",
        )
        eq("291", batch.targets.single { it.kind == OfficialAwardTargetKind.DXCC_ENTITY }.normalizedValue, "DXCC import")
        eq("OR", batch.targets.single { it.kind == OfficialAwardTargetKind.US_STATE }.normalizedValue, "state import")
        eq("NORTH_AMERICA", batch.targets.single { it.kind == OfficialAwardTargetKind.CONTINENT }.normalizedValue, "continent import")
        eq("NA-065", batch.targets.single { it.kind == OfficialAwardTargetKind.IOTA_GROUP }.normalizedValue, "IOTA import")
        eq("US-0001", batch.targets.single { it.kind == OfficialAwardTargetKind.POTA_REFERENCE }.normalizedValue, "POTA import")
        checkThat(batch.targets.all { it.provenance.sourceId == "ADIF_FILE" }, "source id retained")
        checkThat(batch.targets.all { it.provenance.sourceVersion == "3.1.6-fixture" }, "source version retained")
        checkThat(batch.targets.all { it.provenance.reference == "file.adi#record-7" }, "record reference retained")
        checkThat(batch.targets.all { it.provenance.retrievedAtUtcMillis == 777L }, "retrieval time retained")
        eq(0, batch.confirmations.size, "target fields alone do not imply confirmation")
    }

    private fun myStationFieldsAndCallsignDoNotCreateRemoteTargets() {
        val batch = AwardAdifEnrichmentAdapter().fromRecord(
            qsoId = 60,
            record = mapOf(
                "CALL" to "K1ABC",
                "COUNTRY" to "United States",
                "GRIDSQUARE" to "FN31",
                "MY_DXCC" to "291",
                "MY_STATE" to "OR",
                "MY_COUNTRY" to "United States",
                "MY_GRIDSQUARE" to "CN85",
                "NOTES" to "POTA US-9999 IOTA NA-001",
            ),
            source = importSource(),
        )

        eq(0, batch.targets.size, "callsign/country/grid/my-fields/notes do not infer remote award targets")
        eq(0, batch.confirmations.size, "ordinary ADIF QSO record does not imply confirmation")
    }

    private fun explicitPotaFieldsSupportDirectAndSigForms() {
        val adapter = AwardAdifEnrichmentAdapter()

        val direct = adapter.fromRecord(
            70,
            mapOf("POTA_REF" to "US-0001; US-0002,US-0003"),
            importSource(),
        )
        eq(
            setOf("US-0001", "US-0002", "US-0003"),
            direct.targets.mapTo(linkedSetOf()) { it.normalizedValue },
            "direct explicit POTA list imported",
        )

        val app = adapter.fromRecord(
            71,
            mapOf("APP_POTA_REF" to "DE-0001"),
            importSource(),
        )
        eq("DE-0001", app.targets.single().normalizedValue, "explicit app POTA field imported")

        val sig = adapter.fromRecord(
            72,
            mapOf("SIG" to "POTA", "SIG_INFO" to "K-1234"),
            importSource(),
        )
        eq("K-1234", sig.targets.single().normalizedValue, "SIG/SIG_INFO POTA form imported")

        val notPota = adapter.fromRecord(
            73,
            mapOf("SIG" to "SOTA", "SIG_INFO" to "US/W7O/CE-001"),
            importSource(),
        )
        eq(0, notPota.targets.size, "non-POTA SIG_INFO is not reinterpreted")

        val dedupe = adapter.fromRecord(
            74,
            mapOf(
                "POTA_REF" to "US-0001",
                "SIG" to "POTA",
                "SIG_INFO" to "US-0001",
            ),
            importSource(),
        )
        eq(1, dedupe.targets.size, "same explicit POTA reference deduplicated within record")
    }

    private fun confirmationImportIsConservative() {
        val adapter = AwardAdifEnrichmentAdapter()

        val explicit = adapter.fromRecord(
            80,
            mapOf(
                "LOTW_QSL_RCVD" to "Y",
                "LOTW_QSLRDATE" to "20261001",
                "QSL_RCVD" to "Y",
                "QSLRDATE" to "20260930",
            ),
            importSource(reference = "record-80"),
        )
        eq(2, explicit.confirmations.size, "two explicit confirmation channels imported")
        eq(
            setOf("LOTW", "ADIF_QSL_RCVD"),
            explicit.confirmations.mapTo(linkedSetOf()) { it.source },
            "confirmation source identity retained",
        )
        checkThat(
            explicit.confirmations.single { it.source == "LOTW" }.reference!!.contains("20261001"),
            "LoTW confirmation date retained as evidence reference",
        )
        checkThat(
            explicit.confirmations.single { it.source == "ADIF_QSL_RCVD" }.reference!!.contains("record-80"),
            "record provenance retained in confirmation reference",
        )

        for (flag in listOf("N", "R", "I", "V", "")) {
            val batch = adapter.fromRecord(
                81,
                mapOf("LOTW_QSL_RCVD" to flag, "QSL_RCVD" to flag),
                importSource(),
            )
            eq(0, batch.confirmations.size, "confirmation flag $flag is not treated as explicit received=Y")
        }

        val uploadOnly = adapter.fromRecord(
            82,
            mapOf(
                "LOTW_QSL_SENT" to "Y",
                "LOTW_QSLSDATE" to "20261001",
            ),
            importSource(),
        )
        eq(0, uploadOnly.confirmations.size, "LoTW sent/upload metadata is not confirmation")
    }

    private fun parsedAdifRecordsFeedTheAdapter() {
        val text = buildString {
            append("<ADIF_VER:5>3.1.6<EOH>\n")
            append("<CALL:5>K1ABC")
            append("<DXCC:3>291")
            append("<STATE:2>OR")
            append("<CONT:2>NA")
            append("<IOTA:6>NA-065")
            append("<SIG:4>POTA")
            append("<SIG_INFO:6>K-1234")
            append("<LOTW_QSL_RCVD:1>Y")
            append("<EOR>\n")
        }

        val report = AdifCodec.parse(text)
        eq(1, report.records.size, "existing ADIF parser returns one record")
        val batch = AwardAdifEnrichmentAdapter().fromRecord(
            90,
            report.records.single(),
            importSource(reference = "parsed-record"),
        )

        eq(5, batch.targets.size, "parsed ADIF explicit award fields imported")
        eq(1, batch.confirmations.size, "parsed ADIF explicit LoTW confirmation imported")
        eq("LOTW", batch.confirmations.single().source, "parsed LoTW confirmation source")
    }

    private fun importedEvidenceFeedsAwardsCenterWithoutQsoMutation() {
        val store = InMemoryAwardEvidenceSnapshotStore()
        val repo = PersistedAwardEvidenceRepository(store)
        val adapter = AwardAdifEnrichmentAdapter()
        val qsos = mutableListOf<QsoRecord>()

        for (n in 1..6) {
            val id = 100L + n
            val continent = listOf("AF", "AS", "EU", "NA", "OC", "SA")[n - 1]
            val qso = qso(id)
            qsos += qso
            adapter.importRecord(
                repository = repo,
                qsoId = id,
                record = mapOf(
                    "CONT" to continent,
                    "LOTW_QSL_RCVD" to "Y",
                ),
                source = importSource(reference = "record-$n"),
            )
        }

        val before = qsos.map { it.copy() }
        val snapshot = repo.snapshot()
        val cards = AwardsCenterProjectionService().project(
            qsos = qsos,
            targets = AwardTargetEvidenceIndex(snapshot.targets),
            confirmations = snapshot.confirmations,
            sponsorStandings = snapshot.sponsorStandings,
        )
        val wac = cards.single { it.awardId == "IARU_WAC_BASIC" }

        eq(6, wac.confirmedCount, "persisted imported continents feed WAC")
        eq(true, wac.localThresholdMet, "persisted imported WAC threshold")
        eq(10000, wac.progress.basisPoints, "persisted imported WAC progress")
        eq(before, qsos, "award import/projection does not mutate authoritative QSOs")

        val restored = PersistedAwardEvidenceRepository(store).snapshot()
        eq(snapshot, restored, "Awards Center evidence survives repository reconstruction")
    }

    private fun malformedExplicitAwardFieldsFailClosed() {
        val adapter = AwardAdifEnrichmentAdapter()

        expectFailure("malformed explicit DXCC fails") {
            adapter.fromRecord(
                200,
                mapOf("DXCC" to "United States"),
                importSource(),
            )
        }
        expectFailure("malformed explicit state fails") {
            adapter.fromRecord(
                201,
                mapOf("STATE" to "Oregon"),
                importSource(),
            )
        }
        expectFailure("malformed explicit continent fails") {
            adapter.fromRecord(
                202,
                mapOf("CONT" to "Atlantis"),
                importSource(),
            )
        }
        expectFailure("malformed explicit IOTA fails") {
            adapter.fromRecord(
                203,
                mapOf("IOTA" to "NA65"),
                importSource(),
            )
        }
        expectFailure("malformed explicit POTA fails") {
            adapter.fromRecord(
                204,
                mapOf("POTA_REF" to "not a park"),
                importSource(),
            )
        }
        expectFailure("invalid import source URL fails") {
            AwardAdifImportSource(
                sourceId = "ADIF",
                sourceVersion = "1",
                sourceUrl = "http://example.invalid/file.adi",
            )
        }
    }

    private fun target(
        qsoId: Long,
        kind: OfficialAwardTargetKind,
        value: String,
        sourceId: String = "TEST_SOURCE",
        reference: String = "record-$qsoId",
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = kind,
        value = value,
        provenance = AwardTargetProvenance(
            sourceId = sourceId,
            sourceVersion = "v1",
            sourceUrl = "https://example.invalid/evidence",
            reference = reference,
            retrievedAtUtcMillis = 123L,
        ),
    )

    private fun importSource(reference: String? = "record-1") = AwardAdifImportSource(
        sourceId = "ADIF_FILE",
        sourceVersion = "3.1.6-fixture",
        sourceUrl = "https://example.invalid/source.adi",
        retrievedAtUtcMillis = 777L,
        recordReference = reference,
    )

    private fun qso(id: Long) = QsoRecord(
        id = id,
        call = "W1AW",
        stationCallsign = "N0PNG",
        qsoDate = "20261006",
        timeOn = "120000",
        band = "20m",
        mode = "CW",
    )
}
