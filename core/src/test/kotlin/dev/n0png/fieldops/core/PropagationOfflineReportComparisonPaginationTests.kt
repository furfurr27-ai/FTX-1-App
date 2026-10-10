package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008X historical bounded pagination. */
object PropagationOfflineReportComparisonPaginationTests {
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
        val service = PropagationOfflineReportComparisonPaginationService
        val previous = artifact(report())
        val next = artifact(report(
            PropagationSnapshot("large-snapshot", NOW,
                solarGeomagnetic = (0 until 135).map {
                    observation("evidence-" + it.toString().padStart(3, '0'), 3.0)
                }),
            (0 until 122).map { state("source-" + it.toString().padStart(3, '0')) },
            NOW + 120
        ))
        val wire = PropagationOfflineReportComparisonSerialization.serialize(previous, next)
        val view = PropagationOfflineReportComparisonPresentationService.present(wire)
        val initial = service.page(wire)
        eq(wire.sha256Hex, initial.header.comparisonReceipt.sha256Hex,
            "canonical comparison receipt")
        eq(view.beforeReportReceipt, initial.header.beforeReportReceipt,
            "original before receipt")
        eq(view.afterReportReceipt, initial.header.afterReportReceipt, "original after receipt")
        eq(view.afterQueryTimeRelativeToBefore,
            initial.header.afterQueryTimeRelativeToBefore, "original UTC query order")
        eq(view.sourceCounts, initial.header.sourceCounts, "global source tallies")
        eq(view.selectedEvidenceCounts, initial.header.selectedEvidenceCounts,
            "global evidence tallies")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED,
            initial.header.originalReportsReconciliation, "no original reports")
        yes(!initial.header.comparisonReceipt.originAuthenticated &&
            !initial.header.comparisonReceipt.crossStoreAtomicityVerified,
            "no authenticated origin or atomicity")
        eq(25, initial.sources.rows.size, "default source cap")
        eq(25, initial.selectedEvidence.rows.size, "default evidence cap")
        eq(view.sources.size, initial.sources.matchingRows, "global source rows")
        eq(view.selectedEvidence.size, initial.selectedEvidence.matchingRows,
            "global selected evidence rows")
        eq(25L, initial.sources.nextOffset, "source continuation")
        eq(25L, initial.selectedEvidence.nextOffset, "evidence continuation")
        eq(view.sources.take(25), initial.sources.rows, "stable source ordering")
        eq(view.selectedEvidence.take(25), initial.selectedEvidence.rows,
            "stable selected evidence ordering")
        eq(initial, service.page(wire), "deterministic repeatability")

        val sourcePages = mutableListOf<PropagationOfflineComparisonSourceRow>()
        var sourceOffset = 0L
        do {
            val page = service.page(wire, sources =
                PropagationOfflineComparisonSourcePageQuery(offset = sourceOffset, limit = 17))
                .sources
            yes(page.rows.size <= 17, "bounded source page")
            sourcePages.addAll(page.rows)
            val nextOffset = page.nextOffset
            if (nextOffset == null) break
            yes(nextOffset > sourceOffset, "monotone source cursor")
            sourceOffset = nextOffset
        } while (true)
        eq(view.sources, sourcePages, "each source once across page boundaries")

        val evidencePages = mutableListOf<PropagationOfflineComparisonSelectedEvidenceRow>()
        var evidenceOffset = 0L
        do {
            val page = service.page(wire, evidence =
                PropagationOfflineComparisonEvidencePageQuery(
                    offset = evidenceOffset, limit = 100)).selectedEvidence
            yes(page.rows.size <= 100, "bounded evidence page")
            evidencePages.addAll(page.rows)
            val nextOffset = page.nextOffset
            if (nextOffset == null) break
            yes(nextOffset > evidenceOffset, "monotone evidence cursor")
            evidenceOffset = nextOffset
        } while (true)
        eq(view.selectedEvidence, evidencePages, "each selected evidence exactly once")
        yes(view.selectedEvidence.size > 100, "crosses maximum page boundary")
        eq(100, service.page(wire,
            evidence = PropagationOfflineComparisonEvidencePageQuery(limit = 100))
            .selectedEvidence.rows.size, "max page limit")
        eq(1, service.page(wire,
            evidence = PropagationOfflineComparisonEvidencePageQuery(limit = 1))
            .selectedEvidence.rows.size, "minimum page limit")
        val afterEnd = service.page(wire,
            sources = PropagationOfflineComparisonSourcePageQuery(offset = Long.MAX_VALUE),
            evidence = PropagationOfflineComparisonEvidencePageQuery(offset = Long.MAX_VALUE))
        eq(emptyList<Any>(), afterEnd.sources.rows, "huge source offset returns empty")
        eq(emptyList<Any>(), afterEnd.selectedEvidence.rows,
            "huge evidence offset returns empty")
        eq(null, afterEnd.sources.nextOffset, "huge source offset end")
        eq(null, afterEnd.selectedEvidence.nextOffset, "huge evidence offset end")

        val prefix = "source-01"
        val sourceFiltered = view.sources.filter { it.sourceKey.startsWith(prefix) }
        val narrowed = service.page(wire,
            sources = PropagationOfflineComparisonSourcePageQuery(
                sourceKeyPrefix = prefix, change =
                    PropagationOfflineComparisonChange.ADDED_TO_VIEW,
                limit = 100))
        eq(sourceFiltered, narrowed.sources.rows, "source key prefix and change filter")
        eq(sourceFiltered.size, narrowed.sources.matchingRows, "filtered source total")
        eq(view.sourceCounts, narrowed.header.sourceCounts, "global tallies unaffected")
        val evidencePrefix = "evidence-00"
        val evidenceFiltered = view.selectedEvidence.filter {
            it.evidenceId.startsWith(evidencePrefix) &&
                it.change == PropagationOfflineComparisonChange.ADDED_TO_VIEW &&
                (it.before?.kind == PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC ||
                    it.after?.kind == PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC)
        }
        val evidenceNarrowed = service.page(wire, evidence =
            PropagationOfflineComparisonEvidencePageQuery(
                change = PropagationOfflineComparisonChange.ADDED_TO_VIEW,
                kind = PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC,
                evidenceIdPrefix = evidencePrefix, limit = 100))
        eq(evidenceFiltered, evidenceNarrowed.selectedEvidence.rows,
            "selected evidence kind, prefix, change filters")
        eq(evidenceFiltered.size, evidenceNarrowed.selectedEvidence.matchingRows,
            "selected evidence filtered match count")
        eq(0, service.page(wire, evidence =
            PropagationOfflineComparisonEvidencePageQuery(
                kind = PropagationOfflineEvidenceKind.HEARD_PATH)).selectedEvidence.matchingRows,
            "no fabricated heard path data")
        eq(0, service.page(wire, sources =
            PropagationOfflineComparisonSourcePageQuery(
                change = PropagationOfflineComparisonChange.REMOVED_FROM_VIEW))
            .sources.matchingRows, "no provider deletion inference")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            service.page(wire, before = previous, after = next)
                .header.originalReportsReconciliation, "optional original reconciliation")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH,
            service.page(wire, before = next, after = previous)
                .header.originalReportsReconciliation, "misordered originals detected")
        denied("missing second original") { service.page(wire, before = previous) }
        denied("missing first original") { service.page(wire, after = next) }
        denied("invalid source limit zero") {
            PropagationOfflineComparisonSourcePageQuery(limit = 0)
        }
        denied("invalid evidence limit 101") {
            PropagationOfflineComparisonEvidencePageQuery(limit = 101)
        }
        denied("negative source offset") {
            PropagationOfflineComparisonSourcePageQuery(offset = -1)
        }
        denied("negative evidence offset") {
            PropagationOfflineComparisonEvidencePageQuery(offset = -1)
        }
        denied("empty source prefix") {
            PropagationOfflineComparisonSourcePageQuery(sourceKeyPrefix = "")
        }
        denied("oversize evidence prefix") {
            PropagationOfflineComparisonEvidencePageQuery(evidenceIdPrefix = "x".repeat(129))
        }
        denied("bad digest") {
            service.page(wire.copy(sha256Hex = "0".repeat(64)))
        }
        denied("incorrect bytes") {
            service.page(wire.copy(utf8ByteCount = wire.utf8ByteCount + 1))
        }
        denied("tampered canonical payload") {
            service.page(wire.copy(json = wire.json.dropLast(1)))
        }
        denied("outer list mutable") {
            (initial.sources.rows as MutableList<PropagationOfflineComparisonSourceRow>).clear()
        }
        denied("outer evidence list mutable") {
            (initial.selectedEvidence.rows as
                MutableList<PropagationOfflineComparisonSelectedEvidenceRow>).clear()
        }
        println("CP-0008X historical comparison pagination: PASS assertions=$assertions")
    }
}
