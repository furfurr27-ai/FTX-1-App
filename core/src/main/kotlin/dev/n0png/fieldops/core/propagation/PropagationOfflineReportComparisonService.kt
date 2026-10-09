package dev.n0png.fieldops.core.propagation

/**
 * Comparison is between two retained, unauthenticated offline artifacts.
 * A missing selected item means "not in this filtered report", NOT that the
 * underlying RF observation disappeared, provider changed, or signal worsened.
 */
enum class PropagationOfflineComparisonChange {
    ADDED_TO_VIEW,
    REMOVED_FROM_VIEW,
    CHANGED_IN_VIEW,
    UNCHANGED,
}

enum class PropagationOfflineComparisonTimeOrder {
    EARLIER,
    SAME,
    LATER,
}

data class PropagationOfflineSourceComparison(
    val sourceKey: String,
    val change: PropagationOfflineComparisonChange,
    val before: PropagationOfflineSourceReport?,
    val after: PropagationOfflineSourceReport?,
)

data class PropagationOfflineEvidenceComparison(
    val evidenceId: String,
    val change: PropagationOfflineComparisonChange,
    val before: PropagationOfflineEvidenceIndexItem?,
    val after: PropagationOfflineEvidenceIndexItem?,
    /** Includes measured values, paths, and projection metadata, not just index fields. */
    val projectionContentChanged: Boolean,
    /** Flags selected index/provenance drift even when the projection kind is stable. */
    val selectedIndexChanged: Boolean,
)

data class PropagationOfflineReportComparison(
    val beforeReceipt: PropagationOfflineImportReceipt,
    val afterReceipt: PropagationOfflineImportReceipt,
    /** Relative order of ORIGINAL query timestamps; never the import time. */
    val afterQueryTimeRelativeToBefore: PropagationOfflineComparisonTimeOrder,
    val snapshotChanged: Boolean,
    val summaryChanged: Boolean,
    val selectedAssessmentChanged: Boolean,
    val canonicalArtifactChanged: Boolean,
    val sourceChanges: List<PropagationOfflineSourceComparison>,
    val evidenceChanges: List<PropagationOfflineEvidenceComparison>,
    val originAuthenticated: Boolean = false,
    val crossStoreAtomicityVerified: Boolean = false,
) {
    fun changedSources(): List<PropagationOfflineSourceComparison> =
        sourceChanges.filter { it.change != PropagationOfflineComparisonChange.UNCHANGED }

    fun changedEvidence(): List<PropagationOfflineEvidenceComparison> =
        evidenceChanges.filter { it.change != PropagationOfflineComparisonChange.UNCHANGED }
}

/**
 * No stores, clock, refresh, projection recomputation, filesystem, network,
 * credentials, phone, or radio. Both inputs must pass CP-0008R import, which
 * verifies CP-0008Q typed provenance and CP-0008P canonical V1 integrity.
 */
object PropagationOfflineReportComparisonService {
    fun compare(
        beforeArtifact: PropagationOfflineSerializedReport,
        afterArtifact: PropagationOfflineSerializedReport,
    ): PropagationOfflineReportComparison {
        val before = PropagationOfflineReportImportService.importReport(beforeArtifact)
        val after = PropagationOfflineReportImportService.importReport(afterArtifact)
        return compareImported(before, after)
    }

    /** Revalidates imported objects against their receipts in case of unsafe mutation. */
    fun compareImported(
        before: PropagationOfflineImportedReport,
        after: PropagationOfflineImportedReport,
    ): PropagationOfflineReportComparison {
        val oldWire = PropagationOfflineReportSerialization.serialize(before.report)
        val newWire = PropagationOfflineReportSerialization.serialize(after.report)
        require(oldWire.contentType == before.receipt.contentType &&
            oldWire.wireVersion == before.receipt.wireVersion &&
            oldWire.utf8ByteCount == before.receipt.utf8ByteCount &&
            oldWire.sha256Hex == before.receipt.sha256Hex) {
            "First imported report no longer matches its receipt"
        }
        require(newWire.contentType == after.receipt.contentType &&
            newWire.wireVersion == after.receipt.wireVersion &&
            newWire.utf8ByteCount == after.receipt.utf8ByteCount &&
            newWire.sha256Hex == after.receipt.sha256Hex) {
            "Second imported report no longer matches its receipt"
        }
        require(!before.receipt.originAuthenticated &&
            !after.receipt.originAuthenticated &&
            !before.receipt.crossStoreAtomicityVerified &&
            !after.receipt.crossStoreAtomicityVerified &&
            before.receipt.trust == PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT &&
            after.receipt.trust == PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT) {
            "Unsupported offline import trust claim"
        }

        val oldSources = before.sources.associateBy { it.sourceKey }
        val newSources = after.sources.associateBy { it.sourceKey }
        require(oldSources.size == before.sources.size && newSources.size == after.sources.size) {
            "Duplicate source identity"
        }
        val sourceChanges = (oldSources.keys + newSources.keys).toSortedSet().map { key ->
            val first = oldSources[key]
            val second = newSources[key]
            PropagationOfflineSourceComparison(
                sourceKey = key, change = change(first, second),
                before = first, after = second
            )
        }

        val oldEvidence = before.visibleEvidenceIndex.associateBy { it.evidenceId }
        val newEvidence = after.visibleEvidenceIndex.associateBy { it.evidenceId }
        require(oldEvidence.size == before.visibleEvidenceIndex.size &&
            newEvidence.size == after.visibleEvidenceIndex.size) {
            "Duplicate selected evidence identity"
        }
        val oldProjections = selectedProjections(before.workspace)
        val newProjections = selectedProjections(after.workspace)
        require(oldEvidence.keys == oldProjections.keys &&
            newEvidence.keys == newProjections.keys) {
            "Imported selected projections and indexes differ"
        }
        val evidenceChanges = (oldEvidence.keys + newEvidence.keys).toSortedSet().map { id ->
            val first = oldEvidence[id]
            val second = newEvidence[id]
            val payloadChanged = first != null && second != null &&
                oldProjections[id] != newProjections[id]
            val indexChanged = first != null && second != null && first != second
            PropagationOfflineEvidenceComparison(
                evidenceId = id,
                change = when {
                    first == null -> PropagationOfflineComparisonChange.ADDED_TO_VIEW
                    second == null -> PropagationOfflineComparisonChange.REMOVED_FROM_VIEW
                    payloadChanged || indexChanged ->
                        PropagationOfflineComparisonChange.CHANGED_IN_VIEW
                    else -> PropagationOfflineComparisonChange.UNCHANGED
                },
                before = first, after = second,
                projectionContentChanged = payloadChanged,
                selectedIndexChanged = indexChanged,
            )
        }

        val queryOrder = when {
            after.receipt.reportQueryUtcMillis < before.receipt.reportQueryUtcMillis ->
                PropagationOfflineComparisonTimeOrder.EARLIER
            after.receipt.reportQueryUtcMillis > before.receipt.reportQueryUtcMillis ->
                PropagationOfflineComparisonTimeOrder.LATER
            else -> PropagationOfflineComparisonTimeOrder.SAME
        }
        return PropagationOfflineReportComparison(
            beforeReceipt = before.receipt, afterReceipt = after.receipt,
            afterQueryTimeRelativeToBefore = queryOrder,
            snapshotChanged = before.snapshot != after.snapshot,
            summaryChanged = before.report.summary != after.report.summary,
            selectedAssessmentChanged =
                before.workspace?.selectedPathAssessment !=
                    after.workspace?.selectedPathAssessment,
            canonicalArtifactChanged = oldWire != newWire,
            sourceChanges = sourceChanges,
            evidenceChanges = evidenceChanges,
        )
    }

    private fun <T : Any> change(first: T?, second: T?):
        PropagationOfflineComparisonChange = when {
        first == null -> PropagationOfflineComparisonChange.ADDED_TO_VIEW
        second == null -> PropagationOfflineComparisonChange.REMOVED_FROM_VIEW
        first == second -> PropagationOfflineComparisonChange.UNCHANGED
        else -> PropagationOfflineComparisonChange.CHANGED_IN_VIEW
    }

    private fun selectedProjections(workspace: PropagationOfflineWorkspaceSections?):
        Map<String, Any> {
        if (workspace == null) return emptyMap()
        val items: List<Pair<String, Any>> =
            workspace.heardPaths.map { it.metadata.evidenceId to it } +
            workspace.ionosphericProducts.map { it.metadata.evidenceId to it } +
            workspace.solarGeomagnetic.map { it.metadata.evidenceId to it } +
            workspace.modeledPaths.map { it.metadata.evidenceId to it }
        val byId = items.toMap()
        require(byId.size == items.size) { "Duplicate projection evidence ID" }
        return byId
    }
}
