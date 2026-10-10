package dev.n0png.fieldops.core.propagation

import java.util.Collections

/**
 * CP-0008Y platform-neutral render contract. No Android, UI toolkit, storage,
 * wall clock, network, provider, or RF dependencies. Never render raw IDs as
 * HTML: hosts must escape untrusted identifier and provenance strings.
 */
enum class PropagationOfflineComparisonDisplayNoticeCode {
    HISTORICAL_NOT_LIVE,
    SELECTED_VIEW_NOT_PROVIDER_DELETION,
    CHECKSUM_NOT_AUTHENTICATION,
    CROSS_STORE_ATOMICITY_UNVERIFIED,
}

data class PropagationOfflineComparisonDisplayNotice(
    val code: PropagationOfflineComparisonDisplayNoticeCode,
    val text: String,
)

/** Labels are controlled vocabulary, never derived from provider text. */
enum class PropagationOfflineComparisonDisplayChangeLabel {
    ADDED_TO_SELECTED_VIEW,
    REMOVED_FROM_SELECTED_VIEW,
    CHANGED_WITHIN_SELECTED_VIEW,
    UNCHANGED_WITHIN_SELECTED_VIEW,
}

data class PropagationOfflineComparisonDisplaySourceRow(
    val sourceKey: String,
    val label: PropagationOfflineComparisonDisplayChangeLabel,
    val change: PropagationOfflineComparisonChange,
    /** Retain status and timing; these values describe archived reports only. */
    val before: PropagationOfflineSourceReport?,
    val after: PropagationOfflineSourceReport?,
)

data class PropagationOfflineComparisonDisplayEvidenceRow(
    val evidenceId: String,
    val label: PropagationOfflineComparisonDisplayChangeLabel,
    val change: PropagationOfflineComparisonChange,
    /** These indexes refer only to selected evidence, never provider inventory. */
    val before: PropagationOfflineEvidenceIndexItem?,
    val after: PropagationOfflineEvidenceIndexItem?,
    val selectedIndexChanged: Boolean,
    val projectionContentChanged: Boolean,
)

data class PropagationOfflineComparisonDisplaySourceSection(
    val title: String,
    val query: PropagationOfflineComparisonSourcePageQuery,
    val matchingRows: Int,
    val globalRows: Int,
    val rows: List<PropagationOfflineComparisonDisplaySourceRow>,
    val previousQuery: PropagationOfflineComparisonSourcePageQuery?,
    val nextQuery: PropagationOfflineComparisonSourcePageQuery?,
)

data class PropagationOfflineComparisonDisplayEvidenceSection(
    val title: String,
    val query: PropagationOfflineComparisonEvidencePageQuery,
    val matchingRows: Int,
    val globalRows: Int,
    val rows: List<PropagationOfflineComparisonDisplayEvidenceRow>,
    val previousQuery: PropagationOfflineComparisonEvidencePageQuery?,
    val nextQuery: PropagationOfflineComparisonEvidencePageQuery?,
)

/**
 * Consumers must show notices alongside any displayed source/evidence pages.
 * No field in this display projection represents current on-air conditions.
 */
data class PropagationOfflineComparisonDisplayModel(
    val header: PropagationOfflineComparisonPageHeader,
    val historicalTitle: String,
    val reconciliationLabel: String,
    val notices: List<PropagationOfflineComparisonDisplayNotice>,
    val sources: PropagationOfflineComparisonDisplaySourceSection,
    val selectedEvidence: PropagationOfflineComparisonDisplayEvidenceSection,
)

object PropagationOfflineReportComparisonDisplayService {
    private val requiredNotices = Collections.unmodifiableList(listOf(
        PropagationOfflineComparisonDisplayNotice(
            PropagationOfflineComparisonDisplayNoticeCode.HISTORICAL_NOT_LIVE,
            "Historical offline report comparison; not current propagation, source health, or RF state."),
        PropagationOfflineComparisonDisplayNotice(
            PropagationOfflineComparisonDisplayNoticeCode.SELECTED_VIEW_NOT_PROVIDER_DELETION,
            "Added or removed means selected in one archived report, not created or deleted by a provider."),
        PropagationOfflineComparisonDisplayNotice(
            PropagationOfflineComparisonDisplayNoticeCode.CHECKSUM_NOT_AUTHENTICATION,
            "Unkeyed SHA-256 establishes self-consistency, not authenticated origin or a signature."),
        PropagationOfflineComparisonDisplayNotice(
            PropagationOfflineComparisonDisplayNoticeCode.CROSS_STORE_ATOMICITY_UNVERIFIED,
            "Cross-store atomicity and live phone/radio/RF behavior are unverified."),
    ))

    fun display(
        artifact: PropagationOfflineSerializedComparison,
        sources: PropagationOfflineComparisonSourcePageQuery =
            PropagationOfflineComparisonSourcePageQuery(),
        evidence: PropagationOfflineComparisonEvidencePageQuery =
            PropagationOfflineComparisonEvidencePageQuery(),
        before: PropagationOfflineSerializedReport? = null,
        after: PropagationOfflineSerializedReport? = null,
    ): PropagationOfflineComparisonDisplayModel {
        // Always strict-decode the canonical artifact via X -> W -> V -> U.
        val paged = PropagationOfflineReportComparisonPaginationService.page(
            artifact, sources, evidence, before, after)
        return PropagationOfflineComparisonDisplayModel(
            header = paged.header,
            historicalTitle = "Archived propagation comparison",
            reconciliationLabel = when (paged.header.originalReportsReconciliation) {
                PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_NOT_SUPPLIED ->
                    "Original reports not supplied; comparison self-consistency only"
                PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH ->
                    "Comparison agrees with both supplied original reports; origin unauthenticated"
                PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MISMATCH ->
                    "Comparison differs from supplied original reports; verify offline artifacts"
            },
            notices = requiredNotices,
            sources = PropagationOfflineComparisonDisplaySourceSection(
                title = "Source changes in archived reports",
                query = sources,
                matchingRows = paged.sources.matchingRows,
                globalRows = paged.header.sourceCounts.total,
                rows = Collections.unmodifiableList(paged.sources.rows.map {
                    PropagationOfflineComparisonDisplaySourceRow(
                        it.sourceKey, label(it.change), it.change, it.before, it.after)
                }),
                previousQuery = previous(sources.offset, sources.limit)?.let {
                    sources.copy(offset = it)
                },
                nextQuery = paged.sources.nextOffset?.let { sources.copy(offset = it) },
            ),
            selectedEvidence = PropagationOfflineComparisonDisplayEvidenceSection(
                title = "Evidence selected in archived reports",
                query = evidence,
                matchingRows = paged.selectedEvidence.matchingRows,
                globalRows = paged.header.selectedEvidenceCounts.total,
                rows = Collections.unmodifiableList(paged.selectedEvidence.rows.map {
                    PropagationOfflineComparisonDisplayEvidenceRow(
                        it.evidenceId, label(it.change), it.change, it.before, it.after,
                        it.selectedIndexChanged, it.projectionContentChanged)
                }),
                previousQuery = previous(evidence.offset, evidence.limit)?.let {
                    evidence.copy(offset = it)
                },
                nextQuery = paged.selectedEvidence.nextOffset?.let {
                    evidence.copy(offset = it)
                },
            ),
        )
    }

    private fun previous(offset: Long, limit: Int): Long? =
        if (offset == 0L) null else maxOf(0L, offset - limit.toLong())

    private fun label(change: PropagationOfflineComparisonChange) = when (change) {
        PropagationOfflineComparisonChange.ADDED_TO_VIEW ->
            PropagationOfflineComparisonDisplayChangeLabel.ADDED_TO_SELECTED_VIEW
        PropagationOfflineComparisonChange.REMOVED_FROM_VIEW ->
            PropagationOfflineComparisonDisplayChangeLabel.REMOVED_FROM_SELECTED_VIEW
        PropagationOfflineComparisonChange.CHANGED_IN_VIEW ->
            PropagationOfflineComparisonDisplayChangeLabel.CHANGED_WITHIN_SELECTED_VIEW
        PropagationOfflineComparisonChange.UNCHANGED ->
            PropagationOfflineComparisonDisplayChangeLabel.UNCHANGED_WITHIN_SELECTED_VIEW
    }
}
