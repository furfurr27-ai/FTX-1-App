package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008V comparison import/inspection boundary. */
object PropagationOfflineReportComparisonImportTests {
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
        val missing = artifact(report())
        val before = artifact(report(
            PropagationSnapshot("before-snapshot", NOW - 20,
                solarGeomagnetic = listOf(observation("evidence-é🌍", 2.0))),
            listOf(state("offline-solar"))
        ))
        val after = artifact(report(
            PropagationSnapshot("after-snapshot", NOW,
                solarGeomagnetic = listOf(observation("evidence-é🌍", 3.5),
                    observation("extra", 4.0))),
            listOf(state("offline-solar"), state("other-source")),
            NOW + 120
        ))
        val expected = PropagationOfflineReportComparisonService.compare(before, after)
        val wire = PropagationOfflineReportComparisonSerialization.serialize(before, after)
        val imported = PropagationOfflineReportComparisonImportService.importComparison(wire)
        eq(expected, imported.comparison, "exact detached validated comparison")
        eq(expected.beforeReceipt, imported.beforeReceipt, "original before receipt")
        eq(expected.afterReceipt, imported.afterReceipt, "original after receipt")
        eq(wire.sha256Hex, imported.receipt.sha256Hex, "comparison receipt SHA")
        eq(wire.utf8ByteCount, imported.receipt.utf8ByteCount, "comparison receipt UTF-8")
        eq(wire.contentType, imported.receipt.contentType, "comparison content type")
        eq(wire.wireVersion, imported.receipt.wireVersion, "comparison V1 version")
        eq(PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
            imported.receipt.trust, "unkeyed integrity only")
        yes(!imported.receipt.originAuthenticated &&
            !imported.receipt.crossStoreAtomicityVerified, "no false provenance claim")
        eq(PropagationOfflineComparisonTimeOrder.LATER,
            imported.afterQueryTimeRelativeToBefore, "original UTC query order")
        eq(expected.sourceChanges, imported.sourceChanges, "read-only source change snapshot")
        eq(expected.evidenceChanges, imported.evidenceChanges, "read-only evidence snapshot")
        eq(expected.changedSources().size, imported.sourceChangeCount, "source change count")
        eq(expected.changedEvidence().size, imported.evidenceChangeCount,
            "selected evidence change count")
        eq(expected.changedSources(), imported.changedSources(), "changed source rows")
        eq(expected.changedEvidence(), imported.changedSelectedEvidence(),
            "changed selected evidence rows")
        eq(null, imported.sourceByKey("missing"), "missing source lookup")
        eq(null, imported.selectedEvidenceById("missing"), "missing selected evidence")
        eq(expected.sourceChanges.find { it.sourceKey == "offline-solar" },
            imported.sourceByKey("offline-solar"), "source key lookup")
        eq(expected.evidenceChanges.find { it.evidenceId == "evidence-é🌍" },
            imported.selectedEvidenceById("evidence-é🌍"), "non-ASCII evidence identity")
        eq(expected.sourceChanges.filter {
            it.change == PropagationOfflineComparisonChange.ADDED_TO_VIEW
        }, imported.sourcesByChange(PropagationOfflineComparisonChange.ADDED_TO_VIEW),
            "source added-to-selected-view classification")
        eq(expected.evidenceChanges.filter {
            it.change == PropagationOfflineComparisonChange.CHANGED_IN_VIEW
        }, imported.selectedEvidenceByChange(PropagationOfflineComparisonChange.CHANGED_IN_VIEW),
            "selected evidence changed-in-view classification")
        eq(expected.evidenceChanges.filter {
            it.before?.kind == PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC ||
                it.after?.kind == PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC
        }, imported.selectedEvidenceByKind(PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC),
            "selected evidence kind filter uses before or after")
        yes(imported.evidenceChanges.any { it.projectionContentChanged },
            "full projection change flags retained")
        yes(PropagationOfflineReportComparisonImportService.matchesOriginalReports(
            imported, before, after), "optional full original report recomputation")
        denied("reversed original reports cannot match") {
            check(PropagationOfflineReportComparisonImportService.matchesOriginalReports(
                imported, after, before))
        }
        denied("wrong original report cannot match") {
            check(PropagationOfflineReportComparisonImportService.matchesOriginalReports(
                imported, before, before))
        }
        denied("wrong original report checksum rejected") {
            PropagationOfflineReportComparisonImportService.matchesOriginalReports(
                imported, before.copy(sha256Hex = "0".repeat(64)), after)
        }
        val altered = PropagationOfflineReportComparisonSerialization.serialize(
            expected.copy(snapshotChanged = !expected.snapshotChanged))
        val alteredImported =
            PropagationOfflineReportComparisonImportService.importComparison(altered)
        yes(!PropagationOfflineReportComparisonImportService.matchesOriginalReports(
            alteredImported, before, after),
            "structurally valid recomputed checksum does not establish true comparison")
        eq(expected.beforeReceipt, alteredImported.beforeReceipt,
            "counterfeit canonical export still retains original receipt")
        yes(!alteredImported.receipt.originAuthenticated,
            "self-consistent counterfeit is not authenticated")

        // Read-only boundary is detached from DTO passed to the serializer.
        val mutableSource = expected.sourceChanges.toMutableList()
        val manuallyConstructed = expected.copy(sourceChanges = mutableSource)
        val artifact = PropagationOfflineReportComparisonSerialization.serialize(manuallyConstructed)
        val detached = PropagationOfflineReportComparisonImportService.importComparison(artifact)
        mutableSource.clear()
        eq(expected.sourceChanges.size, detached.sourceChanges.size,
            "detached from caller-owned mutable list")
        denied("source list mutation through unsafe cast") {
            @Suppress("UNCHECKED_CAST")
            (detached.sourceChanges as MutableList<PropagationOfflineSourceComparison>).clear()
        }
        denied("evidence list mutation through unsafe cast") {
            @Suppress("UNCHECKED_CAST")
            (detached.evidenceChanges as MutableList<PropagationOfflineEvidenceComparison>).clear()
        }
        val selectedList = detached.changedSelectedEvidence()
        denied("filtered evidence list mutation") {
            @Suppress("UNCHECKED_CAST")
            (selectedList as MutableList<PropagationOfflineEvidenceComparison>).clear()
        }

        for ((first, second, label) in listOf(
            Triple(before, before, "same report"),
            Triple(after, before, "reverse order"),
            Triple(missing, before, "added selected evidence"),
            Triple(before, missing, "removed selected evidence"),
            Triple(missing, missing, "empty views"),
        )) {
            val exported = PropagationOfflineReportComparisonSerialization.serialize(first, second)
            val view = PropagationOfflineReportComparisonImportService.importComparison(exported)
            eq(PropagationOfflineReportComparisonService.compare(first, second),
                view.comparison, "$label exact historical comparison")
            yes(PropagationOfflineReportComparisonImportService.matchesOriginalReports(
                view, first, second), "$label optional original verification")
        }
        eq(PropagationOfflineComparisonTimeOrder.EARLIER,
            PropagationOfflineReportComparisonImportService.importComparison(
                PropagationOfflineReportComparisonSerialization.serialize(after, before)
            ).afterQueryTimeRelativeToBefore, "reversed UTC timestamps")

        denied("raw report media type not accepted") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(contentType = PropagationOfflineReportSerialization.CONTENT_TYPE))
        }
        denied("missing SHA receipt not accepted") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(sha256Hex = ""))
        }
        denied("tampered SHA receipt not accepted") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(sha256Hex = "0".repeat(64)))
        }
        denied("incorrect byte count not accepted") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(utf8ByteCount = wire.utf8ByteCount + 1))
        }
        denied("incompatible metadata version") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(wireVersion = 2))
        }
        denied("truncated wire not accepted") {
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(json = wire.json.dropLast(1)))
        }
        denied("noncanonical JSON whitespace even with checksum") {
            val json = wire.json + " "
            val bytes = json.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            PropagationOfflineReportComparisonImportService.importComparison(
                wire.copy(json = json, utf8ByteCount = bytes.size, sha256Hex = digest))
        }
        println("CP-0008V comparison import/inspection: PASS assertions=$assertions")
    }
}
