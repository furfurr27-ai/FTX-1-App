package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.awards.*
import dev.n0png.fieldops.core.logbook.InMemoryLogbookRepository
import dev.n0png.fieldops.core.logbook.QsoRecord
import dev.n0png.fieldops.core.map.*

object AwardAreaMapProjectionTests {
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
        onlySupportedGeographicAwardsBecomeLayers()
        wasEmitsNeededWorkedAndConfirmedStates()
        noCallsSignInferenceOccurs()
        vuccDoesNotInventNeededGridIdentities()
        ffmaFiniteUniverseEmitsAll488Targets()
        thresholdStateIsLocalOverlayNotSponsorCredit()
        queryContextFiltersContributions()
        aggregationIsDeterministicAndClusteringReady()
        geometryBindingIsMetadataOnlyAndFailClosed()
        evidenceProvenanceSurvivesProjection()
        applicationServiceUsesLiveAuthoritativeRepositories()
        dateRangeFilteringIsPreserved()
        dcCanonicalizationMatchesAwardsEngine()
        emptyEvidenceIsSafe()
        invalidGeometryMetadataFailsClosed()
        println("CP-0007A award-area map projection tests: PASS assertions=$assertions")
    }

    private fun onlySupportedGeographicAwardsBecomeLayers() {
        val layers = AwardAreaMapProjectionService().project(
            qsos = emptyList(),
            evidence = AwardEvidenceSnapshot(),
        )
        val ids = layers.map { it.awardId }

        eq(
            listOf(
                "ARRL_FFMA",
                "ARRL_VUCC_144MHZ",
                "ARRL_VUCC_432MHZ",
                "ARRL_VUCC_50MHZ",
                "ARRL_WAS_BASIC",
            ),
            ids,
            "initial map projection includes only simple state/grid awards",
        )
        checkThat("ARRL_DXCC_MIXED" !in ids, "DXCC excluded until geometry/source work")
        checkThat("ARRL_TRIPLE_PLAY_WAS" !in ids, "Triple Play matrix not flattened into state map")
        checkThat("IOTA_100" !in ids, "IOTA composite coverage not flattened into map")
        checkThat("POTA_BRONZE_HUNTER" !in ids, "POTA not projected without explicit geometry")
        checkThat("SOTA_SHACK_SLOTH_1000" !in ids, "SOTA external scoring not projected")
    }

    private fun wasEmitsNeededWorkedAndConfirmedStates() {
        val qsos = listOf(
            qso(1, call = "K1AAA", band = "20m"),
            qso(2, call = "K2BBB", band = "40m"),
        )
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(1, OfficialAwardTargetKind.US_STATE, "OR"),
                target(2, OfficialAwardTargetKind.US_STATE, "WA"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(2, "LOTW", "confirmed"),
            ),
        )

        val layer = AwardAreaMapProjectionService()
            .project(qsos, evidence)
            .layer("ARRL_WAS_BASIC")

        eq(50, layer.targets.size, "WAS emits all 50 known state identities")
        eq(true, layer.targetUniverseComplete, "WAS target universe is finite")
        eq(49, layer.remainingToThresholdCount, "WAS threshold remainder uses confirmed basis")
        eq(false, layer.localThresholdMet, "partial WAS not threshold met")

        val or = layer.target("OR")
        eq(AwardAreaMapState.WORKED_UNCONFIRMED, or.primaryState, "OR worked unconfirmed")
        eq(listOf(1L), or.qsoIds, "OR QSO id")
        eq(emptyList<Long>(), or.confirmedQsoIds, "OR no confirmed qso")

        val wa = layer.target("WA")
        eq(AwardAreaMapState.CONFIRMED, wa.primaryState, "WA confirmed")
        eq(listOf(2L), wa.confirmedQsoIds, "WA confirmed qso id")
        eq(listOf("LOTW"), wa.confirmationSources, "WA confirmation source")

        val ak = layer.target("AK")
        eq(AwardAreaMapState.NEEDED, ak.primaryState, "AK needed")
        eq(emptyList<Long>(), ak.qsoIds, "needed target has no qso ids")
        eq(48, layer.neededCount, "WAS needed state count")
        eq(1, layer.workedUnconfirmedCount, "WAS worked-unconfirmed count")
        eq(1, layer.confirmedCount, "WAS confirmed target count")
    }

    private fun noCallsSignInferenceOccurs() {
        val qso = qso(10, call = "K7OREGON", band = "20m")
        val layers = AwardAreaMapProjectionService().project(
            qsos = listOf(qso),
            evidence = AwardEvidenceSnapshot(),
        )

        val was = layers.layer("ARRL_WAS_BASIC")
        eq(50, was.neededCount, "callsign alone does not create state progress")
        eq(0, was.workedUnconfirmedCount, "callsign alone no worked state")
        eq(0, was.confirmedCount, "callsign alone no confirmed state")

        val vucc = layers.layer("ARRL_VUCC_50MHZ")
        eq(0, vucc.targets.size, "callsign alone does not create grid identity")
        eq(100, vucc.remainingToThresholdCount, "VUCC remains zero-progress")
    }

    private fun vuccDoesNotInventNeededGridIdentities() {
        val qsos = listOf(
            qso(20, call = "K1AAA", band = "6m"),
            qso(21, call = "K2BBB", band = "6m"),
            qso(22, call = "K3CCC", band = "2m"),
        )
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(20, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN31"),
                target(21, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN32"),
                target(22, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "FN33"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(20, "LOTW"),
                AwardConfirmationEvidence(22, "LOTW"),
            ),
        )

        val layer = AwardAreaMapProjectionService()
            .project(qsos, evidence)
            .layer("ARRL_VUCC_50MHZ")

        eq(false, layer.targetUniverseComplete, "VUCC target universe is intentionally open")
        eq(2, layer.targets.size, "6m VUCC emits only explicitly known 6m grids")
        eq(setOf("FN31", "FN32"), layer.targets.mapTo(linkedSetOf()) { it.identity.targetValue }, "VUCC known grids")
        eq(99, layer.remainingToThresholdCount, "VUCC numeric remainder uses one confirmed grid")
        eq(0, layer.neededCount, "VUCC does not fabricate needed grid identities")
        checkThat(
            layer.warnings.any { it.contains("needed target identities are not fabricated") },
            "VUCC open-universe warning retained",
        )
        eq(AwardAreaMapState.CONFIRMED, layer.target("FN31").primaryState, "confirmed VUCC grid")
        eq(AwardAreaMapState.WORKED_UNCONFIRMED, layer.target("FN32").primaryState, "worked VUCC grid")
        expectFailure("2m-only grid is not present in 6m VUCC layer") {
            layer.target("FN33")
        }
    }

    private fun ffmaFiniteUniverseEmitsAll488Targets() {
        val qso = qso(30, call = "K1AAA", band = "6m")
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(target(30, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "CM79")),
            confirmations = listOf(AwardConfirmationEvidence(30, "LOTW")),
        )

        val layer = AwardAreaMapProjectionService()
            .project(listOf(qso), evidence)
            .layer("ARRL_FFMA")

        eq(true, layer.targetUniverseComplete, "FFMA universe complete")
        eq(488, layer.targets.size, "FFMA emits exact 488 map target identities")
        eq(487, layer.neededCount, "FFMA needed count after one confirmed")
        eq(1, layer.confirmedCount, "FFMA one confirmed")
        eq(487, layer.remainingToThresholdCount, "FFMA threshold remainder")
        eq(AwardAreaMapState.CONFIRMED, layer.target("CM79").primaryState, "CM79 confirmed")
        eq(AwardAreaMapState.NEEDED, layer.target("FN67").primaryState, "FN67 needed")
        checkThat(layer.targets.none { it.identity.targetValue == "FN68" }, "non-FFMA grid not emitted as needed")
    }

    private fun thresholdStateIsLocalOverlayNotSponsorCredit() {
        val states = OfficialAwardCatalog.require("ARRL_WAS_BASIC").requirement.targetUniverse!!.sorted()
        val qsos = states.mapIndexed { index, _ ->
            qso(100L + index, call = "K$index", band = "20m")
        }
        val targets = states.mapIndexed { index, state ->
            target(100L + index, OfficialAwardTargetKind.US_STATE, state)
        }
        val confirmations = qsos.map { AwardConfirmationEvidence(it.id, "LOTW") }

        val layer = AwardAreaMapProjectionService().project(
            qsos,
            AwardEvidenceSnapshot(targets = targets, confirmations = confirmations),
        ).layer("ARRL_WAS_BASIC")

        eq(true, layer.localThresholdMet, "complete WAS local threshold")
        eq(0, layer.remainingToThresholdCount, "complete WAS threshold remainder")
        checkThat(
            layer.targets.all { AwardAreaMapState.LOCAL_THRESHOLD_MET in it.states },
            "local threshold overlay appears on every emitted award target",
        )
        checkThat(
            layer.targets.all { it.primaryState == AwardAreaMapState.CONFIRMED },
            "all complete WAS targets remain confirmed primary state",
        )
        checkThat(
            layer.warnings.any { it.contains("not sponsor claimability") },
            "map layer explicitly separates local threshold from sponsor claimability",
        )
    }

    private fun queryContextFiltersContributions() {
        val qsos = listOf(
            qso(200, "K1AAA", "20m", "CW", null, "20260101"),
            qso(201, "K2BBB", "40m", "MFSK", "FT8", "20260301"),
            qso(202, "K3CCC", "40m", "CW", null, "20260401"),
        )
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(200, OfficialAwardTargetKind.US_STATE, "OR"),
                target(201, OfficialAwardTargetKind.US_STATE, "WA"),
                target(202, OfficialAwardTargetKind.US_STATE, "CA"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(200, "LOTW"),
                AwardConfirmationEvidence(201, "LOTW"),
                AwardConfirmationEvidence(202, "LOTW"),
            ),
        )
        val query = AwardsCenterQuery(
            modeView = AwardsCenterModeView.DIGITAL,
            band = "40M",
            dateRange = AwardsCenterDateRange("2026-02-01", "2026-12-31"),
        )

        val layer = AwardAreaMapProjectionService().project(qsos, evidence, query).layer("ARRL_WAS_BASIC")
        eq(query, layer.query, "map layer preserves query object")
        eq(AwardAreaMapState.CONFIRMED, layer.target("WA").primaryState, "matching digital 40m WA retained")
        eq(AwardAreaMapState.NEEDED, layer.target("OR").primaryState, "pre-range CW OR excluded")
        eq(AwardAreaMapState.NEEDED, layer.target("CA").primaryState, "CW CA excluded from digital view")
        eq(49, layer.remainingToThresholdCount, "query-specific map progress remainder")
    }

    private fun aggregationIsDeterministicAndClusteringReady() {
        val qsos = listOf(
            qso(300, "K1AAA", "20m", "CW"),
            qso(301, "K2BBB", "40m", "MFSK", "FT8"),
            qso(302, "K3CCC", "20m", "SSB"),
        )
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(302, OfficialAwardTargetKind.US_STATE, "OR", sourceId = "C"),
                target(300, OfficialAwardTargetKind.US_STATE, "OR", sourceId = "A"),
                target(301, OfficialAwardTargetKind.US_STATE, "OR", sourceId = "B"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(301, "LOTW"),
                AwardConfirmationEvidence(302, "PAPER"),
            ),
        )

        val a = AwardAreaMapProjectionService().project(qsos, evidence).layer("ARRL_WAS_BASIC").target("OR")
        val b = AwardAreaMapProjectionService().project(qsos.reversed(), evidence).layer("ARRL_WAS_BASIC").target("OR")

        eq(AwardAreaMapState.CONFIRMED, a.primaryState, "any accepted confirmation makes target confirmed")
        eq(3, a.workedQsoCount, "aggregate worked qso count")
        eq(2, a.confirmedQsoCount, "aggregate confirmed qso count")
        eq(listOf(300L, 301L, 302L), a.qsoIds, "aggregate qso ids sorted")
        eq(listOf(301L, 302L), a.confirmedQsoIds, "confirmed qso ids sorted")
        eq(listOf("20m", "40m"), a.bands, "aggregate bands sorted")
        eq(
            listOf(AwardModeGroup.CW, AwardModeGroup.DIGITAL, AwardModeGroup.PHONE),
            a.modeGroups,
            "aggregate mode groups deterministic",
        )
        eq(listOf("CW", "MFSK/FT8", "SSB"), a.exactModes, "exact mode/submode labels retained")
        eq(listOf("LOTW", "PAPER"), a.confirmationSources, "confirmation sources sorted")
        eq(a, b, "projection deterministic regardless qso iteration order")
    }

    private fun geometryBindingIsMetadataOnlyAndFailClosed() {
        val qso = qso(400, "K1AAA", "20m")
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(target(400, OfficialAwardTargetKind.US_STATE, "OR"))
        )
        val identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "OR")
        val source = AwardAreaGeometrySource(
            sourceId = "OFFLINE_STATES",
            sourceVersion = "2026.1",
            sourceUrl = "https://example.invalid/states",
            licenseLabel = "fixture-license",
            retrievedOn = "2026-10-06",
        )
        val catalog = AwardAreaGeometryCatalog { requested ->
            if (requested == identity) {
                AwardAreaGeometryBinding(
                    identity = requested,
                    source = source,
                    geometryAssetId = "states/OR",
                )
            } else {
                null
            }
        }

        val or = AwardAreaMapProjectionService()
            .project(listOf(qso), evidence, geometryCatalog = catalog)
            .layer("ARRL_WAS_BASIC")
            .target("OR")

        eq("US_STATE:OR", or.identity.stableKey, "stable geometry identity")
        eq("states/OR", or.geometry!!.geometryAssetId, "geometry asset reference")
        eq("OFFLINE_STATES", or.geometry!!.source.sourceId, "geometry source id")
        eq("2026.1", or.geometry!!.source.sourceVersion, "geometry source version")

        val layer = AwardAreaMapProjectionService()
            .project(listOf(qso), evidence, geometryCatalog = catalog)
            .layer("ARRL_WAS_BASIC")
        eq(1, layer.geometryBoundCount, "only supplied OR geometry is bound")
        eq(49, layer.geometryUnboundCount, "other states remain unbound rather than fabricated")

        val badCatalog = AwardAreaGeometryCatalog {
            AwardAreaGeometryBinding(
                identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "WA"),
                source = source,
                geometryAssetId = "states/WA",
            )
        }
        expectFailure("geometry catalog cannot return a mismatched identity") {
            AwardAreaMapProjectionService().project(listOf(qso), evidence, geometryCatalog = badCatalog)
        }
    }

    private fun evidenceProvenanceSurvivesProjection() {
        val qso = qso(500, "K1AAA", "6m")
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(
                    500,
                    OfficialAwardTargetKind.MAIDENHEAD_GRID4,
                    "FN31PR",
                    sourceId = "LOTW_ADIF",
                    sourceVersion = "3.1.6",
                    reference = "record-500",
                )
            ),
            confirmations = listOf(AwardConfirmationEvidence(500, "LOTW")),
        )

        val target = AwardAreaMapProjectionService()
            .project(listOf(qso), evidence)
            .layer("ARRL_VUCC_50MHZ")
            .target("FN31")

        eq(1, target.targetProvenance.size, "grid target provenance preserved")
        eq("LOTW_ADIF", target.targetProvenance.single().sourceId, "provenance source id")
        eq("3.1.6", target.targetProvenance.single().sourceVersion, "provenance version")
        eq("record-500", target.targetProvenance.single().reference, "provenance reference")
    }

    private fun applicationServiceUsesLiveAuthoritativeRepositories() {
        val logbook = InMemoryLogbookRepository()
        val evidence = InMemoryAwardEvidenceRepository()
        val service = AwardsCenterApplicationService(logbook, evidence)

        val before = service.awardMapLayers().layer("ARRL_WAS_BASIC")
        eq(50, before.neededCount, "service map starts from empty authoritative repositories")

        logbook.save(qso(600, "K1AAA", "20m"))
        evidence.apply(
            AwardEvidenceBatch(
                targets = listOf(target(600, OfficialAwardTargetKind.US_STATE, "OR")),
                confirmations = listOf(AwardConfirmationEvidence(600, "LOTW")),
            )
        )

        val after = service.awardMapLayers().layer("ARRL_WAS_BASIC")
        eq(AwardAreaMapState.CONFIRMED, after.target("OR").primaryState, "service map sees later authoritative writes")
        eq(1, after.confirmedCount, "service map confirmed count updates")

        val card = service.cards().single { it.awardId == "ARRL_WAS_BASIC" }
        eq(1, card.confirmedCount, "Awards Center and map layer share authoritative progress")
        eq(after.localThresholdMet, card.localThresholdMet, "map/card local threshold state aligned")
    }

    private fun dateRangeFilteringIsPreserved() {
        val qsos = listOf(
            qso(700, "K1AAA", "6m", date = "20250101"),
            qso(701, "K2BBB", "6m", date = "20260101"),
        )
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(
                target(700, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "AA00"),
                target(701, OfficialAwardTargetKind.MAIDENHEAD_GRID4, "AA01"),
            ),
            confirmations = listOf(
                AwardConfirmationEvidence(700, "LOTW"),
                AwardConfirmationEvidence(701, "LOTW"),
            ),
        )
        val query = AwardsCenterQuery(
            dateRange = AwardsCenterDateRange("2025-06-01", "2026-12-31"),
        )

        val layer = AwardAreaMapProjectionService().project(qsos, evidence, query).layer("ARRL_VUCC_50MHZ")
        eq(1, layer.targets.size, "date query leaves one known VUCC grid")
        eq("AA01", layer.targets.single().identity.targetValue, "date query retains later grid")
        eq(99, layer.remainingToThresholdCount, "date-filtered threshold remainder")
    }

    private fun dcCanonicalizationMatchesAwardsEngine() {
        val qso = qso(800, "K1AAA", "20m")
        val evidence = AwardEvidenceSnapshot(
            targets = listOf(target(800, OfficialAwardTargetKind.US_STATE, "DC")),
            confirmations = listOf(AwardConfirmationEvidence(800, "LOTW")),
        )

        val layer = AwardAreaMapProjectionService().project(listOf(qso), evidence).layer("ARRL_WAS_BASIC")
        eq(AwardAreaMapState.CONFIRMED, layer.target("MD").primaryState, "DC credit canonicalizes to Maryland")
        checkThat(layer.targets.none { it.identity.targetValue == "DC" }, "DC is not emitted as separate WAS geometry target")
    }

    private fun emptyEvidenceIsSafe() {
        val layers = AwardAreaMapProjectionService().project(
            qsos = emptyList(),
            evidence = AwardEvidenceSnapshot(),
        )
        val was = layers.layer("ARRL_WAS_BASIC")
        val ffma = layers.layer("ARRL_FFMA")
        val vucc = layers.layer("ARRL_VUCC_50MHZ")

        eq(50, was.targets.size, "empty WAS still emits known state universe")
        eq(50, was.neededCount, "empty WAS all needed")
        eq(488, ffma.targets.size, "empty FFMA still emits known grid universe")
        eq(488, ffma.neededCount, "empty FFMA all needed")
        eq(0, vucc.targets.size, "empty VUCC emits no fabricated world grid universe")
        eq(100, vucc.remainingToThresholdCount, "empty VUCC numeric remainder")
    }

    private fun invalidGeometryMetadataFailsClosed() {
        expectFailure("geometry source URL must be HTTPS") {
            AwardAreaGeometrySource(
                sourceId = "BAD",
                sourceVersion = "1",
                sourceUrl = "http://example.invalid",
            )
        }
        expectFailure("geometry source id required") {
            AwardAreaGeometrySource(
                sourceId = " ",
                sourceVersion = "1",
            )
        }
        expectFailure("geometry version required") {
            AwardAreaGeometrySource(
                sourceId = "SRC",
                sourceVersion = " ",
            )
        }
        expectFailure("geometry retrieval date format") {
            AwardAreaGeometrySource(
                sourceId = "SRC",
                sourceVersion = "1",
                retrievedOn = "06-10-2026",
            )
        }
        expectFailure("geometry asset id required") {
            AwardAreaGeometryBinding(
                identity = AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, "OR"),
                source = AwardAreaGeometrySource("SRC", "1"),
                geometryAssetId = " ",
            )
        }
        expectFailure("geometry target value required") {
            AwardAreaGeometryIdentity(OfficialAwardTargetKind.US_STATE, " ")
        }
    }

    private fun List<AwardAreaMapLayer>.layer(id: String): AwardAreaMapLayer =
        single { it.awardId == id }

    private fun AwardAreaMapLayer.target(value: String): AwardAreaMapTarget =
        singleTarget(value)

    private fun AwardAreaMapLayer.singleTarget(value: String): AwardAreaMapTarget =
        targets.single { it.identity.targetValue == value }

    private fun target(
        qsoId: Long,
        kind: OfficialAwardTargetKind,
        value: String,
        sourceId: String = "MAP_FIXTURE",
        sourceVersion: String = "v1",
        reference: String = "qso-$qsoId",
    ) = AwardTargetEvidence(
        qsoId = qsoId,
        kind = kind,
        value = value,
        provenance = AwardTargetProvenance(
            sourceId = sourceId,
            sourceVersion = sourceVersion,
            sourceUrl = "https://example.invalid/map-fixture",
            reference = reference,
            retrievedAtUtcMillis = 1L,
        ),
    )

    private fun qso(
        id: Long,
        call: String,
        band: String,
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
    )
}
