package dev.n0png.fieldops.core.propagation

import java.util.Collections

/** CP-0009C host-facing history state. NOT an Android Activity, APK or live RF view. */
data class PropagationArchiveWorkspaceState(
    val archive: PropagationOfflineReportArchive,
    val selectedSha256: String? = null,
    val comparisonBeforeSha256: String? = null,
    val comparisonAfterSha256: String? = null,
    val offset: Int = 0,
    val limit: Int = 25,
)

sealed class PropagationArchiveWorkspaceAction {
    data class Capture(val picture: PropagationOperatingPicture) : PropagationArchiveWorkspaceAction()
    data class Select(val id: String) : PropagationArchiveWorkspaceAction()
    data class Compare(val beforeId: String, val afterId: String) : PropagationArchiveWorkspaceAction()
    data class Delete(val id: String) : PropagationArchiveWorkspaceAction()
    data class SetPageSize(val limit: Int) : PropagationArchiveWorkspaceAction()
    object NextPage : PropagationArchiveWorkspaceAction()
    object PreviousPage : PropagationArchiveWorkspaceAction()
    object ClearComparison : PropagationArchiveWorkspaceAction()
}

data class PropagationArchiveWorkspaceRow(
    val sha256Hex: String,
    val queryUtcMillis: Long,
    val snapshotId: String?,
    val utf8ByteCount: Int,
    val selected: Boolean,
    val comparisonBefore: Boolean,
    val comparisonAfter: Boolean,
    val accessibleLabel: String,
)

data class PropagationArchiveWorkspaceScreen(
    val totalReports: Int,
    val totalUtf8Bytes: Long,
    val rows: List<PropagationArchiveWorkspaceRow>,
    val selectedReceipt: PropagationOfflineImportReceipt?,
    val comparison: PropagationWorkspaceHistoryComparison?,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val rangeLabel: String,
    val focusAnnouncement: String,
    val provenanceNotices: List<String>,
)

/** Host MUST escape row labels as plain text, never inject them as HTML. */
object PropagationArchiveWorkspaceService {
    fun open(policy: PropagationOfflineArchivePolicy = PropagationOfflineArchivePolicy()):
        PropagationArchiveWorkspaceState =
        PropagationArchiveWorkspaceState(PropagationOfflineReportArchiveService.empty(policy))

    fun apply(state: PropagationArchiveWorkspaceState, action: PropagationArchiveWorkspaceAction):
        PropagationArchiveWorkspaceState {
        checked(state)
        val api = PropagationOfflineReportArchiveService
        val updated = when (action) {
            is PropagationArchiveWorkspaceAction.Capture -> {
                // One caller-owned picture; no implicit refresh or store access.
                val capture = PropagationWorkspaceHistoryService.capture(action.picture)
                val inserted = api.append(state.archive, capture)
                val before = state.comparisonBeforeSha256?.takeIf { id ->
                    inserted.archive.entries.any { it.id == id }
                }
                val after = state.comparisonAfterSha256?.takeIf { id ->
                    inserted.archive.entries.any { it.id == id }
                }
                state.copy(
                    archive = inserted.archive,
                    selectedSha256 = capture.receipt.sha256Hex,
                    comparisonBeforeSha256 = if (before != null && after != null) before else null,
                    comparisonAfterSha256 = if (before != null && after != null) after else null,
                    offset = 0,
                )
            }
            is PropagationArchiveWorkspaceAction.Select -> {
                api.select(state.archive, action.id)
                state.copy(selectedSha256 = action.id)
            }
            is PropagationArchiveWorkspaceAction.Compare -> {
                require(action.beforeId != action.afterId) { "Comparison needs two captures" }
                api.compare(state.archive, action.beforeId, action.afterId)
                state.copy(comparisonBeforeSha256 = action.beforeId, comparisonAfterSha256 = action.afterId)
            }
            is PropagationArchiveWorkspaceAction.Delete -> {
                val remaining = api.remove(state.archive, action.id)
                val removedPair = action.id == state.comparisonBeforeSha256 ||
                    action.id == state.comparisonAfterSha256
                val lastOffset = if (remaining.entries.isEmpty()) 0 else
                    ((remaining.entries.size - 1) / state.limit) * state.limit
                state.copy(
                    archive = remaining,
                    selectedSha256 = state.selectedSha256?.takeUnless { it == action.id },
                    comparisonBeforeSha256 = if (removedPair) null else state.comparisonBeforeSha256,
                    comparisonAfterSha256 = if (removedPair) null else state.comparisonAfterSha256,
                    offset = minOf(state.offset, lastOffset),
                )
            }
            is PropagationArchiveWorkspaceAction.SetPageSize -> {
                require(action.limit in 1..100) { "Invalid page size" }
                state.copy(offset = 0, limit = action.limit)
            }
            PropagationArchiveWorkspaceAction.NextPage -> {
                val next = state.offset.toLong() + state.limit
                if (next >= state.archive.entries.size) state else state.copy(offset = next.toInt())
            }
            PropagationArchiveWorkspaceAction.PreviousPage ->
                state.copy(offset = maxOf(0, state.offset - state.limit))
            PropagationArchiveWorkspaceAction.ClearComparison ->
                state.copy(comparisonBeforeSha256 = null, comparisonAfterSha256 = null)
        }
        return checked(updated)
    }

    fun screen(state: PropagationArchiveWorkspaceState): PropagationArchiveWorkspaceScreen {
        checked(state)
        val api = PropagationOfflineReportArchiveService
        val selected = state.selectedSha256?.let { api.select(state.archive, it).receipt }
        val comparison = if (state.comparisonBeforeSha256 != null) api.compare(
            state.archive, state.comparisonBeforeSha256, state.comparisonAfterSha256!!
        ) else null
        val page = api.page(state.archive, state.offset, state.limit)
        val rows = page.map { item ->
            PropagationArchiveWorkspaceRow(
                sha256Hex = item.id,
                queryUtcMillis = item.queryUtcMillis,
                snapshotId = item.receipt.snapshotId,
                utf8ByteCount = item.artifact.utf8ByteCount,
                selected = item.id == state.selectedSha256,
                comparisonBefore = item.id == state.comparisonBeforeSha256,
                comparisonAfter = item.id == state.comparisonAfterSha256,
                accessibleLabel = "Archived propagation report UTC milliseconds " +
                    item.queryUtcMillis + ", content digest " + item.id +
                    ". Historical data, not current radio conditions.",
            )
        }
        val label = if (rows.isEmpty()) "No archived reports on this page" else
            "Archived reports " + (state.offset + 1) + " through " +
                (state.offset + rows.size) + " of " + state.archive.entries.size
        return PropagationArchiveWorkspaceScreen(
            totalReports = state.archive.entries.size,
            totalUtf8Bytes = state.archive.totalUtf8Bytes,
            rows = Collections.unmodifiableList(ArrayList(rows)),
            selectedReceipt = selected,
            comparison = comparison,
            previousEnabled = state.offset > 0,
            nextEnabled = state.offset.toLong() + rows.size < state.archive.entries.size,
            rangeLabel = label,
            focusAnnouncement = if (comparison != null)
                "Two archived reports selected for historical comparison." else if (selected != null)
                "Archived report selected." else "Offline history, not live propagation.",
            provenanceNotices = Collections.unmodifiableList(arrayListOf(
                "ARCHIVED DATA ONLY: not current CAT, USB, TX, RX or RF state.",
                "SHA-256 identifies content; it does not authenticate origin.",
                "Caller-owned memory only. No Android file or database persistence is proven.",
                "Comparison reconciles both original canonical reports.",
            )),
        )
    }

    private fun checked(state: PropagationArchiveWorkspaceState):
        PropagationArchiveWorkspaceState {
        PropagationOfflineReportArchiveService.validate(state.archive)
        require(state.offset >= 0 && state.limit in 1..100) { "Invalid page state" }
        state.selectedSha256?.let {
            PropagationOfflineReportArchiveService.select(state.archive, it)
        }
        require((state.comparisonBeforeSha256 == null) ==
            (state.comparisonAfterSha256 == null)) { "Incomplete comparison pair" }
        if (state.comparisonBeforeSha256 != null) {
            require(state.comparisonBeforeSha256 != state.comparisonAfterSha256) {
                "Cannot compare a report with itself"
            }
            PropagationOfflineReportArchiveService.compare(
                state.archive, state.comparisonBeforeSha256, state.comparisonAfterSha256!!
            )
        }
        return state
    }
}
