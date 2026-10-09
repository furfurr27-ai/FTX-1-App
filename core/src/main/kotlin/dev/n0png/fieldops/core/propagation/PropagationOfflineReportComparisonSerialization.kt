package dev.n0png.fieldops.core.propagation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Separate immutable export record; never reuse the CP-0008P report media type. */
data class PropagationOfflineSerializedComparison(
    val contentType: String,
    val wireVersion: Int,
    val json: String,
    val utf8ByteCount: Int,
    val sha256Hex: String,
)

/**
 * Canonical export V1 of a CP-0008S offline comparison, not a decoder or a
 * digital signature. This does not modify CP-0008P's golden report wire format.
 *
 * The artifact contains source-row diffs, evidence-index diffs, original
 * before/after receipts and change flags. It does NOT include full source
 * report JSON artifacts or every original projection payload: use both
 * original report artifacts to independently verify/recompute the comparison.
 * Projection-content changes are explicitly flagged but not independently
 * reproducible from this stand-alone derivative alone.
 */
object PropagationOfflineReportComparisonSerialization {
    const val WIRE_VERSION = 1
    const val FORMAT = "fieldops.propagation.offline-comparison"
    const val CONTENT_TYPE =
        "application/vnd.fieldops.propagation-comparison+json;version=1"
    const val MAX_UTF8_BYTES = 16 * 1024 * 1024

    /** Validation-first export from both original canonical V1 report inputs. */
    fun serialize(
        beforeArtifact: PropagationOfflineSerializedReport,
        afterArtifact: PropagationOfflineSerializedReport,
    ): PropagationOfflineSerializedComparison =
        serialize(PropagationOfflineReportComparisonService.compare(
            beforeArtifact, afterArtifact
        ))

    /**
     * Deterministic export of an already-computed comparison. The explicit
     * invariants catch accidental DTO drift, but cannot authenticate arbitrary
     * caller-constructed DTO values. For provenance-sensitive use, prefer
     * serialize(beforeArtifact, afterArtifact).
     */
    fun serialize(comparison: PropagationOfflineReportComparison):
        PropagationOfflineSerializedComparison {
        validate(comparison)
        val payload = PropagationOfflineReportSerialization.canonicalValue(comparison)
        val json = "{\"format\":\"$FORMAT\",\"payload\":$payload,\"wireVersion\":$WIRE_VERSION}"
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_UTF8_BYTES) {
            "Offline comparison exceeds maximum UTF-8 byte count"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return PropagationOfflineSerializedComparison(
            contentType = CONTENT_TYPE,
            wireVersion = WIRE_VERSION,
            json = json,
            utf8ByteCount = bytes.size,
            sha256Hex = digest,
        )
    }

    /** Exact DTO-to-artifact equality; a matching SHA-256 is not a signature. */
    fun verify(
        comparison: PropagationOfflineReportComparison,
        encoded: PropagationOfflineSerializedComparison,
    ): Boolean = serialize(comparison) == encoded

    private fun validate(c: PropagationOfflineReportComparison) {
        require(!c.originAuthenticated && !c.crossStoreAtomicityVerified) {
            "Offline comparison cannot claim authentication or cross-store atomicity"
        }
        require(c.beforeReceipt.trust ==
            PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT &&
            c.afterReceipt.trust ==
                PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT &&
            !c.beforeReceipt.originAuthenticated &&
            !c.afterReceipt.originAuthenticated &&
            !c.beforeReceipt.crossStoreAtomicityVerified &&
            !c.afterReceipt.crossStoreAtomicityVerified) {
            "Original import receipts cannot claim authenticated provenance"
        }
        for (receipt in listOf(c.beforeReceipt, c.afterReceipt)) {
            require(receipt.contentType == PropagationOfflineReportSerialization.CONTENT_TYPE &&
                receipt.wireVersion == PropagationOfflineReportSerialization.WIRE_VERSION &&
                receipt.utf8ByteCount in 1..PropagationOfflineReportSerialization.MAX_UTF8_BYTES &&
                receipt.sha256Hex.matches(Regex("[a-f0-9]{64}")) &&
                receipt.reportQueryUtcMillis >= 0L &&
                receipt.sourceCount >= 0 && receipt.visibleEvidenceCount >= 0 &&
                ((receipt.snapshotId == null) == (receipt.snapshotCapturedAtUtcMillis == null))) {
                "Invalid original offline report receipt"
            }
        }
        val expectedTimeOrder = when {
            c.afterReceipt.reportQueryUtcMillis < c.beforeReceipt.reportQueryUtcMillis ->
                PropagationOfflineComparisonTimeOrder.EARLIER
            c.afterReceipt.reportQueryUtcMillis > c.beforeReceipt.reportQueryUtcMillis ->
                PropagationOfflineComparisonTimeOrder.LATER
            else -> PropagationOfflineComparisonTimeOrder.SAME
        }
        require(c.afterQueryTimeRelativeToBefore == expectedTimeOrder) {
            "Offline report query UTC ordering drift"
        }
        require(c.canonicalArtifactChanged ==
            (c.beforeReceipt.sha256Hex != c.afterReceipt.sha256Hex)) {
            "Canonical comparison report-identity marker drift"
        }

        require(c.sourceChanges.map { it.sourceKey } ==
            c.sourceChanges.map { it.sourceKey }.distinct().sorted()) {
            "Source comparison keys must be unique and sorted"
        }
        require(c.evidenceChanges.map { it.evidenceId } ==
            c.evidenceChanges.map { it.evidenceId }.distinct().sorted()) {
            "Evidence comparison IDs must be unique and sorted"
        }
        var beforeSources = 0
        var afterSources = 0
        for (row in c.sourceChanges) {
            require(row.sourceKey.isNotBlank()) { "Empty comparison source key" }
            val before = row.before
            val after = row.after
            require(before != null || after != null) { "Empty source comparison row" }
            require((before == null || before.sourceKey == row.sourceKey) &&
                (after == null || after.sourceKey == row.sourceKey)) {
                "Source change key drift"
            }
            if (before != null) beforeSources++
            if (after != null) afterSources++
            val expected = when {
                before == null -> PropagationOfflineComparisonChange.ADDED_TO_VIEW
                after == null -> PropagationOfflineComparisonChange.REMOVED_FROM_VIEW
                before == after -> PropagationOfflineComparisonChange.UNCHANGED
                else -> PropagationOfflineComparisonChange.CHANGED_IN_VIEW
            }
            require(row.change == expected) { "Source change classification drift" }
        }
        require(beforeSources == c.beforeReceipt.sourceCount &&
            afterSources == c.afterReceipt.sourceCount) {
            "Source comparison counts differ from import receipts"
        }

        var beforeEvidence = 0
        var afterEvidence = 0
        for (row in c.evidenceChanges) {
            require(row.evidenceId.isNotBlank()) { "Empty selected evidence ID" }
            val before = row.before
            val after = row.after
            require(before != null || after != null) { "Empty evidence comparison row" }
            require((before == null || before.evidenceId == row.evidenceId) &&
                (after == null || after.evidenceId == row.evidenceId)) {
                "Evidence comparison identity drift"
            }
            if (before != null) beforeEvidence++
            if (after != null) afterEvidence++
            val both = before != null && after != null
            require(row.selectedIndexChanged == (both && before != after)) {
                "Selected evidence metadata-change flag drift"
            }
            require(!row.projectionContentChanged || both) {
                "Projection change cannot be claimed for absent selected evidence"
            }
            val expected = when {
                before == null -> PropagationOfflineComparisonChange.ADDED_TO_VIEW
                after == null -> PropagationOfflineComparisonChange.REMOVED_FROM_VIEW
                row.selectedIndexChanged || row.projectionContentChanged ->
                    PropagationOfflineComparisonChange.CHANGED_IN_VIEW
                else -> PropagationOfflineComparisonChange.UNCHANGED
            }
            require(row.change == expected) { "Evidence change classification drift" }
        }
        require(beforeEvidence == c.beforeReceipt.visibleEvidenceCount &&
            afterEvidence == c.afterReceipt.visibleEvidenceCount) {
            "Selected evidence counts differ from import receipts"
        }
    }
}
