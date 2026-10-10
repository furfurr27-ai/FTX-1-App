package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008Y historical display contract. */
object PropagationOfflineReportComparisonDisplayTests {
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
        val before = artifact(report())
        val after = artifact(report(
            PropagationSnapshot("display-snapshot", NOW,
                solarGeomagnetic = (0 until 130).map {
                    observation("evidence-" + it.toString().padStart(3, '0'), 2.0)
                }),
            (0 until 115).map { state("source-" + it.toString().padStart(3, '0')) },
            NOW + 120
        ))
        val wire = PropagationOfflineReportComparisonSerialization.serialize(before, after)
        val service = PropagationOfflineReportComparisonDisplayService
        val page = service.display(wire)
        val prior = PropagationOfflineReportComparisonPaginationService.page(wire)
        eq(page, service.display(wire), "deterministic display")
        eq("Archived propagation comparison", page.historicalTitle, "fixed historical title")
        eq(prior.header, page.header, "source provenance and global tallies preserved")
        eq(before.sha256Hex, page.header.beforeReportReceipt.sha256Hex,
            "original before receipt checksum")
        eq(after.sha256Hex, page.header.afterReportReceipt.sha256Hex,
            "original after receipt checksum")
        eq(wire.sha256Hex, page.header.comparisonReceipt.sha256Hex,
            "comparison receipt checksum")
        eq(PropagationOfflineComparisonTimeOrder.LATER,
            page.header.afterQueryTimeRelativeToBefore, "original UTC time order")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED,
            page.header.originalReportsReconciliation, "unverified originals")
        yes(page.reconciliationLabel.contains("not supplied"), "not supplied label")
        yes(!page.header.comparisonReceipt.originAuthenticated &&
            !page.header.comparisonReceipt.crossStoreAtomicityVerified,
            "no unearned trust claims")
        eq(PropagationOfflineComparisonDisplayNoticeCode.values().toList(),
            page.notices.map { it.code }, "all four non-optional safety notices")
        yes(page.notices.all { it.text.isNotBlank() }, "readable warnings")
        yes(page.notices.any { it.text.contains("not current propagation") },
            "historical-only warning is explicit")
        yes(page.notices.any { it.text.contains("not created or deleted") },
            "selected view not provider deletion")
        yes(page.notices.any { it.text.contains("not authenticated origin") },
            "checksum not authentication")
        yes(page.notices.any { it.text.contains("Cross-store atomicity") },
            "cross-store uncertainty explicit")
        eq(25, page.sources.rows.size, "bounded source display")
        eq(25, page.selectedEvidence.rows.size, "bounded evidence display")
        eq(prior.sources.matchingRows, page.sources.matchingRows,
            "source filtered counts")
        eq(prior.selectedEvidence.matchingRows, page.selectedEvidence.matchingRows,
            "evidence filtered counts")
        eq(page.header.sourceCounts.total, page.sources.globalRows, "global source count")
        eq(page.header.selectedEvidenceCounts.total, page.selectedEvidence.globalRows,
            "global evidence count")
        eq(prior.sources.rows.map { it.sourceKey }, page.sources.rows.map { it.sourceKey },
            "preserve source order and identity")
        eq(prior.selectedEvidence.rows.map { it.evidenceId },
            page.selectedEvidence.rows.map { it.evidenceId },
            "preserve evidence order and identity")
        eq(prior.sources.rows.map { it.before }, page.sources.rows.map { it.before },
            "before source report provenance")
        eq(prior.sources.rows.map { it.after }, page.sources.rows.map { it.after },
            "after source report status/timing")
        eq(prior.selectedEvidence.rows.map { it.before },
            page.selectedEvidence.rows.map { it.before }, "before selected index")
        eq(prior.selectedEvidence.rows.map { it.after },
            page.selectedEvidence.rows.map { it.after }, "after selected index")
        eq(prior.selectedEvidence.rows.map { it.selectedIndexChanged },
            page.selectedEvidence.rows.map { it.selectedIndexChanged }, "index flags")
        eq(prior.selectedEvidence.rows.map { it.projectionContentChanged },
            page.selectedEvidence.rows.map { it.projectionContentChanged },
            "full projection-content flags")
        yes(page.sources.rows.all {
            it.label == PropagationOfflineComparisonDisplayChangeLabel.ADDED_TO_SELECTED_VIEW &&
                it.change == PropagationOfflineComparisonChange.ADDED_TO_VIEW
        }, "source badges refer to selected view only")
        yes(page.selectedEvidence.rows.all {
            it.label == PropagationOfflineComparisonDisplayChangeLabel.ADDED_TO_SELECTED_VIEW
        }, "evidence badges refer to selected view only")
        eq(null, page.sources.previousQuery, "initial sources no previous page")
        eq(null, page.selectedEvidence.previousQuery, "initial evidence no previous page")
        eq(25L, page.sources.nextQuery?.offset, "source next")
        eq(25L, page.selectedEvidence.nextQuery?.offset, "evidence next")
        val sourceFiltered = PropagationOfflineComparisonSourcePageQuery(
            offset = 10, limit = 7, sourceKeyPrefix = "source-0",
            change = PropagationOfflineComparisonChange.ADDED_TO_VIEW)
        val evidenceFiltered = PropagationOfflineComparisonEvidencePageQuery(
            offset = 15, limit = 11, evidenceIdPrefix = "evidence-0",
            kind = PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC,
            change = PropagationOfflineComparisonChange.ADDED_TO_VIEW)
        val filtered = service.display(wire, sourceFiltered, evidenceFiltered)
        val filteredUnderlying =
            PropagationOfflineReportComparisonPaginationService.page(
                wire, sourceFiltered, evidenceFiltered)
        eq(filteredUnderlying.sources.rows.map { it.sourceKey },
            filtered.sources.rows.map { it.sourceKey }, "filtered stable source order")
        eq(filteredUnderlying.selectedEvidence.rows.map { it.evidenceId },
            filtered.selectedEvidence.rows.map { it.evidenceId },
            "filtered stable evidence order")
        eq(3L, filtered.sources.previousQuery?.offset, "source previous offset bounded")
        eq(4L, filtered.selectedEvidence.previousQuery?.offset,
            "evidence previous offset bounded")
        eq(sourceFiltered.copy(offset = 17), filtered.sources.nextQuery,
            "source continuation retains all filters")
        eq(evidenceFiltered.copy(offset = 26), filtered.selectedEvidence.nextQuery,
            "evidence continuation retains kind/change/prefix filters")
        eq(page.sources.globalRows, filtered.sources.globalRows, "unfiltered source totals")
        eq(page.selectedEvidence.globalRows, filtered.selectedEvidence.globalRows,
            "unfiltered selected evidence totals")
        val allSources = mutableListOf<String>()
        var currentSources = page.sources
        while (true) {
            allSources.addAll(currentSources.rows.map { it.sourceKey })
            val q = currentSources.nextQuery ?: break
            currentSources = service.display(wire, sources = q).sources
            yes(currentSources.rows.size <= 25, "continuation source page bounded")
        }
        eq(page.sources.globalRows, allSources.size, "all source IDs reachable")
        eq(allSources.size, allSources.toSet().size, "no duplicate source keys")
        val allEvidence = mutableListOf<String>()
        var currentEvidence = page.selectedEvidence
        while (true) {
            allEvidence.addAll(currentEvidence.rows.map { it.evidenceId })
            val q = currentEvidence.nextQuery ?: break
            currentEvidence = service.display(wire, evidence = q).selectedEvidence
            yes(currentEvidence.rows.size <= 25, "continuation evidence page bounded")
        }
        eq(page.selectedEvidence.globalRows, allEvidence.size, "all selected evidence reachable")
        eq(allEvidence.size, allEvidence.toSet().size, "no repeated evidence")
        val beyond = service.display(wire,
            sources = PropagationOfflineComparisonSourcePageQuery(offset = Long.MAX_VALUE),
            evidence = PropagationOfflineComparisonEvidencePageQuery(offset = Long.MAX_VALUE))
        eq(0, beyond.sources.rows.size, "huge source offset empty")
        eq(0, beyond.selectedEvidence.rows.size, "huge evidence offset empty")
        eq(Long.MAX_VALUE - 25, beyond.sources.previousQuery?.offset,
            "long offset previous safe")
        eq(null, beyond.sources.nextQuery, "no overflow source next")
        eq(null, beyond.selectedEvidence.nextQuery, "no overflow evidence next")
        val matched = service.display(wire, before = before, after = after)
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            matched.header.originalReportsReconciliation, "original receipts crosschecked")
        yes(matched.reconciliationLabel.contains("origin unauthenticated"),
            "even reconciled originals not authenticated")
        val mismatched = service.display(wire, before = after, after = before)
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH,
            mismatched.header.originalReportsReconciliation, "swapped originals mismatch")
        yes(mismatched.reconciliationLabel.contains("differs"),
            "mismatch indicated clearly")
        eq(page.notices, matched.notices, "reconciliation does not suppress warnings")
        eq(page.notices, mismatched.notices, "mismatch does not suppress warnings")
        val reverse = PropagationOfflineReportComparisonSerialization.serialize(after, before)
        val removed = service.display(reverse)
        yes(removed.sources.rows.all {
            it.label == PropagationOfflineComparisonDisplayChangeLabel.REMOVED_FROM_SELECTED_VIEW
        }, "reverse view source removal label")
        yes(removed.selectedEvidence.rows.all {
            it.label == PropagationOfflineComparisonDisplayChangeLabel.REMOVED_FROM_SELECTED_VIEW
        }, "reverse view evidence removal label")
        val same = service.display(
            PropagationOfflineReportComparisonSerialization.serialize(before, before))
        eq(0, same.sources.rows.size, "empty identical source report")
        eq(0, same.selectedEvidence.rows.size, "empty identical evidence report")
        eq(null, same.sources.nextQuery, "empty no continuation")
        denied("one original report") { service.display(wire, before = before) }
        denied("invalid source pagination") {
            service.display(wire, sources = PropagationOfflineComparisonSourcePageQuery(limit = 101))
        }
        denied("invalid evidence pagination") {
            service.display(wire, evidence = PropagationOfflineComparisonEvidencePageQuery(offset = -1))
        }
        denied("bad comparison checksum") {
            service.display(wire.copy(sha256Hex = "0".repeat(64)))
        }
        denied("bad comparison wire version") {
            service.display(wire.copy(wireVersion = 3))
        }
        denied("truncated comparison JSON") {
            service.display(wire.copy(json = wire.json.dropLast(1)))
        }
        denied("unmodifiable notices") {
            (page.notices as MutableList<PropagationOfflineComparisonDisplayNotice>).clear()
        }
        denied("unmodifiable source rows") {
            (page.sources.rows as MutableList<PropagationOfflineComparisonDisplaySourceRow>).clear()
        }
        denied("unmodifiable evidence rows") {
            (page.selectedEvidence.rows as MutableList<PropagationOfflineComparisonDisplayEvidenceRow>).clear()
        }
        println("CP-0008Y paged display integration: PASS assertions=$assertions")
    }
}
