package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*

/** CP-0009A end-to-end host integration; synthetic evidence only. */
object PropagationWorkspaceHistoryTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0

    private fun eq(expected: Any?, actual: Any?, name: String) {
        assertions++
        check(expected == actual) { "$name expected=$expected actual=$actual" }
    }
    private fun yes(condition: Boolean, name: String) {
        assertions++
        check(condition) { name }
    }
    private fun rejected(name: String, body: () -> Unit) {
        assertions++
        check(runCatching(body).isFailure) { "Accepted invalid $name" }
    }

    private fun source(key: String) = PropagationRefreshSourceState(
        sourceKey = key,
        role = PropagationRefreshSourceRole.GENERIC,
        lastAttemptUtcMillis = NOW - 100,
        lastSuccessUtcMillis = NOW - 100,
        nextEligibleRefreshUtcMillis = NOW + 200,
    )

    private fun observation(id: String) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = "offline-solar",
            providerName = "Synthetic host fixture",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "host-v1",
            retrievedAtUtcMillis = NOW - 50,
        ),
        observedAtUtcMillis = NOW - 100,
        confidence = PropagationConfidence(
            0.85, PropagationConfidenceBasis.SYNTHETIC, "host"),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 2.0,
    )

    private fun picture(
        snapshotId: String? = null,
        sources: Int = 0,
        evidence: Int = 0,
        queriedAt: Long = NOW,
    ): PropagationOperatingPicture {
        val snapshots = InMemoryPropagationSnapshotStore()
        if (snapshotId != null) {
            snapshots.save(PropagationSnapshot(
                snapshotId, NOW - 20,
                solarGeomagnetic = (0 until evidence).map {
                    observation("solar-" + it.toString().padStart(3, '0'))
                }
            ))
        }
        val refresh = InMemoryPropagationRefreshStateStore()
        (0 until sources).forEach {
            refresh.save(source("state-" + it.toString().padStart(3, '0')))
        }
        return PropagationOperatingPictureService(snapshots, refresh)
            .read(PropagationProjectionQuery(queriedAt))
    }

    @JvmStatic fun main(args: Array<String>) {
        val bridge = PropagationWorkspaceHistoryService
        val noSnapshot = picture(sources = 1)
        val first = bridge.capture(noSnapshot)
        eq(first, bridge.capture(noSnapshot), "deterministic same captured picture")
        eq(NOW, first.receipt.reportQueryUtcMillis, "first report query UTC")
        eq(null, first.receipt.snapshotId, "no fabricated original snapshot")
        eq(1, first.receipt.sourceCount, "no-snapshot source count")
        eq(0, first.receipt.visibleEvidenceCount, "no cached evidence")
        eq(PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
            first.receipt.trust, "self consistency only")
        yes(!first.receipt.originAuthenticated &&
            !first.receipt.crossStoreAtomicityVerified,
            "no authenticated provider / store atomicity claims")
        eq(PropagationOfflineReportSerialization.CONTENT_TYPE,
            first.artifact.contentType, "canonical report media type")

        val picture = picture("snapshot-archived", sources = 35,
            evidence = 37, queriedAt = NOW + 120)
        val second = bridge.capture(picture)
        eq("snapshot-archived", second.receipt.snapshotId, "captured snapshot identity")
        eq(NOW + 120, second.receipt.reportQueryUtcMillis, "captured query UTC")
        eq(35, second.receipt.sourceCount, "sources retained")
        eq(37, second.receipt.visibleEvidenceCount, "selected evidence retained")
        val imported = PropagationOfflineReportImportService.importReport(second.artifact)
        eq(second.receipt, imported.receipt, "strict import confirms output")
        eq(second, bridge.capture(picture), "unchanged operating picture deterministic")

        val result = bridge.compare(first, second)
        eq(PropagationOfflineReportComparisonSerialization.CONTENT_TYPE,
            result.artifact.contentType, "separate canonical comparison media type")
        eq(first.receipt, result.screen.display.header.beforeReportReceipt,
            "exact before receipt")
        eq(second.receipt, result.screen.display.header.afterReportReceipt,
            "exact after receipt")
        eq(result.artifact.sha256Hex,
            result.screen.display.header.comparisonReceipt.sha256Hex,
            "exact derivative receipt")
        eq(PropagationOfflineComparisonTimeOrder.LATER,
            result.screen.display.header.afterQueryTimeRelativeToBefore,
            "query time preserved")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            result.screen.display.header.originalReportsReconciliation,
            "both original report artifacts independently checked")
        eq(35, result.screen.sources.matchingRows,
            "historical source view population")
        eq(37, result.screen.selectedEvidence.matchingRows,
            "historical selected evidence population")
        eq(25, result.screen.sources.rows.size, "first page bounded")
        eq(25, result.screen.selectedEvidence.rows.size, "first evidence page bounded")
        eq(4, result.screen.noticeReadingOrder.size,
            "all provenance and live RF disclaimers retained")
        yes(result.screen.noticeReadingOrder.any {
            it.code == PropagationOfflineComparisonDisplayNoticeCode.HISTORICAL_NOT_LIVE
        }, "historical vs live boundary explicit")
        yes(result.screen.noticeReadingOrder.any {
            it.code == PropagationOfflineComparisonDisplayNoticeCode.SELECTED_VIEW_NOT_PROVIDER_DELETION
        }, "selected view vs provider deletion boundary")
        eq(result, bridge.compare(first, second), "deterministic pair comparison")

        val nextSources = bridge.interact(result,
            PropagationOfflineComparisonInteraction.NextSources)
        eq(25L, nextSources.screen.state.sourcesQuery.offset,
            "next source cursor")
        eq(0L, nextSources.screen.state.evidenceQuery.offset,
            "independent evidence cursor")
        eq(26, nextSources.screen.sources.firstOrdinal, "screen source ordinal")
        eq(first, nextSources.before, "original before capture retained")
        eq(second, nextSources.after, "original after capture retained")
        eq(result.artifact, nextSources.artifact, "canonical derivative retained")
        eq(result.screen.state.sourcesQuery.offset, 0L,
            "previous screen not mutated")
        val nextEvidence = bridge.interact(nextSources,
            PropagationOfflineComparisonInteraction.NextSelectedEvidence)
        eq(25L, nextEvidence.screen.state.sourcesQuery.offset,
            "source page not changed by evidence navigation")
        eq(25L, nextEvidence.screen.state.evidenceQuery.offset,
            "evidence independently advanced")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            nextEvidence.screen.display.header.originalReportsReconciliation,
            "original receipt check preserved on transition")
        val filtered = bridge.interact(nextEvidence,
            PropagationOfflineComparisonInteraction.FilterSources(
                prefix = "state-00", limit = 4))
        eq(0L, filtered.screen.state.sourcesQuery.offset, "filter resets source page")
        eq(25L, filtered.screen.state.evidenceQuery.offset,
            "source filter preserves evidence page")
        eq(10, filtered.screen.sources.matchingRows, "exact prefix matched only ten sources")
        eq(4, filtered.screen.sources.rows.size, "bounded filtered source page")
        eq(result.screen.display.header.sourceCounts,
            filtered.screen.display.header.sourceCounts,
            "global source counts never reinterpret filtered count")
        eq(result.screen.noticeReadingOrder, filtered.screen.noticeReadingOrder,
            "provenance warnings cannot be hidden")
        val noticeFocus = bridge.interact(filtered,
            PropagationOfflineComparisonInteraction.Focus(
                PropagationOfflineComparisonFocusTarget.PROVENANCE_NOTICES))
        eq(PropagationOfflineComparisonFocusTarget.PROVENANCE_NOTICES,
            noticeFocus.screen.state.focusTarget, "host-only notice focus")
        yes(noticeFocus.screen.state.announcement.contains("limitations"),
            "host-only descriptive announcement")
        val reversed = bridge.compare(second, first)
        eq(PropagationOfflineComparisonTimeOrder.EARLIER,
            reversed.screen.display.header.afterQueryTimeRelativeToBefore,
            "earlier archive comparison allowed but correctly classified")
        eq(PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH,
            reversed.screen.display.header.originalReportsReconciliation,
            "reverse original receipt consistency")
        yes(reversed.screen.sources.rows.any {
            it.change == PropagationOfflineComparisonDisplayChangeLabel.REMOVED_FROM_SELECTED_VIEW
        }, "reverse removal remains selected view semantics")
        val identical = bridge.compare(second, second)
        eq(PropagationOfflineComparisonTimeOrder.SAME,
            identical.screen.display.header.afterQueryTimeRelativeToBefore,
            "same query time")
        yes(identical.screen.sources.rows.all {
            it.change == PropagationOfflineComparisonDisplayChangeLabel.UNCHANGED_WITHIN_SELECTED_VIEW
        }, "same snapshot unchanged")
        val alteredReceipt = second.copy(receipt = second.receipt.copy(sourceCount = 99))
        rejected("fake receipt") { bridge.compare(first, alteredReceipt) }
        rejected("fake query UTC") { bridge.compare(first,
            second.copy(receipt = second.receipt.copy(reportQueryUtcMillis = NOW))) }
        rejected("tampered payload") {
            bridge.compare(first, second.copy(
                artifact = second.artifact.copy(json = second.artifact.json.dropLast(1))))
        }
        rejected("tampered digest") {
            bridge.compare(first, second.copy(
                artifact = second.artifact.copy(sha256Hex = "0".repeat(64))))
        }
        rejected("mutated derivative in interaction") {
            bridge.interact(result.copy(artifact = result.artifact.copy(
                sha256Hex = "0".repeat(64))),
                PropagationOfflineComparisonInteraction.NextSources)
        }
        rejected("swapped originals in interaction") {
            bridge.interact(result.copy(before = second, after = first),
                PropagationOfflineComparisonInteraction.NextSources)
        }
        rejected("fake screenshot state") {
            bridge.interact(result.copy(screen = result.screen.copy(
                state = result.screen.state.copy(comparisonSha256 = "0".repeat(64)))),
                PropagationOfflineComparisonInteraction.NextSources)
        }
        rejected("invalid source query") {
            bridge.interact(result,
                PropagationOfflineComparisonInteraction.FilterSources(limit = 101))
        }
        eq(first, result.before, "original capture preserved throughout tests")
        eq(second, result.after, "post capture preserved throughout tests")
        println("CP-0009A workspace history bridge: PASS assertions=$assertions")
    }
}
