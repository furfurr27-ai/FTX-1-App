package dev.n0png.fieldops.core.propagation

import java.util.Collections

/**
 * CP-0008X historical presentation pagination. Zero-based offsets are indexes
 * into the *filtered* stable CP-0008W row order, never provider positions.
 */
data class PropagationOfflineComparisonSourcePageQuery(
    val offset: Long = 0L,
    val limit: Int = 25,
    val change: PropagationOfflineComparisonChange? = null,
    val sourceKeyPrefix: String? = null,
) {
    init {
        require(offset >= 0L) { "Source offset cannot be negative" }
        require(limit in 1..100) { "Source limit must be within 1..100" }
        require(sourceKeyPrefix == null || sourceKeyPrefix.length in 1..128) {
            "Source key prefix must be 1..128 UTF-16 units"
        }
    }
}

data class PropagationOfflineComparisonEvidencePageQuery(
    val offset: Long = 0L,
    val limit: Int = 25,
    val change: PropagationOfflineComparisonChange? = null,
    val kind: PropagationOfflineEvidenceKind? = null,
    val evidenceIdPrefix: String? = null,
) {
    init {
        require(offset >= 0L) { "Evidence offset cannot be negative" }
        require(limit in 1..100) { "Evidence limit must be within 1..100" }
        require(evidenceIdPrefix == null || evidenceIdPrefix.length in 1..128) {
            "Evidence ID prefix must be 1..128 UTF-16 units"
        }
    }
}

/** Rows are bounded, detached outer unmodifiable copies. */
data class PropagationOfflineComparisonPage<T>(
    val offset: Long,
    val limit: Int,
    val matchingRows: Int,
    val rows: List<T>,
    val nextOffset: Long?,
) {
    val hasMore: Boolean get() = nextOffset != null
}

/** Only metadata and global tallies: the unbounded original rows are not exposed. */
data class PropagationOfflineComparisonPageHeader(
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
    val originalReportsReconciliation: PropagationOfflineComparisonReconciliation,
)

data class PropagationOfflineComparisonPagedPresentation(
    val header: PropagationOfflineComparisonPageHeader,
    val sources: PropagationOfflineComparisonPage<PropagationOfflineComparisonSourceRow>,
    val selectedEvidence: PropagationOfflineComparisonPage<PropagationOfflineComparisonSelectedEvidenceRow>,
)

/**
 * Pure read-only host service. Each call performs a strict CP-0008W presentation
 * from a canonical V1 comparison artifact. Original reports are an optional
 * paired consistency check, never authenticated origin, live propagation/RF,
 * or source-store atomicity.
 *
 * Filtering uses exact case-sensitive prefixes; no locale, wall clock, network,
 * cache, mutable paging cursor, data-store or radio is involved.
 */
object PropagationOfflineReportComparisonPaginationService {
    fun page(
        artifact: PropagationOfflineSerializedComparison,
        sources: PropagationOfflineComparisonSourcePageQuery =
            PropagationOfflineComparisonSourcePageQuery(),
        evidence: PropagationOfflineComparisonEvidencePageQuery =
            PropagationOfflineComparisonEvidencePageQuery(),
        before: PropagationOfflineSerializedReport? = null,
        after: PropagationOfflineSerializedReport? = null,
    ): PropagationOfflineComparisonPagedPresentation {
        val original = PropagationOfflineReportComparisonPresentationService.present(
            artifact, before, after)
        val filteredSources = original.sources.filter {
            (sources.change == null || it.change == sources.change) &&
                (sources.sourceKeyPrefix == null ||
                    it.sourceKey.startsWith(sources.sourceKeyPrefix))
        }
        val filteredEvidence = original.selectedEvidence.filter {
            (evidence.change == null || it.change == evidence.change) &&
                (evidence.kind == null ||
                    it.before?.kind == evidence.kind || it.after?.kind == evidence.kind) &&
                (evidence.evidenceIdPrefix == null ||
                    it.evidenceId.startsWith(evidence.evidenceIdPrefix))
        }
        return PropagationOfflineComparisonPagedPresentation(
            header = PropagationOfflineComparisonPageHeader(
                original.comparisonReceipt,
                original.beforeReportReceipt,
                original.afterReportReceipt,
                original.afterQueryTimeRelativeToBefore,
                original.snapshotChanged,
                original.summaryChanged,
                original.selectedAssessmentChanged,
                original.canonicalArtifactChanged,
                original.sourceCounts,
                original.selectedEvidenceCounts,
                original.originalReportsReconciliation,
            ),
            sources = bounded(filteredSources, sources.offset, sources.limit),
            selectedEvidence = bounded(filteredEvidence, evidence.offset, evidence.limit),
        )
    }

    /** Long arithmetic avoids overflow even for Long.MAX_VALUE offsets. */
    private fun <T> bounded(items: List<T>, offset: Long, limit: Int):
        PropagationOfflineComparisonPage<T> {
        val count = items.size
        if (offset >= count.toLong()) {
            return PropagationOfflineComparisonPage(offset, limit, count, emptyList(), null)
        }
        val start = offset.toInt()
        val end = minOf(count.toLong(), offset + limit.toLong()).toInt()
        val copied = Collections.unmodifiableList(ArrayList(items.subList(start, end)))
        return PropagationOfflineComparisonPage(
            offset = offset,
            limit = limit,
            matchingRows = count,
            rows = copied,
            nextOffset = if (end < count) end.toLong() else null,
        )
    }
}
