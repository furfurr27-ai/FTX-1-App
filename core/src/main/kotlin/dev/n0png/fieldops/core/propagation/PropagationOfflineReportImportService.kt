package dev.n0png.fieldops.core.propagation

/**
 * Source provenance is *reported*, not authenticated. Successful canonical
 * import establishes only V1 structural and checksum consistency.
 */
enum class PropagationOfflineImportTrust {
    UNAUTHENTICATED_SELF_CONSISTENT
}

/**
 * Explicit receipt for a single, detached offline artifact.
 * The query/capture times are preserved from the report; there is deliberately
 * no import clock and no implicit conversion to live provider or RF state.
 */
data class PropagationOfflineImportReceipt(
    val contentType: String,
    val wireVersion: Int,
    val utf8ByteCount: Int,
    val sha256Hex: String,
    val trust: PropagationOfflineImportTrust,
    val reportQueryUtcMillis: Long,
    val snapshotId: String?,
    val snapshotCapturedAtUtcMillis: Long?,
    val sourceCount: Int,
    val visibleEvidenceCount: Int,
    val originAuthenticated: Boolean,
    val crossStoreAtomicityVerified: Boolean,
)

/**
 * Offline-only read view. Consumers must not treat the included original
 * source statuses/freshness labels as a live provider assessment.
 *
 * Lists/DTOs are detached by CP-0008Q's JSON decoder from any original
 * in-memory source, but remain ordinary Kotlin read-only interfaces, not
 * a guarantee against a caller deliberately casting and mutating them.
 */
class PropagationOfflineImportedReport internal constructor(
    val report: PropagationOfflineDiagnosticReport,
    val receipt: PropagationOfflineImportReceipt,
) {
    val sources: List<PropagationOfflineSourceReport>
        get() = report.sources

    val snapshot: PropagationOfflineSnapshotHeader
        get() = report.snapshot

    val workspace: PropagationOfflineWorkspaceSections?
        get() = report.workspace

    val visibleEvidenceIndex: List<PropagationOfflineEvidenceIndexItem>
        get() = report.visibleEvidenceIndex

    fun sourceByKey(key: String): PropagationOfflineSourceReport? =
        sources.firstOrNull { it.sourceKey == key }

    /** ID lookup uses the already-validated selected workspace index only. */
    fun selectedEvidenceById(id: String): PropagationOfflineEvidenceIndexItem? =
        visibleEvidenceIndex.firstOrNull { it.evidenceId == id }

    fun selectedEvidenceByKind(kind: PropagationOfflineEvidenceKind):
        List<PropagationOfflineEvidenceIndexItem> =
        visibleEvidenceIndex.filter { it.kind == kind }
}

/**
 * A deliberately narrow import boundary: no external file paths, mutable
 * snapshot stores, refresh coordinators, Android lifecycle, clock, network,
 * credentials, signatures, or RF. Callers supply the complete artifact record.
 *
 * A bare JSON string is not accepted as a report import, because its expected
 * checksum and content type would be missing. CP-0008Q still exposes explicit
 * decodeCanonical(...) for callers that intentionally need that lower-level API.
 */
object PropagationOfflineReportImportService {
    fun importReport(encoded: PropagationOfflineSerializedReport):
        PropagationOfflineImportedReport {
        // Decoder enforces all V1 format, bound, timestamp, source and index
        // contracts before returning a fully constructed typed report.
        val decoded = PropagationOfflineReportDecoder.decode(encoded)
        require(PropagationOfflineReportSerialization.serialize(decoded) == encoded) {
            "Imported report differs from its canonical artifact"
        }
        return PropagationOfflineImportedReport(
            report = decoded,
            receipt = PropagationOfflineImportReceipt(
                contentType = encoded.contentType,
                wireVersion = encoded.wireVersion,
                utf8ByteCount = encoded.utf8ByteCount,
                sha256Hex = encoded.sha256Hex,
                trust = PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
                reportQueryUtcMillis = decoded.queriedAtUtcMillis,
                snapshotId = decoded.snapshot.snapshotId,
                snapshotCapturedAtUtcMillis = decoded.snapshot.capturedAtUtcMillis,
                sourceCount = decoded.summary.sourceCount,
                visibleEvidenceCount = decoded.summary.visibleEvidenceCount,
                originAuthenticated = false,
                crossStoreAtomicityVerified = false,
            ),
        )
    }
}
