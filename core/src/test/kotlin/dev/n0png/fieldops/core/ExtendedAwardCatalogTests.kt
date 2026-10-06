package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.QsoRecord
import java.net.URI

object ExtendedAwardCatalogTests {
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
        catalogVersionAndOfficialSources()
        vuccRulesMatchOfficialBandThresholds()
        ffmaRulesAndUniverseAreExact()
        gridNormalizerIsConservative()
        gridEvidenceIsSingleValued()
        explicitAdifGridImportPreservesProvenance()
        requiredBandFilterIsEvaluatorRuleNotUiConvention()
        vucc50ProgressRequiresConfirmed6mGrids()
        vucc432UsesFiftyGridThreshold()
        ffmaRequiresAll488Confirmed6mGrids()
        dateFloorIsEnforced()
        restrictedBandDefinitionFailsClosed()
        println("CP-0006G extended award catalog tests: PASS assertions=$assertions")
    }

    private fun catalogVersionAndOfficialSources() {
        eq("2026-10-06.2", OfficialAwardCatalog.CATALOG_VERSION, "expanded catalog version")
        checkThat(OfficialAwardCatalog.entries.size >= 11, "expanded catalog has at least eleven entries")

        val newIds = setOf(
            "ARRL_VUCC_50MHZ",
            "ARRL_VUCC_144MHZ",
            "ARRL_VUCC_432MHZ",
            "ARRL_FFMA",
        )
        newIds.forEach { id ->
            val entry = OfficialAwardCatalog.require(id)
            eq("ARRL", entry.issuer, "$id issuer")
            checkThat(entry.sources.isNotEmpty(), "$id official sources present")
            checkThat(URI(entry.informationUrl).host == "www.arrl.org", "$id info host is ARRL")
            entry.sources.forEach {
                eq("www.arrl.org", URI(it.url).host, "$id source host is ARRL")
                eq("2026-10-06", it.retrievedOn, "$id source retrieval date")
            }
        }

        val vuccSource = OfficialAwardCatalog.require("ARRL_VUCC_50MHZ").sources
            .single { it.role == OfficialAwardSourceRole.RULES }
        checkThat(vuccSource.url.endsWith("VUCC-Rules-July-2019.pdf"), "VUCC official rules PDF pinned")
        checkThat(vuccSource.versionLabel!!.contains("July 2019"), "VUCC rule version retained")

        val ffmaSource = OfficialAwardCatalog.require("ARRL_FFMA").sources
            .single { it.role == OfficialAwardSourceRole.RULES }
        eq("https://www.arrl.org/FFMA", ffmaSource.url, "FFMA official source")
    }

    private fun vuccRulesMatchOfficialBandThresholds() {
        val six = OfficialAwardCatalog.require("ARRL_VUCC_50MHZ")
        eq(OfficialAwardTargetKind.MAIDENHEAD_GRID4, six.requirement.targetKind, "50 MHz target kind")
        eq(100, six.requirement.requiredDistinctTargets, "50 MHz threshold")
        eq(setOf("6m"), six.requirement.requiredBands, "50 MHz required FieldOps band")
        eq("1983-01-01", six.requirement.notBeforeUtcDate, "50 MHz date floor")
        eq(AwardThresholdBasis.CONFIRMED, six.requirement.thresholdBasis, "50 MHz threshold basis")
        eq(OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION, six.requirement.confirmationPolicy, "50 MHz confirmation")
        checkThat(six.requirement.requiredModeGroups.isEmpty(), "VUCC has no mode endorsement requirement")
        checkThat(six.requirement.additionalConditions.any { it.contains("200 km") }, "VUCC 200 km condition retained")
        checkThat(six.requirement.additionalConditions.any { it.contains("active repeaters") }, "VUCC repeater condition retained")

        val two = OfficialAwardCatalog.require("ARRL_VUCC_144MHZ")
        eq(100, two.requirement.requiredDistinctTargets, "144 MHz threshold")
        eq(setOf("2m"), two.requirement.requiredBands, "144 MHz required FieldOps band")

        val seventy = OfficialAwardCatalog.require("ARRL_VUCC_432MHZ")
        eq(50, seventy.requirement.requiredDistinctTargets, "432 MHz threshold")
        eq(setOf("70cm"), seventy.requirement.requiredBands, "432 MHz required FieldOps band")
    }

    private fun ffmaRulesAndUniverseAreExact() {
        val ffma = OfficialAwardCatalog.require("ARRL_FFMA")
        eq(OfficialAwardTargetKind.MAIDENHEAD_GRID4, ffma.requirement.targetKind, "FFMA target kind")
        eq(488, ffma.requirement.requiredDistinctTargets, "FFMA threshold")
        eq(488, ffma.requirement.targetUniverse!!.size, "FFMA exact target universe size")
        eq(setOf("6m"), ffma.requirement.requiredBands, "FFMA 6m only")
        eq("1983-01-01", ffma.requirement.notBeforeUtcDate, "FFMA date floor")
        eq(AwardThresholdBasis.CONFIRMED, ffma.requirement.thresholdBasis, "FFMA confirmed threshold")
        checkThat("CM79" in ffma.requirement.targetUniverse!!, "FFMA includes CM79")
        checkThat("EM00" in ffma.requirement.targetUniverse!!, "FFMA includes EM00")
        checkThat("EM99" in ffma.requirement.targetUniverse!!, "FFMA includes EM99")
        checkThat("FN67" in ffma.requirement.targetUniverse!!, "FFMA includes FN67")
        checkThat("FN68" !in ffma.requirement.targetUniverse!!, "FFMA excludes non-listed FN68")
        checkThat(ffma.requirement.additionalConditions.any { it.contains("no progress tiers", ignoreCase = true) }, "FFMA all-or-nothing retained")
        checkThat(ffma.requirement.additionalConditions.any { it.contains("200 km") }, "FFMA 200 km condition retained")
        checkThat(ffma.requirement.additionalConditions.any { it.contains("direct initiation") }, "FFMA direct-initiation condition retained")
    }

    private fun gridNormalizerIsConservative() {
        eq(
            "FN31",
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "fn31"),
            "four-character grid normalized",
        )
        eq(
            "FN31",
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN31pr"),
            "six-character grid reduced to official four-character locator",
        )
        eq(
            "FN31",
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN31PR12"),
            "eight-character grid reduced to official four-character locator",
        )

        expectFailure("invalid Maidenhead field rejected") {
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "ZZ99")
        }
        expectFailure("incomplete Maidenhead locator rejected") {
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN3")
        }
        expectFailure("arbitrary text is not a grid") {
            AwardTargetNormalizer.normalize(OfficialAwardTargetKind.MAIDENHEAD_GRID4, "Oregon")
        }
    }

    private fun gridEvidenceIsSingleValued() {
        val one = gridEvidence(1, "FN31", "SOURCE_A")
        val same = gridEvidence(1, "FN31PR", "SOURCE_B")
        val index = AwardTargetEvidenceIndex(listOf(one, same))
        eq(setOf("FN31"), index.values(1, OfficialAwardTargetKind.MAIDENHEAD_GRID4), "equivalent explicit grids coalesce")

        expectFailure("one immutable QSO cannot have conflicting remote four-character grids") {
            AwardTargetEvidenceIndex(
                listOf(
                    gridEvidence(2, "FN31", "SOURCE_A"),
                    gridEvidence(2, "FN32", "SOURCE_B"),
                )
            )
        }
    }

    private fun explicitAdifGridImportPreservesProvenance() {
        val adapter = AwardAdifEnrichmentAdapter()
        val source = AwardAdifImportSource(
            sourceId = "LOTW_ADIF_FIXTURE",
            sourceVersion = "3.1.6-fixture",
            sourceUrl = "https://example.invalid/lotw.adi",
            retrievedAtUtcMillis = 1234L,
            recordReference = "record-77",
        )

        val batch = adapter.fromRecord(
            qsoId = 77,
            record = mapOf(
                "CALL" to "K1ABC",
                "GRIDSQUARE" to "FN31pr",
                "MY_GRIDSQUARE" to "JO40",
            ),
            source = source,
        )

        val grid = batch.targets.single()
        eq(OfficialAwardTargetKind.MAIDENHEAD_GRID4, grid.kind, "GRIDSQUARE import kind")
        eq("FN31", grid.normalizedValue, "GRIDSQUARE import normalized value")
        eq("LOTW_ADIF_FIXTURE", grid.provenance.sourceId, "grid provenance source")
        eq("3.1.6-fixture", grid.provenance.sourceVersion, "grid provenance version")
        eq("record-77", grid.provenance.reference, "grid provenance record")
        eq(1, batch.targets.size, "MY_GRIDSQUARE is not imported as remote award grid")
    }

    private fun requiredBandFilterIsEvaluatorRuleNotUiConvention() {
        val entry = OfficialAwardCatalog.require("ARRL_VUCC_50MHZ")
        val qsos = listOf(
            qso(100, "6m"),
            qso(101, "2m"),
            qso(102, "70cm"),
        )
        val targets = AwardTargetEvidenceIndex(
            listOf(
                gridEvidence(100, "AA00"),
                gridEvidence(101, "AA01"),
                gridEvidence(102, "AA02"),
            )
        )
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val progress = OfficialAwardProgressEngine().evaluate(entry, qsos, targets, confirmations)
        eq(setOf("AA00"), progress.confirmedTargets, "50 MHz entry excludes other bands without UI filter")
        eq(1, progress.confirmedCount, "required band contributes one target")

        val mismatchedView = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos,
            targets,
            confirmations,
            AwardEvaluationFilter(band = "2m"),
        )
        eq(0, mismatchedView.confirmedCount, "user 2m view cannot bypass official 6m required band")
    }

    private fun vucc50ProgressRequiresConfirmed6mGrids() {
        val entry = OfficialAwardCatalog.require("ARRL_VUCC_50MHZ")
        val qsos = mutableListOf<QsoRecord>()
        val evidence = mutableListOf<AwardTargetEvidence>()
        val confirmations = mutableListOf<AwardConfirmationEvidence>()

        for (n in 0 until 100) {
            val id = 200L + n
            val grid = "AA" + n.toString().padStart(2, '0')
            qsos += qso(id, "6m")
            evidence += gridEvidence(id, grid)
            if (n < 99) confirmations += AwardConfirmationEvidence(id, "LOTW")
        }

        val incomplete = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations,
        )
        eq(100, incomplete.workedCount, "VUCC 50 worked grids")
        eq(99, incomplete.confirmedCount, "VUCC 50 confirmed grids")
        eq(false, incomplete.thresholdMet, "99 confirmed grids do not meet VUCC 50")

        confirmations += AwardConfirmationEvidence(299, "LOTW")
        val complete = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations,
        )
        eq(100, complete.confirmedCount, "VUCC 50 all grids confirmed")
        eq(true, complete.thresholdMet, "100 confirmed 6m grids meet local VUCC threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in complete.states, "VUCC local threshold is not sponsor claimability")
    }

    private fun vucc432UsesFiftyGridThreshold() {
        val entry = OfficialAwardCatalog.require("ARRL_VUCC_432MHZ")
        val qsos = (0 until 50).map { n -> qso(400L + n, "70cm") }
        val evidence = (0 until 50).map { n ->
            gridEvidence(400L + n, "AB" + n.toString().padStart(2, '0'))
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val progress = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations,
        )
        eq(50, progress.confirmedCount, "432 MHz VUCC confirmed count")
        eq(true, progress.thresholdMet, "50 confirmed 70cm grids meet local 432 MHz VUCC threshold")
    }

    private fun ffmaRequiresAll488Confirmed6mGrids() {
        val entry = OfficialAwardCatalog.require("ARRL_FFMA")
        val universe = entry.requirement.targetUniverse!!.sorted()
        val qsos = universe.mapIndexed { index, _ -> qso(1000L + index, "6m") }
        val evidence = universe.mapIndexed { index, grid ->
            gridEvidence(1000L + index, grid)
        }
        val confirmations = qsos.dropLast(1).map { AwardConfirmationEvidence(it.id, "LOTW") }

        val incomplete = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos,
            AwardTargetEvidenceIndex(evidence),
            confirmations,
        )
        eq(488, incomplete.workedCount, "FFMA all required grids worked")
        eq(487, incomplete.confirmedCount, "FFMA one grid unconfirmed")
        eq(1, incomplete.remainingTargets!!.size, "FFMA one required grid remains")
        eq(false, incomplete.thresholdMet, "FFMA is all-or-nothing")

        val allConfirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }
        val complete = OfficialAwardProgressEngine().evaluate(
            entry,
            qsos + qso(2000, "6m"),
            AwardTargetEvidenceIndex(evidence + gridEvidence(2000, "FN68")),
            allConfirmations + AwardConfirmationEvidence(2000, "LOTW"),
        )
        eq(488, complete.confirmedCount, "non-FFMA grid does not inflate FFMA target count")
        eq(emptySet<String>(), complete.remainingTargets, "all 488 required FFMA grids complete")
        eq(true, complete.thresholdMet, "all 488 confirmed 6m grids meet local FFMA threshold")
        checkThat(AwardProgressState.OFFICIALLY_CLAIMABLE !in complete.states, "FFMA local threshold is not sponsor claimability")
    }

    private fun dateFloorIsEnforced() {
        val entry = OfficialAwardCatalog.require("ARRL_VUCC_144MHZ")
        val qsos = listOf(
            qso(3000, "2m", date = "19821231"),
            qso(3001, "2m", date = "19830101"),
        )
        val evidence = AwardTargetEvidenceIndex(
            listOf(
                gridEvidence(3000, "AC00"),
                gridEvidence(3001, "AC01"),
            )
        )
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val progress = OfficialAwardProgressEngine().evaluate(entry, qsos, evidence, confirmations)
        eq(setOf("AC01"), progress.confirmedTargets, "VUCC 1983 date floor inclusive")
    }

    private fun restrictedBandDefinitionFailsClosed() {
        expectFailure("same band cannot be required and excluded") {
            OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.MAIDENHEAD_GRID4,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 1,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                requiredBands = setOf("6m"),
                excludedBands = setOf("6M"),
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
            )
        }
        expectFailure("blank required band rejected") {
            OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.MAIDENHEAD_GRID4,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 1,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                requiredBands = setOf(" "),
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
            )
        }
    }

    private fun gridEvidence(
        qsoId: Long,
        grid: String,
        sourceId: String = "GRID_FIXTURE",
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = OfficialAwardTargetKind.MAIDENHEAD_GRID4,
        value = grid,
        provenance = AwardTargetProvenance(
            sourceId = sourceId,
            sourceVersion = "v1",
            sourceUrl = "https://example.invalid/grid-fixture",
            reference = "qso-$qsoId",
            retrievedAtUtcMillis = 1L,
        ),
    )

    private fun qso(
        id: Long,
        band: String,
        date: String = "20261006",
    ) = QsoRecord(
        id = id,
        call = "K1ABC",
        stationCallsign = "N0PNG",
        qsoDate = date,
        timeOn = "120000",
        band = band,
        mode = "CW",
    )
}
