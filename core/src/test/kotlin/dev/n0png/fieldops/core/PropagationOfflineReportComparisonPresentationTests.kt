package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008V comparison import/inspection boundary. */
object PropagationOfflineReportComparisonPresentationTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0

    private fun eq(a: Any?, b: Any?, name: String) {
        assertions++
        check(a == b) { "$name expected=$a actual=$b" }
    }
    private fun yes(ok: Boolean, name: String) {
        assertions++
        check(ok) { name }
    }
    private fun denied(name: String, f: () -> Unit) {
        assertions++
        check(runCatching(f).isFailure) { "Accepted invalid $name" }
    }

    private fun state(key: String) = PropagationRefreshSourceState(
        sourceKey = key, role = PropagationRefreshSourceRole.GENERIC,
        lastAttemptUtcMillis = NOW - 100,
        lastSuccessUtcMillis = NOW - 100,
        nextEligibleRefreshUtcMillis = NOW + 200,
    )

    private fun report(
        snapshot: PropagationSnapshot? = null,
        sources: List<PropagationRefreshSourceState> = emptyList(),
        time: Long = NOW,
    ): PropagationOfflineDiagnosticReport {
        val snapshots = InMemoryPropagationSnapshotStore()
        snapshot?.let(snapshots::save)
        val refresh = InMemoryPropagationRefreshStateStore()
        sources.forEach(refresh::save)
        val picture = PropagationOperatingPictureService(snapshots, refresh)
            .read(PropagationProjectionQuery(time))
        return PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(picture)
        )
    }

    private fun artifact(report: PropagationOfflineDiagnosticReport) =
        PropagationOfflineReportSerialization.serialize(report)

    private fun confidence() = PropagationConfidence(
        0.85, PropagationConfidenceBasis.SYNTHETIC, "fixture"
    )

    private fun observation(id: String, kp: Double) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = "offline-solar", providerName = "synthetic 🌍",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "v1", retrievedAtUtcMillis = NOW - 50
        ),
        observedAtUtcMillis = NOW - 100,
        confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = kp
    )




    @JvmStatic fun main(args: Array<String>) {
        val none = artifact(report())
        val before = artifact(report(
            PropagationSnapshot("snap-1", NOW - 20,
                solarGeomagnetic = listOf(observation("solar-é🌍", 2.0))),
            listOf(state("offline-solar"))
        ))
        val after = artifact(report(
            PropagationSnapshot("snap-2", NOW,
                solarGeomagnetic = listOf(observation("solar-é🌍", 3.5),
                    observation("added", 4.0))),
            listOf(state("offline-solar"), state("another-source")), NOW + 120
        ))
        val expected = PropagationOfflineReportComparisonService.compare(before, after)
        val wire = PropagationOfflineReportComparisonSerialization.serialize(before, after)
        val service = PropagationOfflineReportComparisonPresentationService
        val view = service.present(wire)
        val matched = service.present(wire, before, after)
        eq(view, service.present(wire), "deterministic")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED,
            view.originalReportsReconciliation, "unverified stand-alone")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            matched.originalReportsReconciliation, "complete original reconciliation")
        eq(expected.beforeReceipt, view.beforeReportReceipt, "before receipt")
        eq(expected.afterReceipt, view.afterReportReceipt, "after receipt")
        eq(wire.sha256Hex, view.comparisonReceipt.sha256Hex, "comparison receipt hash")
        eq(wire.utf8ByteCount, view.comparisonReceipt.utf8ByteCount, "UTF8 receipt length")
        yes(!view.comparisonReceipt.originAuthenticated &&
            !view.comparisonReceipt.crossStoreAtomicityVerified, "no false claims")
        eq(PropagationOfflineComparisonTimeOrder.LATER, view.afterQueryTimeRelativeToBefore,
            "original UTC query order")
        eq(expected.snapshotChanged, view.snapshotChanged, "snapshot")
        eq(expected.summaryChanged, view.summaryChanged, "summary")
        eq(expected.selectedAssessmentChanged, view.selectedAssessmentChanged, "assessment")
        eq(expected.canonicalArtifactChanged, view.canonicalArtifactChanged, "artifact marker")
        eq(expected.sourceChanges.map { it.sourceKey }, view.sources.map { it.sourceKey },
            "source ordering")
        eq(expected.evidenceChanges.map { it.evidenceId },
            view.selectedEvidence.map { it.evidenceId }, "evidence ordering")
        eq(expected.sourceChanges.map { it.before }, view.sources.map { it.before },
            "before source provenance")
        eq(expected.evidenceChanges.map { it.after }, view.selectedEvidence.map { it.after },
            "after evidence provenance")
        eq(expected.evidenceChanges.map { it.projectionContentChanged },
            view.selectedEvidence.map { it.projectionContentChanged }, "projection flags")
        eq(expected.evidenceChanges.map { it.selectedIndexChanged },
            view.selectedEvidence.map { it.selectedIndexChanged }, "index flags")
        eq(view.sources.size, view.sourceCounts.total, "source total")
        eq(view.selectedEvidence.size, view.selectedEvidenceCounts.total, "evidence total")
        eq(expected.changedSources().size, view.sourceCounts.changed, "changed source tally")
        eq(expected.changedEvidence().size, view.selectedEvidenceCounts.changed,
            "changed evidence tally")
        eq(1, view.sourceCounts.addedToView, "new source")
        eq(1, view.sourceCounts.unchanged, "same source")
        eq(1, view.selectedEvidenceCounts.changedInView, "changed selected evidence")
        eq(1, view.selectedEvidenceCounts.addedToView, "new selected evidence")
        yes(view.evidence("solar-é🌍")?.projectionContentChanged == true,
            "evidence identity and full content change")
        eq(null, view.evidence("missing"), "missing evidence lookup")
        eq(null, view.source("missing"), "missing source lookup")
        eq(1, view.sourcesWithChange(
            PropagationOfflineComparisonChange.ADDED_TO_VIEW).size, "source filter")
        eq(1, view.evidenceWithChange(
            PropagationOfflineComparisonChange.CHANGED_IN_VIEW).size, "evidence filter")
        eq(view.selectedEvidence, view.evidenceOfKind(
            PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC), "kind filter")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH,
            service.present(wire, before, before).originalReportsReconciliation,
            "non-matching originals")
        val altered = PropagationOfflineReportComparisonSerialization.serialize(
            expected.copy(snapshotChanged = !expected.snapshotChanged))
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH,
            service.present(altered, before, after).originalReportsReconciliation,
            "independently recomputed derivative mismatch")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED,
            service.present(altered).originalReportsReconciliation,
            "self-consistency is not authenticated evidence")
        for ((first, second, label) in listOf(
            Triple(none, none, "both empty"),
            Triple(none, before, "selected added"),
            Triple(before, none, "selected removed"),
            Triple(before, before, "same report"),
            Triple(after, before, "reversed"),
        )) {
            val compare = PropagationOfflineReportComparisonService.compare(first, second)
            val v = service.present(
                PropagationOfflineReportComparisonSerialization.serialize(first, second),
                first, second)
            eq(compare.sourceChanges.size, v.sourceCounts.total, "$label sources")
            eq(compare.evidenceChanges.size, v.selectedEvidenceCounts.total, "$label evidence")
            eq(compare.changedEvidence().size, v.selectedEvidenceCounts.changed,
                "$label changed")
            eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
                v.originalReportsReconciliation, "$label reconciled")
        }
        denied("missing after artifact") { service.present(wire, before, null) }
        denied("missing before artifact") { service.present(wire, null, after) }
        denied("wrong media") { service.present(wire.copy(contentType = "application/json")) }
        denied("wrong version") { service.present(wire.copy(wireVersion = 2)) }
        denied("bad byte count") { service.present(
            wire.copy(utf8ByteCount = wire.utf8ByteCount + 1)) }
        denied("wrong checksum") { service.present(wire.copy(sha256Hex = "0".repeat(64))) }
        denied("truncated JSON") { service.present(wire.copy(json = wire.json.dropLast(1))) }
        denied("bad original checksum") { service.present(
            wire, before.copy(sha256Hex = "0".repeat(64)), after) }
        denied("outer source view mutable") {
            (view.sources as MutableList<PropagationOfflineComparisonSourceRow>).clear()
        }
        denied("outer evidence view mutable") {
            (view.selectedEvidence as MutableList<PropagationOfflineComparisonSelectedEvidenceRow>)
                .clear()
        }
        println("CP-0008W presentation model: PASS assertions=$assertions")
    }
}
