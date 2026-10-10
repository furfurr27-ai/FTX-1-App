package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008Y historical display contract. */
object PropagationOfflineReportComparisonInteractionTests {
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
            PropagationSnapshot("accessibility-snapshot", NOW,
                solarGeomagnetic = (0 until 125).map {
                    observation("evidence-" + it.toString().padStart(3, '0'), 2.0)
                }),
            (0 until 116).map { state("source-" + it.toString().padStart(3, '0')) },
            NOW + 120
        ))
        val wire = PropagationOfflineReportComparisonSerialization.serialize(before, after)
        val service = PropagationOfflineReportComparisonInteractionService
        val initial = service.open(wire)
        eq(initial, service.open(wire), "deterministic initial screen")
        eq(wire.sha256Hex, initial.state.comparisonSha256, "canonical artifact bound to state")
        eq(PropagationOfflineComparisonFocusTarget.SUMMARY, initial.state.focusTarget,
            "initial keyboard focus intent")
        yes(initial.state.announcement.contains("not live RF"),
            "initial accessible announcement is historical")
        eq(initial.display.notices, initial.noticeReadingOrder,
            "all required warnings always accessible in order")
        eq(4, initial.noticeReadingOrder.size, "all four warnings retained")
        yes(initial.noticeReadingOrder.any {
            it.code == PropagationOfflineComparisonDisplayNoticeCode.CHECKSUM_NOT_AUTHENTICATION
        }, "unkeyed checksum never promoted to identity")
        yes(initial.noticeReadingOrder.any {
            it.code == PropagationOfflineComparisonDisplayNoticeCode.SELECTED_VIEW_NOT_PROVIDER_DELETION
        }, "provider deletion warning")
        eq(1, initial.sources.firstOrdinal, "first source ordinal")
        eq(25, initial.sources.lastOrdinal, "end source ordinal")
        eq(116, initial.sources.matchingRows, "historical source matches")
        eq(125, initial.selectedEvidence.matchingRows, "historical evidence matches")
        yes(initial.sources.nextEnabled && !initial.sources.previousEnabled,
            "initial source nav capabilities")
        yes(initial.selectedEvidence.nextEnabled && !initial.selectedEvidence.previousEnabled,
            "initial evidence nav capabilities")
        yes(initial.sources.rangeLabel.contains("1 through 25 of 116"),
            "screenreader source range")
        yes(initial.selectedEvidence.rangeLabel.contains("1 through 25 of 125"),
            "screenreader evidence range")
        eq(initial.display.sources.rows.map { it.sourceKey }, initial.sources.rows.map { it.identity },
            "source identity and order match CP-0008Y")
        eq(initial.display.selectedEvidence.rows.map { it.evidenceId },
            initial.selectedEvidence.rows.map { it.identity },
            "evidence identity and order match CP-0008Y")
        yes(initial.sources.rows.all { it.label.contains("added to selected view") },
            "no live RF and source labels selected-view-only")
        yes(initial.selectedEvidence.rows.all {
            it.label.contains("added to selected view")
        }, "evidence labels selected-view-only")

        val nextSources = service.interact(
            wire, initial.state, PropagationOfflineComparisonInteraction.NextSources)
        eq(25L, nextSources.state.sourcesQuery.offset, "next source cursor")
        eq(0L, nextSources.state.evidenceQuery.offset, "evidence cursor unchanged")
        eq(26, nextSources.sources.firstOrdinal, "second page source start")
        eq(50, nextSources.sources.lastOrdinal, "second page source end")
        eq(PropagationOfflineComparisonFocusTarget.SOURCES,
            nextSources.state.focusTarget, "focus follows source interaction")
        eq(initial.display.header, nextSources.display.header,
            "receipts, tallies, time order and reconciliation unchanged")
        val back = service.interact(
            wire, nextSources.state, PropagationOfflineComparisonInteraction.PreviousSources)
        eq(initial.sources, back.sources, "previous source page returns exactly")
        eq(initial.selectedEvidence, back.selectedEvidence,
            "source navigation does not affect selected evidence")

        val nextEvidence = service.interact(
            wire, nextSources.state,
            PropagationOfflineComparisonInteraction.NextSelectedEvidence)
        eq(25L, nextEvidence.state.sourcesQuery.offset,
            "selected evidence doesn't reset source position")
        eq(25L, nextEvidence.state.evidenceQuery.offset, "selected evidence advances alone")
        eq(PropagationOfflineComparisonFocusTarget.SELECTED_EVIDENCE,
            nextEvidence.state.focusTarget, "evidence focus intent")
        eq(26, nextEvidence.selectedEvidence.firstOrdinal, "evidence second page begins")
        val evidenceBack = service.interact(
            wire, nextEvidence.state,
            PropagationOfflineComparisonInteraction.PreviousSelectedEvidence)
        eq(0L, evidenceBack.state.evidenceQuery.offset, "evidence previous independent")
        eq(25L, evidenceBack.state.sourcesQuery.offset, "source still at 25")

        val sourceFiltered = service.interact(wire, nextEvidence.state,
            PropagationOfflineComparisonInteraction.FilterSources(
                prefix = "source-0", change = PropagationOfflineComparisonChange.ADDED_TO_VIEW,
                limit = 11))
        eq(0L, sourceFiltered.state.sourcesQuery.offset,
            "source filter resets source cursor")
        eq(25L, sourceFiltered.state.evidenceQuery.offset,
            "source filter keeps evidence cursor")
        eq(11, sourceFiltered.sources.rows.size, "source filter page limit")
        eq(100, sourceFiltered.sources.matchingRows, "prefix count")
        eq(116, sourceFiltered.display.sources.globalRows, "global source total unchanged")
        eq(nextEvidence.display.selectedEvidence, sourceFiltered.display.selectedEvidence,
            "unaffected evidence section")
        val evidenceFiltered = service.interact(wire, sourceFiltered.state,
            PropagationOfflineComparisonInteraction.FilterSelectedEvidence(
                prefix = "evidence-01",
                kind = PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC,
                change = PropagationOfflineComparisonChange.ADDED_TO_VIEW, limit = 3))
        eq(0L, evidenceFiltered.state.evidenceQuery.offset,
            "selected evidence filter resets only own page")
        eq(0L, evidenceFiltered.state.sourcesQuery.offset,
            "source filter query retained")
        eq(10, evidenceFiltered.selectedEvidence.matchingRows,
            "kind and prefix and change filtering")
        eq(3, evidenceFiltered.selectedEvidence.rows.size, "filtered page length")
        eq(125, evidenceFiltered.display.selectedEvidence.globalRows,
            "unfiltered evidence total retained")
        eq(sourceFiltered.state.sourcesQuery, evidenceFiltered.state.sourcesQuery,
            "source filters independent")
        val evidenceFilteredNext = service.interact(
            wire, evidenceFiltered.state,
            PropagationOfflineComparisonInteraction.NextSelectedEvidence)
        eq(evidenceFiltered.state.evidenceQuery.copy(offset = 3),
            evidenceFilteredNext.state.evidenceQuery,
            "next page preserves all selected evidence filters")
        val noMatch = service.interact(wire, evidenceFilteredNext.state,
            PropagationOfflineComparisonInteraction.FilterSelectedEvidence(
                kind = PropagationOfflineEvidenceKind.HEARD_PATH))
        eq(0, noMatch.selectedEvidence.matchingRows, "no fabricated heard paths")
        eq(null, noMatch.selectedEvidence.firstOrdinal, "empty has no first ordinal")
        eq(null, noMatch.selectedEvidence.lastOrdinal, "empty has no last ordinal")
        eq("No matching archived rows", noMatch.selectedEvidence.rangeLabel,
            "empty state has explicit narration")
        yes(!noMatch.selectedEvidence.nextEnabled, "empty page cannot advance")
        val noNext = service.interact(wire, noMatch.state,
            PropagationOfflineComparisonInteraction.NextSelectedEvidence)
        eq(noMatch.state.evidenceQuery, noNext.state.evidenceQuery,
            "disabled next cannot change cursor")
        yes(noNext.state.announcement.contains("End of archived selected evidence"),
            "end of list spoken status")

        val noticeFocused = service.interact(wire, initial.state,
            PropagationOfflineComparisonInteraction.Focus(
                PropagationOfflineComparisonFocusTarget.PROVENANCE_NOTICES))
        eq(PropagationOfflineComparisonFocusTarget.PROVENANCE_NOTICES,
            noticeFocused.state.focusTarget, "provenance focus")
        eq(initial.noticeReadingOrder, noticeFocused.noticeReadingOrder,
            "focus does not hide provenance limitations")
        val records = mutableListOf<String>()
        var current = initial
        while (true) {
            records.addAll(current.sources.rows.map { it.identity })
            if (!current.sources.nextEnabled) break
            current = service.interact(
                wire, current.state, PropagationOfflineComparisonInteraction.NextSources)
        }
        eq(116, records.size, "traverse complete source collection")
        eq(records.size, records.toSet().size, "no repeated source rows")
        yes(!current.sources.nextEnabled, "last page disables next")
        val atEnd = service.interact(
            wire, current.state, PropagationOfflineComparisonInteraction.NextSources)
        eq(current.state.sourcesQuery, atEnd.state.sourcesQuery,
            "cannot paginate beyond end")
        val lastSourceQuery = current.state.sourcesQuery
        eq(lastSourceQuery, atEnd.state.sourcesQuery, "end does not mutate cursor")
        val reconciled = service.open(wire, before, after)
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            reconciled.display.header.originalReportsReconciliation,
            "optional originals only check historical consistency")
        eq(initial.noticeReadingOrder, reconciled.noticeReadingOrder,
            "reconciled reports still have mandatory warnings")
        val withOriginals = service.interact(
            wire, reconciled.state, PropagationOfflineComparisonInteraction.NextSources,
            before, after)
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            withOriginals.display.header.originalReportsReconciliation,
            "consistency check retained across state transition")
        val swapped = service.open(wire, after, before)
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH,
            swapped.display.header.originalReportsReconciliation, "mismatched original reports")
        eq(initial.noticeReadingOrder, swapped.noticeReadingOrder, "mismatch notices retained")
        val other = PropagationOfflineReportComparisonSerialization.serialize(before, before)
        denied("stale interaction on different comparison artifact") {
            service.interact(other, initial.state,
                PropagationOfflineComparisonInteraction.NextSources)
        }
        denied("invalid source filter limit") {
            service.interact(wire, initial.state,
                PropagationOfflineComparisonInteraction.FilterSources(limit = 101))
        }
        denied("empty selected evidence prefix") {
            service.interact(wire, initial.state,
                PropagationOfflineComparisonInteraction.FilterSelectedEvidence(prefix = ""))
        }
        denied("corrupt canonical comparison JSON") {
            service.interact(wire.copy(json = wire.json.dropLast(1)), initial.state,
                PropagationOfflineComparisonInteraction.NextSources)
        }
        denied("digest mismatch") {
            service.open(wire.copy(sha256Hex = "0".repeat(64)))
        }
        denied("unpaired original report") {
            service.open(wire, before = before)
        }
        denied("immutable accessibility source rows") {
            (initial.sources.rows as MutableList<PropagationOfflineComparisonAccessibleRow>).clear()
        }
        denied("immutable accessibility evidence rows") {
            (initial.selectedEvidence.rows as
                MutableList<PropagationOfflineComparisonAccessibleRow>).clear()
        }
        denied("immutable mandatory warnings") {
            (initial.noticeReadingOrder as
                MutableList<PropagationOfflineComparisonDisplayNotice>).clear()
        }
        println("CP-0008Z accessibility and state contract: PASS assertions=$assertions")
    }
}
