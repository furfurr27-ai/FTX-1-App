package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import dev.n0png.fieldops.core.map.GeoCoordinate

/** CP-0008S reproducible, entirely synthetic, offline comparative tests. */
object PropagationOfflineReportComparisonTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0

    private fun yes(ok: Boolean, label: String) {
        assertions++
        check(ok) { label }
    }

    private fun equal(expected: Any?, actual: Any?, label: String) {
        assertions++
        check(expected == actual) {
            "$label expected=$expected actual=$actual"
        }
    }

    private fun denied(label: String, task: () -> Unit) {
        assertions++
        check(runCatching(task).isFailure) { "Accepted invalid $label" }
    }

    private fun ref(key: String) = PropagationSourceRef(
        sourceId = key, providerName = "source 🌍",
        sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
        sourceVersion = "v1", retrievedAtUtcMillis = NOW - 40
    )

    private fun confidence() = PropagationConfidence(
        0.92, PropagationConfidenceBasis.SYNTHETIC, "offline"
    )

    private fun solar(id: String, kp: Double = 3.5) = SolarGeomagneticObservation(
        evidenceId = id, source = ref("NOAA_SWPC_PLANETARY_KP"),
        observedAtUtcMillis = NOW - 120, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = kp
    )

    private fun endpoint(lat: Double, lon: Double, callsign: String) =
        PropagationEndpoint(
            PropagationPosition(
                coordinate = GeoCoordinate(latitude = lat, longitude = lon),
                method = PropagationLocationMethod.EXPLICIT_COORDINATE
            ),
            callsign = callsign
        )

    private fun heard() = HeardPathObservation(
        evidenceId = "heard", source = ref("PSK_REPORTER_PUBLIC_QUERY"),
        observedAtUtcMillis = NOW - 125, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        transmitter = endpoint(50.0, 8.2, "N0PNG"),
        receiver = endpoint(49.9, 7.7, "DL1TEST"),
        frequencyHz = 7_075_000, band = "40m", mode = "FT8",
        snrDb = -15.0, reportCount = 3
    )

    private fun state(key: String, role: PropagationRefreshSourceRole) =
        PropagationRefreshSourceState(
            sourceKey = key, role = role,
            lastAttemptUtcMillis = NOW - 10,
            lastSuccessUtcMillis = NOW - 10,
            nextEligibleRefreshUtcMillis = NOW + 100
        )

    private fun fixture(
        snapshot: PropagationSnapshot? = null,
        states: List<PropagationRefreshSourceState> = emptyList(),
        queryUtc: Long = NOW
    ): PropagationOfflineDiagnosticReport {
        val snapshots = InMemoryPropagationSnapshotStore()
        snapshot?.let(snapshots::save)
        val refresh = InMemoryPropagationRefreshStateStore()
        states.forEach(refresh::save)
        val picture = PropagationOperatingPictureService(snapshots, refresh)
            .read(PropagationProjectionQuery(queryUtc))
        return PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(picture)
        )
    }

    private fun wire(report: PropagationOfflineDiagnosticReport) =
        PropagationOfflineReportSerialization.serialize(report)

    private fun delta(
        before: PropagationOfflineDiagnosticReport,
        after: PropagationOfflineDiagnosticReport,
    ) = PropagationOfflineReportComparisonService.compare(wire(before), wire(after))

    @JvmStatic fun main(args: Array<String>) {
        val empty = fixture()
        val identical = delta(empty, empty)
        yes(!identical.canonicalArtifactChanged, "identical wire is unchanged")
        yes(!identical.snapshotChanged && !identical.summaryChanged,
            "identical snapshot/summary")
        yes(identical.sourceChanges.isEmpty() && identical.evidenceChanges.isEmpty(),
            "no invented rows")
        equal(PropagationOfflineComparisonTimeOrder.SAME,
            identical.afterQueryTimeRelativeToBefore, "equal original time")
        yes(!identical.originAuthenticated && !identical.crossStoreAtomicityVerified,
            "no invented authenticity or atomicity")

        val old = fixture(
            PropagationSnapshot("same", NOW,
                solarGeomagnetic = listOf(solar("same", 3.5)),
                heardPaths = listOf(heard())
            ),
            listOf(
                state("NOAA_SWPC_PLANETARY_KP",
                    PropagationRefreshSourceRole.NOAA_KP_OBSERVED),
                state("PSK_REPORTER_PUBLIC_QUERY",
                    PropagationRefreshSourceRole.PSK_REPORTER)
            )
        )
        val newer = fixture(
            PropagationSnapshot("same", NOW,
                solarGeomagnetic = listOf(solar("same", 6.0), solar("new", 2.0))
            ),
            listOf(
                state("NOAA_SWPC_PLANETARY_KP",
                    PropagationRefreshSourceRole.NOAA_KP_OBSERVED),
                state("OTHER", PropagationRefreshSourceRole.GENERIC)
            )
        )
        val originalBefore = wire(old)
        val originalAfter = wire(newer)
        val comparison = PropagationOfflineReportComparisonService.compare(
            originalBefore, originalAfter)
        equal(originalBefore.sha256Hex, comparison.beforeReceipt.sha256Hex,
            "before artifact identity retained")
        equal(originalAfter.sha256Hex, comparison.afterReceipt.sha256Hex,
            "after artifact identity retained")
        yes(comparison.canonicalArtifactChanged, "changed canonical bytes")
        yes(comparison.snapshotChanged, "snapshot source-count metadata changed")
        yes(comparison.summaryChanged, "source/evidence summary difference")
        equal(listOf("NOAA_SWPC_PLANETARY_KP", "OTHER", "PSK_REPORTER_PUBLIC_QUERY"),
            comparison.sourceChanges.map { it.sourceKey },
            "deterministic Unicode-independent source key sort")
        equal(PropagationOfflineComparisonChange.ADDED_TO_VIEW,
            comparison.sourceChanges.single { it.sourceKey == "OTHER" }.change,
            "source added to reported view")
        equal(PropagationOfflineComparisonChange.REMOVED_FROM_VIEW,
            comparison.sourceChanges.single {
                it.sourceKey == "PSK_REPORTER_PUBLIC_QUERY" }.change,
            "source removed from reported view")
        equal(listOf("heard", "new", "same"),
            comparison.evidenceChanges.map { it.evidenceId },
            "deterministic evidence identity sort")
        val removed = comparison.evidenceChanges.single { it.evidenceId == "heard" }
        equal(PropagationOfflineComparisonChange.REMOVED_FROM_VIEW,
            removed.change, "heard path no longer selected")
        yes(removed.before != null && removed.after == null,
            "removed metadata remains inspectable")
        val added = comparison.evidenceChanges.single { it.evidenceId == "new" }
        equal(PropagationOfflineComparisonChange.ADDED_TO_VIEW, added.change,
            "new solar observation selected")
        val changed = comparison.evidenceChanges.single { it.evidenceId == "same" }
        equal(PropagationOfflineComparisonChange.CHANGED_IN_VIEW, changed.change,
            "solar measurement change not missed")
        yes(changed.projectionContentChanged, "payload change detected")
        yes(!changed.selectedIndexChanged,
            "unchanged metadata index remains equal even when payload changes")
        yes(comparison.changedEvidence().size == 3, "all selected deltas counted")
        equal(PropagationOfflineComparisonTimeOrder.SAME,
            comparison.afterQueryTimeRelativeToBefore,
            "same queries do not imply a later observation")
        yes(!comparison.originAuthenticated && !comparison.crossStoreAtomicityVerified,
            "checksums do not authenticate provider origin")
        equal(originalBefore, wire(old), "compare did not mutate first artifact")
        equal(originalAfter, wire(newer), "compare did not mutate second artifact")

        val reversed = delta(newer, old)
        equal(PropagationOfflineComparisonChange.ADDED_TO_VIEW,
            reversed.evidenceChanges.single { it.evidenceId == "heard" }.change,
            "reverse direction restores selected path")
        equal(PropagationOfflineComparisonChange.REMOVED_FROM_VIEW,
            reversed.evidenceChanges.single { it.evidenceId == "new" }.change,
            "reverse direction loses selected solar")
        yes(reversed.evidenceChanges.single {
            it.evidenceId == "same" }.projectionContentChanged,
            "reverse direction preserves changed projection")

        val left = delta(empty, old)
        yes(left.evidenceChanges.all {
            it.change == PropagationOfflineComparisonChange.ADDED_TO_VIEW },
            "absent snapshot is not mistaken for stale evidence")
        val right = delta(old, empty)
        yes(right.evidenceChanges.all {
            it.change == PropagationOfflineComparisonChange.REMOVED_FROM_VIEW },
            "removed from selection does not mean deleted from source")

        val extraQueryTime = fixture(
            PropagationSnapshot("same", NOW,
                solarGeomagnetic = listOf(solar("same", 3.5)),
                heardPaths = listOf(heard())
            ),
            listOf(
                state("NOAA_SWPC_PLANETARY_KP",
                    PropagationRefreshSourceRole.NOAA_KP_OBSERVED),
                state("PSK_REPORTER_PUBLIC_QUERY",
                    PropagationRefreshSourceRole.PSK_REPORTER)
            ),
            queryUtc = NOW + 200
        )
        val later = delta(old, extraQueryTime)
        equal(PropagationOfflineComparisonTimeOrder.LATER,
            later.afterQueryTimeRelativeToBefore,
            "preserve original query clock order")
        equal(PropagationOfflineComparisonTimeOrder.EARLIER,
            delta(extraQueryTime, old).afterQueryTimeRelativeToBefore,
            "reverse query clock order")
        yes(later.canonicalArtifactChanged, "query time is report content")
        yes(later.evidenceChanges.any { it.selectedIndexChanged ||
            it.projectionContentChanged }, "freshness metadata may evolve with query")
        
        val assessed = old.copy(workspace = requireNotNull(old.workspace).copy(
            selectedPathAssessment = PropagationPathAssessment(
                usability = PropagationUsability.UNKNOWN,
                confidence = null,
                reasons = listOf(PropagationAssessmentReason(
                    code = PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE,
                    explanation = "synthetic comparison-only assessment"
                )),
                evidenceIds = emptyList()
            )
        ))
        val assessment = delta(old, assessed)
        yes(assessment.selectedAssessmentChanged, "assessment-only difference")
        yes(assessment.evidenceChanges.all {
            it.change == PropagationOfflineComparisonChange.UNCHANGED
        }, "assessment change does not fabricate evidence change")

        val beforeImported = PropagationOfflineReportImportService.importReport(originalBefore)
        val afterImported = PropagationOfflineReportImportService.importReport(originalAfter)
        val reused = PropagationOfflineReportComparisonService.compareImported(
            beforeImported, afterImported)
        equal(comparison, reused, "prevalidated imports compare identically")
        
        denied("tampered first digest") {
            PropagationOfflineReportComparisonService.compare(
                originalBefore.copy(sha256Hex = "f".repeat(64)), originalAfter)
        }
        denied("tampered second content type") {
            PropagationOfflineReportComparisonService.compare(
                originalBefore, originalAfter.copy(contentType = "application/json"))
        }
        denied("truncated second report") {
            PropagationOfflineReportComparisonService.compare(
                originalBefore, originalAfter.copy(json = originalAfter.json.dropLast(1)))
        }
        denied("tampered original JSON after import") {
            val dirty = PropagationOfflineReportImportService.importReport(originalBefore)
            val evidence = dirty.report.visibleEvidenceIndex as MutableList<PropagationOfflineEvidenceIndexItem>
            evidence.clear()
            PropagationOfflineReportComparisonService.compareImported(dirty, afterImported)
        }
        println("CP-0008S offline imported report comparison: PASS assertions=$assertions")
    }
}
