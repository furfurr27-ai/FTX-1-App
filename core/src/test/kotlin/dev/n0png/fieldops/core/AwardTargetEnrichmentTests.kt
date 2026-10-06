package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.QsoRecord

object AwardTargetEnrichmentTests {
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
        provenanceAndNormalizationAreExplicit()
        conflictingSingleValuedEvidenceFailsClosed()
        noEvidenceMeansNoCallsignGuessing()
        dxccUsesNormalizedEntitiesAndOfficialDateFloor()
        wasUsesFiftyStateUniverseDcAliasAndBandRule()
        wacUsesNormalizedContinentsAndViewFilters()
        potaSupportsMultipleReferencesWithoutInventingConfirmation()
        triplePlayRequiresEveryLotwConfirmedStateModeCell()
        iotaRequiresOneHundredConfirmedGroupsAndSevenContinents()
        sotaPointScoringRemainsExternal()
        evaluationNeverMutatesAuthoritativeQso()
        println("CP-0006C award target enrichment tests: PASS assertions=$assertions")
    }

    private fun provenanceAndNormalizationAreExplicit() {
        val provenance = AwardTargetProvenance(
            sourceId = "ADIF_IMPORT",
            sourceVersion = "2026-10-fixture",
            sourceUrl = "https://example.invalid/dataset",
            reference = "row-17",
            retrievedAtUtcMillis = 1_800_000_000_000L,
        )
        eq("ADIF_IMPORT", provenance.sourceId, "provenance source id")
        eq("2026-10-fixture", provenance.sourceVersion, "provenance version")
        eq("https://example.invalid/dataset", provenance.sourceUrl, "provenance URL")
        eq("row-17", provenance.reference, "provenance reference")

        eq(
            "291",
            evidence(1, OfficialAwardTargetKind.DXCC_ENTITY, "0291").normalizedValue,
            "DXCC numeric normalization",
        )
        eq(
            "OR",
            evidence(2, OfficialAwardTargetKind.US_STATE, "or").normalizedValue,
            "state normalization",
        )
        eq(
            "NORTH_AMERICA",
            evidence(3, OfficialAwardTargetKind.CONTINENT, "NA").normalizedValue,
            "ADIF continent normalization",
        )
        eq(
            "EU-005",
            evidence(4, OfficialAwardTargetKind.IOTA_GROUP, "eu-005").normalizedValue,
            "IOTA normalization",
        )
        eq(
            "US-0001",
            evidence(5, OfficialAwardTargetKind.POTA_REFERENCE, "us-0001").normalizedValue,
            "POTA normalization",
        )

        expectFailure("blank provenance source") {
            AwardTargetProvenance("", "v1")
        }
        expectFailure("blank provenance version") {
            AwardTargetProvenance("TEST", "")
        }
        expectFailure("non-HTTPS provenance URL") {
            AwardTargetProvenance("TEST", "v1", sourceUrl = "http://example.invalid")
        }
        expectFailure("invalid state target") {
            evidence(6, OfficialAwardTargetKind.US_STATE, "Oregon")
        }
        expectFailure("invalid continent target") {
            evidence(7, OfficialAwardTargetKind.CONTINENT, "Atlantis")
        }
        expectFailure("invalid IOTA target") {
            evidence(8, OfficialAwardTargetKind.IOTA_GROUP, "EU5")
        }
        expectFailure("composite target must not be direct evidence") {
            AwardTargetEvidence(
                qsoId = 9,
                kind = OfficialAwardTargetKind.MODE_STATE_PAIR,
                value = "OR|CW",
                provenance = provenance(),
            )
        }
    }

    private fun conflictingSingleValuedEvidenceFailsClosed() {
        expectFailure("one QSO cannot have conflicting states") {
            AwardTargetEvidenceIndex(
                listOf(
                    evidence(10, OfficialAwardTargetKind.US_STATE, "OR", source = "SOURCE_A"),
                    evidence(10, OfficialAwardTargetKind.US_STATE, "WA", source = "SOURCE_B"),
                )
            )
        }
        expectFailure("one QSO cannot have conflicting DXCC entities") {
            AwardTargetEvidenceIndex(
                listOf(
                    evidence(11, OfficialAwardTargetKind.DXCC_ENTITY, "291"),
                    evidence(11, OfficialAwardTargetKind.DXCC_ENTITY, "110"),
                )
            )
        }

        val multipleParks = AwardTargetEvidenceIndex(
            listOf(
                evidence(12, OfficialAwardTargetKind.POTA_REFERENCE, "US-0001"),
                evidence(12, OfficialAwardTargetKind.POTA_REFERENCE, "US-0002"),
            )
        )
        eq(
            setOf("US-0001", "US-0002"),
            multipleParks.values(12, OfficialAwardTargetKind.POTA_REFERENCE),
            "multiple POTA references per QSO remain legal",
        )

        val duplicateAgreement = AwardTargetEvidenceIndex(
            listOf(
                evidence(13, OfficialAwardTargetKind.CONTINENT, "EU", source = "SOURCE_A"),
                evidence(13, OfficialAwardTargetKind.CONTINENT, "EUROPE", source = "SOURCE_B"),
            )
        )
        eq(
            "EUROPE",
            duplicateAgreement.singleValue(13, OfficialAwardTargetKind.CONTINENT),
            "multiple provenance records may agree on one normalized value",
        )
        eq(
            2,
            duplicateAgreement.evidence(13, OfficialAwardTargetKind.CONTINENT).size,
            "agreeing provenance records remain available",
        )
    }

    private fun noEvidenceMeansNoCallsignGuessing() {
        val qso = qso(20, call = "DL1ABC")
        val result = OfficialAwardProgressEngine().evaluate(
            entry = OfficialAwardCatalog.require("ARRL_DXCC_MIXED"),
            qsos = listOf(qso),
            targets = AwardTargetEvidenceIndex(emptyList()),
            confirmations = listOf(AwardConfirmationEvidence(qso.id, "LOTW")),
        )

        eq(0, result.workedCount, "callsign prefix must not create DXCC evidence")
        eq(0, result.confirmedCount, "confirmation without target evidence cannot count")
        checkThat(result.states.isEmpty(), "no target evidence means no award state")
    }

    private fun dxccUsesNormalizedEntitiesAndOfficialDateFloor() {
        val qsos = mutableListOf<QsoRecord>()
        val targets = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()

        for (n in 1..100) {
            val id = 1000L + n
            qsos += qso(id, date = "20261006")
            targets += evidence(id, OfficialAwardTargetKind.DXCC_ENTITY, n.toString())
            confirmations += AwardConfirmationEvidence(id, if (n % 2 == 0) "LOTW" else "PAPER_QSL")
        }

        val tooOld = qso(1200, date = "19451114")
        qsos += tooOld
        targets += evidence(tooOld.id, OfficialAwardTargetKind.DXCC_ENTITY, "999")
        confirmations += AwardConfirmationEvidence(tooOld.id, "LOTW")

        val result = OfficialAwardProgressEngine().evaluate(
            OfficialAwardCatalog.require("ARRL_DXCC_MIXED"),
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        )

        eq(100, result.workedCount, "DXCC date floor excludes pre-rule QSO")
        eq(100, result.confirmedCount, "DXCC accepted confirmation sources")
        checkThat(result.thresholdMet, "100 confirmed DXCC entities meet local threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states, "DXCC local threshold is not sponsor claimability")
        checkThat("999" !in result.workedTargets, "pre-1945-11-15 DXCC evidence excluded")
        eq("1", result.contributions.first().targetValue, "DXCC target canonicalized")
        checkThat(result.contributions.all { it.targetProvenance.isNotEmpty() }, "DXCC contributions retain provenance")
    }

    private fun wasUsesFiftyStateUniverseDcAliasAndBandRule() {
        val was = OfficialAwardCatalog.require("ARRL_WAS_BASIC")
        val states = was.requirement.targetUniverse!!.sorted()
        val qsos = mutableListOf<QsoRecord>()
        val targetEvidence = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()

        states.forEachIndexed { index, state ->
            val id = 2000L + index
            qsos += qso(id, band = "20m")
            targetEvidence += evidence(
                id,
                OfficialAwardTargetKind.US_STATE,
                if (state == "MD") "DC" else state,
            )
            confirmations += AwardConfirmationEvidence(id, "LOTW")
        }

        val sixtyMeterExtra = qso(2100, band = "60m")
        qsos += sixtyMeterExtra
        targetEvidence += evidence(sixtyMeterExtra.id, OfficialAwardTargetKind.US_STATE, "OR")
        confirmations += AwardConfirmationEvidence(sixtyMeterExtra.id, "LOTW")

        val result = OfficialAwardProgressEngine().evaluate(
            was,
            qsos,
            AwardTargetEvidenceIndex(targetEvidence),
            confirmations,
        )

        eq(50, result.workedCount, "WAS counts exactly fifty states")
        eq(50, result.confirmedCount, "WAS confirmed state count")
        checkThat(result.thresholdMet, "WAS threshold met")
        checkThat("MD" in result.confirmedTargets, "DC evidence maps to Maryland for WAS")
        checkThat("DC" !in result.confirmedTargets, "DC is not retained as separate WAS target")
        eq(emptySet<String>(), result.remainingTargets, "WAS remaining state set")
        eq(
            50,
            result.contributions.count { it.band == "20m" },
            "60m QSO excluded before contribution",
        )
    }

    private fun wacUsesNormalizedContinentsAndViewFilters() {
        val codes = listOf("AF", "AS", "EU", "NA", "OC", "SA")
        val qsos = codes.mapIndexed { index, _ ->
            qso(3000L + index, band = if (index == 5) "40m" else "20m")
        }
        val targets = codes.mapIndexed { index, code ->
            evidence(3000L + index, OfficialAwardTargetKind.CONTINENT, code)
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }
        val engine = OfficialAwardProgressEngine()

        val all = engine.evaluate(
            OfficialAwardCatalog.require("IARU_WAC_BASIC"),
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
        )
        eq(6, all.confirmedCount, "WAC six normalized continents")
        checkThat(all.thresholdMet, "WAC local threshold met")
        checkThat("NORTH_AMERICA" in all.confirmedTargets, "ADIF NA maps to North America")
        checkThat("OCEANIA" in all.confirmedTargets, "ADIF OC maps to Oceania")

        val twenty = engine.evaluate(
            OfficialAwardCatalog.require("IARU_WAC_BASIC"),
            qsos,
            AwardTargetEvidenceIndex(targets),
            confirmations,
            AwardEvaluationFilter(band = "20M"),
        )
        eq(5, twenty.confirmedCount, "band-specific WAC view")
        checkThat(!twenty.thresholdMet, "20m-only view is not complete")
        eq(setOf("SOUTH_AMERICA"), twenty.remainingTargets, "band view remaining continent")
    }

    private fun potaSupportsMultipleReferencesWithoutInventingConfirmation() {
        val qsos = (0..8).map { index -> qso(4000L + index) }
        val evidence = mutableListOf<AwardTargetEvidence>()
        evidence += evidence(4000, OfficialAwardTargetKind.POTA_REFERENCE, "US-0001")
        evidence += evidence(4000, OfficialAwardTargetKind.POTA_REFERENCE, "US-0002")
        for (index in 1..8) {
            evidence += evidence(
                4000L + index,
                OfficialAwardTargetKind.POTA_REFERENCE,
                "US-" + (index + 2).toString().padStart(4, '0'),
            )
        }

        val result = OfficialAwardProgressEngine().evaluate(
            OfficialAwardCatalog.require("POTA_BRONZE_HUNTER"),
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") },
        )

        eq(10, result.workedCount, "POTA multiple references on one QSO count separately")
        eq(0, result.confirmedCount, "program-validated policy is not forged from LoTW confirmations")
        checkThat(result.thresholdMet, "POTA local worked threshold can be reached")
        checkThat(AwardProgressState.CONFIRMED !in result.states, "POTA sponsor validation is not fabricated")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in result.states, "automatic POTA issue is not local claimability")
        eq(
            2,
            result.contributions.count { it.qsoId == 4000L },
            "one QSO may contribute multiple POTA references",
        )
    }

    private fun triplePlayRequiresEveryLotwConfirmedStateModeCell() {
        val triple = OfficialAwardCatalog.require("ARRL_TRIPLE_PLAY_WAS")
        val states = triple.requirement.requiredCoverage.sorted()
        val qsos = mutableListOf<QsoRecord>()
        val evidence = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()
        var id = 5000L
        var paperOnlyId = -1L

        for (state in states) {
            for (mode in listOf(AwardModeGroup.CW, AwardModeGroup.PHONE, AwardModeGroup.DIGITAL)) {
                val currentId = id++
                qsos += when (mode) {
                    AwardModeGroup.CW -> qso(currentId, mode = "CW", date = "20260101")
                    AwardModeGroup.PHONE -> qso(currentId, mode = "SSB", date = "20260101")
                    AwardModeGroup.DIGITAL -> qso(currentId, mode = "MFSK", submode = "FT8", date = "20260101")
                    AwardModeGroup.UNCLASSIFIED -> error("not used")
                }
                evidence += evidence(
                    currentId,
                    OfficialAwardTargetKind.US_STATE,
                    if (state == "MD") "DC" else state,
                )
                if (state == "WY" && mode == AwardModeGroup.DIGITAL) {
                    paperOnlyId = currentId
                    confirmations += AwardConfirmationEvidence(currentId, "PAPER_QSL")
                } else {
                    confirmations += AwardConfirmationEvidence(currentId, "LOTW")
                }
            }
        }

        val pre2009 = qso(id++, mode = "CW", date = "20081231")
        qsos += pre2009
        evidence += evidence(pre2009.id, OfficialAwardTargetKind.US_STATE, "OR")
        confirmations += AwardConfirmationEvidence(pre2009.id, "LOTW")

        val sixty = qso(id++, mode = "CW", date = "20260101", band = "60m")
        qsos += sixty
        evidence += evidence(sixty.id, OfficialAwardTargetKind.US_STATE, "OR")
        confirmations += AwardConfirmationEvidence(sixty.id, "LOTW")

        val engine = OfficialAwardProgressEngine()
        val almost = engine.evaluate(
            triple,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations,
        )

        eq(150, almost.composite!!.requiredCells.size, "Triple Play required matrix size")
        eq(150, almost.composite!!.workedCells.size, "Triple Play worked matrix size")
        eq(149, almost.composite!!.confirmedCells.size, "paper QSL does not satisfy LoTW-only cell")
        eq(1, almost.composite!!.missingCells.size, "one Triple Play cell missing")
        checkThat("WY|DIGITAL" in almost.composite!!.missingCells, "missing Triple Play cell identity")
        checkThat(!almost.thresholdMet, "149 LoTW cells do not meet Triple Play threshold")
        checkThat("MD|CW" in almost.composite!!.confirmedCells, "DC evidence maps to Maryland matrix cell")

        val completed = engine.evaluate(
            triple,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations + AwardConfirmationEvidence(paperOnlyId, "ARRL_LOTW"),
        )
        eq(150, completed.composite!!.confirmedCells.size, "all Triple Play cells LoTW-confirmed")
        checkThat(completed.composite!!.missingCells.isEmpty(), "no Triple Play cells missing")
        checkThat(completed.thresholdMet, "complete Triple Play matrix meets local threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in completed.states, "Triple Play local matrix does not assert sponsor claimability")
    }

    private fun iotaRequiresOneHundredConfirmedGroupsAndSevenContinents() {
        val iota = OfficialAwardCatalog.require("IOTA_100")
        val continentCodes = listOf("AF", "AN", "AS", "EU", "NA", "OC", "SA")
        val qsos = mutableListOf<QsoRecord>()
        val evidenceWithoutAntarctica = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()
        var antarcticaQsoId = -1L

        for (n in 1..100) {
            val id = 6000L + n
            val code = if (n <= continentCodes.size) continentCodes[n - 1] else "EU"
            val group = code + "-" + n.toString().padStart(3, '0')
            qsos += qso(id, date = "20260101")
            evidenceWithoutAntarctica += evidence(id, OfficialAwardTargetKind.IOTA_GROUP, group)
            if (code != "AN") {
                evidenceWithoutAntarctica += evidence(id, OfficialAwardTargetKind.CONTINENT, code)
            } else {
                antarcticaQsoId = id
            }
            confirmations += AwardConfirmationEvidence(id, "LOTW")
        }

        val engine = OfficialAwardProgressEngine()
        val missingCoverage = engine.evaluate(
            iota,
            qsos,
            AwardTargetEvidenceIndex(evidenceWithoutAntarctica),
            confirmations,
        )

        eq(100, missingCoverage.confirmedCount, "IOTA confirmed group count")
        eq(6, missingCoverage.composite!!.achievedCoverage.size, "IOTA six supplied continents")
        eq(setOf("ANTARCTICA"), missingCoverage.composite!!.missingCoverage, "IOTA missing Antarctica coverage")
        checkThat(!missingCoverage.thresholdMet, "100 groups without seven-continent coverage is incomplete")

        val completeEvidence = evidenceWithoutAntarctica + evidence(
            antarcticaQsoId,
            OfficialAwardTargetKind.CONTINENT,
            "AN",
        )
        val complete = engine.evaluate(
            iota,
            qsos,
            AwardTargetEvidenceIndex(completeEvidence),
            confirmations,
        )

        eq(100, complete.confirmedCount, "IOTA complete confirmed groups")
        eq(7, complete.composite!!.achievedCoverage.size, "IOTA seven-continent coverage")
        checkThat(complete.composite!!.missingCoverage.isEmpty(), "IOTA coverage complete")
        checkThat(complete.thresholdMet, "IOTA 100 plus coverage meets local threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in complete.states, "IOTA threshold does not assert sponsor claimability")
    }

    private fun sotaPointScoringRemainsExternal() {
        expectFailure("SOTA scoring must stay external") {
            OfficialAwardProgressEngine().evaluate(
                OfficialAwardCatalog.require("SOTA_SHACK_SLOTH_1000"),
                listOf(qso(7000)),
                AwardTargetEvidenceIndex(emptyList()),
            )
        }
    }

    private fun evaluationNeverMutatesAuthoritativeQso() {
        val original = qso(
            id = 8000,
            call = "K1ABC",
            band = "40m",
            mode = "MFSK",
            submode = "FT8",
            date = "20261006",
        )
        val before = original.copy()
        val result = OfficialAwardProgressEngine().evaluate(
            OfficialAwardCatalog.require("IARU_WAC_BASIC"),
            listOf(original),
            AwardTargetEvidenceIndex(
                listOf(evidence(original.id, OfficialAwardTargetKind.CONTINENT, "NA"))
            ),
            listOf(AwardConfirmationEvidence(original.id, "LOTW")),
        )

        eq(before, original, "award evaluation must not rewrite QSO")
        eq("MFSK", result.contributions.single().exactMode, "exact mode preserved in contribution")
        eq("FT8", result.contributions.single().exactSubmode, "exact submode preserved in contribution")
        eq(AwardModeGroup.DIGITAL, result.contributions.single().modeGroup, "controlled mode group derived separately")
        eq("TEST_DATASET", result.contributions.single().targetProvenance.single().sourceId, "target provenance preserved")
    }

    private fun provenance(source: String = "TEST_DATASET") = AwardTargetProvenance(
        sourceId = source,
        sourceVersion = "fixture-v1",
        sourceUrl = "https://example.invalid/fixture",
        reference = "synthetic",
    )

    private fun evidence(
        qsoId: Long,
        kind: OfficialAwardTargetKind,
        value: String,
        source: String = "TEST_DATASET",
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = kind,
        value = value,
        provenance = provenance(source),
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
}
