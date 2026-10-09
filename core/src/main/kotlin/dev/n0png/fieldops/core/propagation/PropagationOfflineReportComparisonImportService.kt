package dev.n0png.fieldops.core.propagation

import java.util.Collections

/**
 * Receipt for a detached comparison artifact. SHA-256 is unkeyed integrity,
 * not authenticated attribution or a signature.
 */
data class PropagationOfflineComparisonImportReceipt(
    val contentType: String,
    val wireVersion: Int,
    val utf8ByteCount: Int,
    val sha256Hex: String,
    val trust: PropagationOfflineImportTrust,
    val beforeReportReceipt: PropagationOfflineImportReceipt,
    val afterReportReceipt: PropagationOfflineImportReceipt,
    val originAuthenticated: Boolean = false,
    val crossStoreAtomicityVerified: Boolean = false,
)

/**
 * Read-only historical selection view built from canonical decoded bytes,
 * detached from caller-owned DTO collections. The returned Kotlin DTOs are
 * read-only interfaces, not transitive immutability against unsafe casts.
 * Absence from selected view does not prove provider deletion or RF changes.
 */
class PropagationOfflineImportedComparison internal constructor(
    val comparison: PropagationOfflineReportComparison,
    val receipt: PropagationOfflineComparisonImportReceipt,
    internal val artifact: PropagationOfflineSerializedComparison,
) {
    val beforeReceipt: PropagationOfflineImportReceipt get() = receipt.beforeReportReceipt
    val afterReceipt: PropagationOfflineImportReceipt get() = receipt.afterReportReceipt
    val afterQueryTimeRelativeToBefore: PropagationOfflineComparisonTimeOrder
        get() = comparison.afterQueryTimeRelativeToBefore

    val sourceChanges: List<PropagationOfflineSourceComparison> =
        Collections.unmodifiableList(ArrayList(comparison.sourceChanges))
    val evidenceChanges: List<PropagationOfflineEvidenceComparison> =
        Collections.unmodifiableList(ArrayList(comparison.evidenceChanges))

    val sourceChangeCount: Int get() = sourceChanges.count {
        it.change != PropagationOfflineComparisonChange.UNCHANGED
    }
    val evidenceChangeCount: Int get() = evidenceChanges.count {
        it.change != PropagationOfflineComparisonChange.UNCHANGED
    }

    fun sourceByKey(key: String): PropagationOfflineSourceComparison? =
        sourceChanges.firstOrNull { it.sourceKey == key }

    fun selectedEvidenceById(id: String): PropagationOfflineEvidenceComparison? =
        evidenceChanges.firstOrNull { it.evidenceId == id }

    fun sourcesByChange(change: PropagationOfflineComparisonChange):
        List<PropagationOfflineSourceComparison> =
        Collections.unmodifiableList(sourceChanges.filter { it.change == change })

    fun selectedEvidenceByChange(change: PropagationOfflineComparisonChange):
        List<PropagationOfflineEvidenceComparison> =
        Collections.unmodifiableList(evidenceChanges.filter { it.change == change })

    /** Matches either before or after selected entry, never provider-wide presence. */
    fun selectedEvidenceByKind(kind: PropagationOfflineEvidenceKind):
        List<PropagationOfflineEvidenceComparison> =
        Collections.unmodifiableList(evidenceChanges.filter {
            it.before?.kind == kind || it.after?.kind == kind
        })

    fun changedSources(): List<PropagationOfflineSourceComparison> =
        Collections.unmodifiableList(sourceChanges.filter {
            it.change != PropagationOfflineComparisonChange.UNCHANGED
        })

    fun changedSelectedEvidence(): List<PropagationOfflineEvidenceComparison> =
        Collections.unmodifiableList(evidenceChanges.filter {
            it.change != PropagationOfflineComparisonChange.UNCHANGED
        })
}

/**
 * CP-0008V host-only import/inspection. No store, clock, Android, network,
 * credentials, signature verification, RF or hardware dependencies.
 */
object PropagationOfflineReportComparisonImportService {
    fun importComparison(encoded: PropagationOfflineSerializedComparison):
        PropagationOfflineImportedComparison {
        val value = PropagationOfflineReportComparisonDecoder.decode(encoded)
        require(PropagationOfflineReportComparisonSerialization.serialize(value) == encoded) {
            "Imported comparison differs from canonical export"
        }
        return PropagationOfflineImportedComparison(
            comparison = value,
            receipt = PropagationOfflineComparisonImportReceipt(
                contentType = encoded.contentType,
                wireVersion = encoded.wireVersion,
                utf8ByteCount = encoded.utf8ByteCount,
                sha256Hex = encoded.sha256Hex,
                trust = PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
                beforeReportReceipt = value.beforeReceipt,
                afterReportReceipt = value.afterReceipt,
            ),
            artifact = encoded,
        )
    }

    /**
     * Optional recalculation against both original CP-0008P reports. Re-decodes
     * this artifact so unsafe DTO mutation cannot replace canonical evidence.
     * This is consistency, not authenticated origin or live RF verification.
     */
    fun matchesOriginalReports(
        imported: PropagationOfflineImportedComparison,
        before: PropagationOfflineSerializedReport,
        after: PropagationOfflineSerializedReport,
    ): Boolean {
        val original = importComparison(imported.artifact)
        if (original.comparison != imported.comparison ||
            original.receipt != imported.receipt) return false
        val recalculated = PropagationOfflineReportComparisonService.compare(before, after)
        return recalculated == original.comparison &&
            original.beforeReceipt.sha256Hex == before.sha256Hex &&
            original.afterReceipt.sha256Hex == after.sha256Hex
    }
}
