package dev.n0png.fieldops.core.propagation

import java.util.Collections

/** Historical display focus intent only; never a platform focus command. */
enum class PropagationOfflineComparisonFocusTarget {
    SUMMARY, SOURCES, SELECTED_EVIDENCE, PROVENANCE_NOTICES,
}

sealed class PropagationOfflineComparisonInteraction {
    object NextSources : PropagationOfflineComparisonInteraction()
    object PreviousSources : PropagationOfflineComparisonInteraction()
    object NextSelectedEvidence : PropagationOfflineComparisonInteraction()
    object PreviousSelectedEvidence : PropagationOfflineComparisonInteraction()
    data class FilterSources(
        val change: PropagationOfflineComparisonChange? = null,
        val prefix: String? = null,
        val limit: Int = 25,
    ) : PropagationOfflineComparisonInteraction()
    data class FilterSelectedEvidence(
        val change: PropagationOfflineComparisonChange? = null,
        val kind: PropagationOfflineEvidenceKind? = null,
        val prefix: String? = null,
        val limit: Int = 25,
    ) : PropagationOfflineComparisonInteraction()
    data class Focus(val target: PropagationOfflineComparisonFocusTarget) :
        PropagationOfflineComparisonInteraction()
}

/**
 * State is bound to one canonical comparison export by its UNKEYED digest and
 * validated afresh with every transition. This is not source authentication.
 */
data class PropagationOfflineComparisonInteractionState(
    val comparisonSha256: String,
    val sourcesQuery: PropagationOfflineComparisonSourcePageQuery,
    val evidenceQuery: PropagationOfflineComparisonEvidencePageQuery,
    val focusTarget: PropagationOfflineComparisonFocusTarget,
    /** Controlled narration, not proof of a real screen-reader announcement. */
    val announcement: String,
)

/** Plain text for hosts to escape, not HTML, ARIA attributes, or Android semantics. */
data class PropagationOfflineComparisonAccessibleRow(
    val identity: String,
    val label: String,
    val change: PropagationOfflineComparisonDisplayChangeLabel,
)

data class PropagationOfflineComparisonAccessibleSection(
    val heading: String,
    val rangeLabel: String,
    val matchingRows: Int,
    val firstOrdinal: Int?,
    val lastOrdinal: Int?,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val rows: List<PropagationOfflineComparisonAccessibleRow>,
)

data class PropagationOfflineComparisonAccessibleScreen(
    val state: PropagationOfflineComparisonInteractionState,
    /** CP-0008Y notices must never be hidden by interaction or filtering. */
    val display: PropagationOfflineComparisonDisplayModel,
    val sources: PropagationOfflineComparisonAccessibleSection,
    val selectedEvidence: PropagationOfflineComparisonAccessibleSection,
    val noticeReadingOrder: List<PropagationOfflineComparisonDisplayNotice>,
)

/**
 * CP-0008Z pure host-contract state machine. No Android screen reader, actual
 * focus move, persistence, RF state, live provider, network, phone, or radio.
 * Host must escape source IDs and evidence IDs as untrusted text.
 */
object PropagationOfflineReportComparisonInteractionService {
    fun open(
        artifact: PropagationOfflineSerializedComparison,
        before: PropagationOfflineSerializedReport? = null,
        after: PropagationOfflineSerializedReport? = null,
    ): PropagationOfflineComparisonAccessibleScreen {
        val display = PropagationOfflineReportComparisonDisplayService.display(
            artifact, before = before, after = after)
        val state = PropagationOfflineComparisonInteractionState(
            comparisonSha256 = display.header.comparisonReceipt.sha256Hex,
            sourcesQuery = display.sources.query,
            evidenceQuery = display.selectedEvidence.query,
            focusTarget = PropagationOfflineComparisonFocusTarget.SUMMARY,
            announcement = "Archived propagation comparison. Historical data, not live RF.",
        )
        return screen(state, display)
    }

    fun interact(
        artifact: PropagationOfflineSerializedComparison,
        state: PropagationOfflineComparisonInteractionState,
        action: PropagationOfflineComparisonInteraction,
        before: PropagationOfflineSerializedReport? = null,
        after: PropagationOfflineSerializedReport? = null,
    ): PropagationOfflineComparisonAccessibleScreen {
        // CP-0008Y delegates to strict canonical CP-0008X/W/V/U validation.
        val old = PropagationOfflineReportComparisonDisplayService.display(
            artifact, state.sourcesQuery, state.evidenceQuery, before, after)
        require(old.header.comparisonReceipt.sha256Hex == state.comparisonSha256) {
            "Historical comparison interaction belongs to another canonical artifact"
        }
        val candidate = when (action) {
            is PropagationOfflineComparisonInteraction.NextSources ->
                state.copy(
                    sourcesQuery = old.sources.nextQuery ?: state.sourcesQuery,
                    focusTarget = PropagationOfflineComparisonFocusTarget.SOURCES,
                    announcement = if (old.sources.nextQuery == null)
                        "End of archived source changes." else "Next archived source page.",
                )
            is PropagationOfflineComparisonInteraction.PreviousSources ->
                state.copy(
                    sourcesQuery = old.sources.previousQuery ?: state.sourcesQuery,
                    focusTarget = PropagationOfflineComparisonFocusTarget.SOURCES,
                    announcement = if (old.sources.previousQuery == null)
                        "Start of archived source changes." else "Previous archived source page.",
                )
            is PropagationOfflineComparisonInteraction.NextSelectedEvidence ->
                state.copy(
                    evidenceQuery = old.selectedEvidence.nextQuery ?: state.evidenceQuery,
                    focusTarget = PropagationOfflineComparisonFocusTarget.SELECTED_EVIDENCE,
                    announcement = if (old.selectedEvidence.nextQuery == null)
                        "End of archived selected evidence." else "Next archived selected evidence page.",
                )
            is PropagationOfflineComparisonInteraction.PreviousSelectedEvidence ->
                state.copy(
                    evidenceQuery = old.selectedEvidence.previousQuery ?: state.evidenceQuery,
                    focusTarget = PropagationOfflineComparisonFocusTarget.SELECTED_EVIDENCE,
                    announcement = if (old.selectedEvidence.previousQuery == null)
                        "Start of archived selected evidence." else
                        "Previous archived selected evidence page.",
                )
            is PropagationOfflineComparisonInteraction.FilterSources ->
                state.copy(
                    sourcesQuery = PropagationOfflineComparisonSourcePageQuery(
                        offset = 0, limit = action.limit, change = action.change,
                        sourceKeyPrefix = action.prefix),
                    focusTarget = PropagationOfflineComparisonFocusTarget.SOURCES,
                    announcement = "Archived source filters updated. Results begin at first page.",
                )
            is PropagationOfflineComparisonInteraction.FilterSelectedEvidence ->
                state.copy(
                    evidenceQuery = PropagationOfflineComparisonEvidencePageQuery(
                        offset = 0, limit = action.limit, change = action.change,
                        kind = action.kind, evidenceIdPrefix = action.prefix),
                    focusTarget = PropagationOfflineComparisonFocusTarget.SELECTED_EVIDENCE,
                    announcement = "Archived selected evidence filters updated. Results begin at first page.",
                )
            is PropagationOfflineComparisonInteraction.Focus ->
                state.copy(
                    focusTarget = action.target,
                    announcement = if (action.target ==
                        PropagationOfflineComparisonFocusTarget.PROVENANCE_NOTICES)
                        "Historical data and provenance limitations." else
                        "Archived comparison focus changed.",
                )
        }
        val updated = PropagationOfflineReportComparisonDisplayService.display(
            artifact, candidate.sourcesQuery, candidate.evidenceQuery, before, after)
        require(updated.header.comparisonReceipt.sha256Hex == candidate.comparisonSha256)
        return screen(candidate, updated)
    }

    private fun screen(
        state: PropagationOfflineComparisonInteractionState,
        display: PropagationOfflineComparisonDisplayModel,
    ): PropagationOfflineComparisonAccessibleScreen {
        val sources = display.sources
        val evidence = display.selectedEvidence
        return PropagationOfflineComparisonAccessibleScreen(
            state = state,
            display = display,
            sources = section(
                title = sources.title,
                offset = sources.query.offset,
                matching = sources.matchingRows,
                prev = sources.previousQuery != null,
                next = sources.nextQuery != null,
                rows = sources.rows.map {
                    PropagationOfflineComparisonAccessibleRow(
                        it.sourceKey, "Archived source " + it.sourceKey +
                            ", " + changeText(it.label), it.label)
                },
            ),
            selectedEvidence = section(
                title = evidence.title,
                offset = evidence.query.offset,
                matching = evidence.matchingRows,
                prev = evidence.previousQuery != null,
                next = evidence.nextQuery != null,
                rows = evidence.rows.map {
                    PropagationOfflineComparisonAccessibleRow(
                        it.evidenceId, "Archived selected evidence " + it.evidenceId +
                            ", " + changeText(it.label), it.label)
                },
            ),
            noticeReadingOrder = Collections.unmodifiableList(ArrayList(display.notices)),
        )
    }

    private fun section(
        title: String, offset: Long, matching: Int, prev: Boolean, next: Boolean,
        rows: List<PropagationOfflineComparisonAccessibleRow>,
    ): PropagationOfflineComparisonAccessibleSection {
        val first = if (rows.isEmpty()) null else (offset + 1L).toInt()
        val last = if (rows.isEmpty()) null else (offset + rows.size.toLong()).toInt()
        val range = if (rows.isEmpty()) "No matching archived rows" else
            "Archived rows $first through $last of $matching matching rows"
        return PropagationOfflineComparisonAccessibleSection(
            heading = title, rangeLabel = range, matchingRows = matching,
            firstOrdinal = first, lastOrdinal = last, previousEnabled = prev,
            nextEnabled = next, rows = Collections.unmodifiableList(ArrayList(rows)),
        )
    }

    private fun changeText(label: PropagationOfflineComparisonDisplayChangeLabel) =
        when (label) {
            PropagationOfflineComparisonDisplayChangeLabel.ADDED_TO_SELECTED_VIEW ->
                "added to selected view"
            PropagationOfflineComparisonDisplayChangeLabel.REMOVED_FROM_SELECTED_VIEW ->
                "removed from selected view"
            PropagationOfflineComparisonDisplayChangeLabel.CHANGED_WITHIN_SELECTED_VIEW ->
                "changed within selected view"
            PropagationOfflineComparisonDisplayChangeLabel.UNCHANGED_WITHIN_SELECTED_VIEW ->
                "unchanged within selected view"
        }
}
