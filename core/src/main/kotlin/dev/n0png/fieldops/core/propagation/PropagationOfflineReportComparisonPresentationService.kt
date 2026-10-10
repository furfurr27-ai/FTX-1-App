package dev.n0png.fieldops.core.propagation

import java.util.Collections

/**
 * Strictly historical, presentation-ready selected-view comparison, never
 * a provider health signal or live RF measurement. Outer lists are unmodifiable;
 * nested Kotlin DTOs are not a hostile-code deep-immutability guarantee.
 */
data class PropagationOfflineComparisonChangeCounts(
    val addedToView: Int,
    val removedFromView: Int,
    val changedInView: Int,
    val unchanged: Int,
) {
    val total: Int get() = Math.addExact(Math.addExact(addedToView, removedFromView),
        Math.addExact(changedInView, unchanged))
    val changed: Int get() = Math.addExact(Math.addExact(addedToView, removedFromView), changedInView)
}

data class PropagationOfflineComparisonSourceRow(
    val sourceKey: String,
    val change: PropagationOfflineComparisonChange,
    val before: PropagationOfflineSourceReport?,
    val after: PropagationOfflineSourceReport?,
)

data class PropagationOfflineComparisonSelectedEvidenceRow(
    val evidenceId: String,
    val change: PropagationOfflineComparisonChange,
    val before: PropagationOfflineEvidenceIndexItem?,
    val after: PropagationOfflineEvidenceIndexItem?,
    val selectedIndexChanged: Boolean,
    val projectionContentChanged: Boolean,
)

/** Reconciliation describes report-to-derivative consistency, not authenticity. */
enum class PropagationOfflineComparisonReconciliation {
    ORIGINAL_REPORTS_NOT_SUPPLIED,
    ORIGINAL_REPORTS_MATCH,
    ORIGINAL_REPORTS_MISMATCH,
}

data class PropagationOfflineComparisonPresentation(
    val comparisonReceipt: PropagationOfflineComparisonImportReceipt,
    val beforeReportReceipt: PropagationOfflineImportReceipt,
    val afterReportReceipt: PropagationOfflineImportReceipt,
    val afterQueryTimeRelativeToBefore: PropagationOfflineComparisonTimeOrder,
    val snapshotChanged: Boolean,
    val summaryChanged: Boolean,
    val selectedAssessmentChanged: Boolean,
    val canonicalArtifactChanged: Boolean,
    val sourceCounts: PropagationOfflineComparisonChangeCounts,
    val selectedEvidenceCounts: PropagationOfflineComparisonChangeCounts,
    val sources: List<PropagationOfflineComparisonSourceRow>,
    val selectedEvidence: List<PropagationOfflineComparisonSelectedEvidenceRow>,
    val originalReportsReconciliation: PropagationOfflineComparisonReconciliation,
) {
    fun source(key: String): PropagationOfflineComparisonSourceRow? =
        sources.firstOrNull { it.sourceKey == key }
    fun evidence(id: String): PropagationOfflineComparisonSelectedEvidenceRow? =
        selectedEvidence.firstOrNull { it.evidenceId == id }
    fun sourcesWithChange(change: PropagationOfflineComparisonChange):
        List<PropagationOfflineComparisonSourceRow> =
        Collections.unmodifiableList(sources.filter { it.change == change })
    fun evidenceWithChange(change: PropagationOfflineComparisonChange):
        List<PropagationOfflineComparisonSelectedEvidenceRow> =
        Collections.unmodifiableList(selectedEvidence.filter { it.change == change })
    fun evidenceOfKind(kind: PropagationOfflineEvidenceKind):
        List<PropagationOfflineComparisonSelectedEvidenceRow> =
        Collections.unmodifiableList(selectedEvidence.filter {
            it.before?.kind == kind || it.after?.kind == kind
        })
}

/**
 * CP-0008W: pure deterministic presentation projection from a validated
 * canonical export (never caller-authored mutable DTOs). Optional original
 * reports permit independent derivative recalculation but not provider
 * authentication, cross-store atomicity, or actual RF verification.
 */
object PropagationOfflineReportComparisonPresentationService {
    fun present(
        artifact: PropagationOfflineSerializedComparison,
        before: PropagationOfflineSerializedReport? = null,
        after: PropagationOfflineSerializedReport? = null,
    ): PropagationOfflineComparisonPresentation {
        require((before == null) == (after == null)) {
            "Original reports must be supplied as a pair"
        }
        val imported = PropagationOfflineReportComparisonImportService.importComparison(artifact)
        val comparison = imported.comparison
        val reconciliation = if (before == null) {
            PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED
        } else {
            // These non-null assertions follow the pair invariant above.
            if (PropagationOfflineReportComparisonImportService.matchesOriginalReports(
                    imported, before, requireNotNull(after)))
                PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH
            else PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH
        }
        val sourceRows = comparison.sourceChanges.map {
            PropagationOfflineComparisonSourceRow(it.sourceKey, it.change, it.before, it.after)
        }
        val evidenceRows = comparison.evidenceChanges.map {
            PropagationOfflineComparisonSelectedEvidenceRow(
                it.evidenceId, it.change, it.before, it.after,
                it.selectedIndexChanged, it.projectionContentChanged)
        }
        return PropagationOfflineComparisonPresentation(
            comparisonReceipt = imported.receipt,
            beforeReportReceipt = imported.beforeReceipt,
            afterReportReceipt = imported.afterReceipt,
            afterQueryTimeRelativeToBefore = comparison.afterQueryTimeRelativeToBefore,
            snapshotChanged = comparison.snapshotChanged,
            summaryChanged = comparison.summaryChanged,
            selectedAssessmentChanged = comparison.selectedAssessmentChanged,
            canonicalArtifactChanged = comparison.canonicalArtifactChanged,
            sourceCounts = counts(sourceRows.map { it.change }),
            selectedEvidenceCounts = counts(evidenceRows.map { it.change }),
            sources = Collections.unmodifiableList(sourceRows),
            selectedEvidence = Collections.unmodifiableList(evidenceRows),
            originalReportsReconciliation = reconciliation,
        )
    }

    private fun counts(values: List<PropagationOfflineComparisonChange>) =
        PropagationOfflineComparisonChangeCounts(
            addedToView = values.count { it == PropagationOfflineComparisonChange.ADDED_TO_VIEW },
            removedFromView = values.count { it == PropagationOfflineComparisonChange.REMOVED_FROM_VIEW },
            changedInView = values.count { it == PropagationOfflineComparisonChange.CHANGED_IN_VIEW },
            unchanged = values.count { it == PropagationOfflineComparisonChange.UNCHANGED },
        )
}
