package dev.n0png.fieldops.core.propagation

/**
 * CP-0009A: detached canonical offline capture from ONE already-read operating
 * picture. This is not a live provider read, store transaction or RF sample.
 * Captures are deliberately artifact-only: no mutable workspace/store escapes.
 */
data class PropagationWorkspaceHistoryCapture(
    val artifact: PropagationOfflineSerializedReport,
    val receipt: PropagationOfflineImportReceipt,
)

/**
 * Historical pair with typed interaction view. Original canonical reports
 * accompany the derivative for full comparison reconciliation, not origin
 * authentication. This model is never the current live workspace.
 */
data class PropagationWorkspaceHistoryComparison(
    val before: PropagationWorkspaceHistoryCapture,
    val after: PropagationWorkspaceHistoryCapture,
    val artifact: PropagationOfflineSerializedComparison,
    val screen: PropagationOfflineComparisonAccessibleScreen,
)

/**
 * Application-level software-only bridge from an already captured workspace
 * picture into offline report serialization and CP-0008Z history navigation.
 *
 * No snapshotStore.latest, refresh calls, wall clock, Android or network.
 * No untrusted receipt is accepted without canonical import validation.
 */
object PropagationWorkspaceHistoryService {
    /** No new store read: only the caller's single operating picture is used. */
    fun capture(picture: PropagationOperatingPicture): PropagationWorkspaceHistoryCapture {
        val diagnostic = PropagationReadModelConsistencyService.withDiagnostics(picture)
        val report = PropagationOfflineDiagnosticReportService.build(diagnostic)
        val artifact = PropagationOfflineReportSerialization.serialize(report)
        val imported = PropagationOfflineReportImportService.importReport(artifact)
        require(imported.report == report) { "Offline capture import/report mismatch" }
        require(imported.receipt.reportQueryUtcMillis == picture.queriedAtUtcMillis) {
            "Offline capture query UTC drift"
        }
        require(imported.receipt.snapshotId == picture.sourceStatus.snapshotId) {
            "Offline capture snapshot identity drift"
        }
        return PropagationWorkspaceHistoryCapture(artifact, imported.receipt)
    }

    /**
     * Build canonical derivative and accessible historical comparison. A
     * previous report is supplied by the host, never retrieved or fabricated.
     */
    fun compare(
        before: PropagationWorkspaceHistoryCapture,
        after: PropagationWorkspaceHistoryCapture,
    ): PropagationWorkspaceHistoryComparison {
        checked(before)
        checked(after)
        val comparison = PropagationOfflineReportComparisonSerialization.serialize(
            before.artifact, after.artifact)
        val screen = PropagationOfflineReportComparisonInteractionService.open(
            comparison, before.artifact, after.artifact)
        require(screen.display.header.originalReportsReconciliation ==
            PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH) {
            "Historical comparison does not match both original reports"
        }
        require(screen.display.header.beforeReportReceipt == before.receipt &&
            screen.display.header.afterReportReceipt == after.receipt) {
            "Historical comparison receipt mismatch"
        }
        return PropagationWorkspaceHistoryComparison(before, after, comparison, screen)
    }

    /**
     * Applies CP-0008Z host-only pagination/filter/focus action; requires all
     * original artifacts and derivative to be revalidated on every transition.
     * Existing comparison and captures never mutate.
     */
    fun interact(
        historical: PropagationWorkspaceHistoryComparison,
        action: PropagationOfflineComparisonInteraction,
    ): PropagationWorkspaceHistoryComparison {
        checked(historical.before)
        checked(historical.after)
        // Caller-authored derivative cannot silently masquerade as this pair.
        val expected = PropagationOfflineReportComparisonSerialization.serialize(
            historical.before.artifact, historical.after.artifact)
        require(historical.artifact == expected) {
            "Historical derivative not bound to supplied original artifacts"
        }
        val updated = PropagationOfflineReportComparisonInteractionService.interact(
            historical.artifact, historical.screen.state, action,
            historical.before.artifact, historical.after.artifact)
        require(updated.display.header.originalReportsReconciliation ==
            PropagationOfflineComparisonReconciliation.ORIGINAL_REPORTS_MATCH) {
            "Historical comparison original report consistency lost"
        }
        return historical.copy(screen = updated)
    }

    private fun checked(capture: PropagationWorkspaceHistoryCapture) {
        val imported = PropagationOfflineReportImportService.importReport(capture.artifact)
        require(imported.receipt == capture.receipt) {
            "Historical capture receipt is not the canonical artifact receipt"
        }
        require(!imported.receipt.originAuthenticated &&
            !imported.receipt.crossStoreAtomicityVerified) {
            "A historical capture cannot claim authentication or atomicity"
        }
    }
}
