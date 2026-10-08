package dev.n0png.fieldops.core.propagation

/**
 * Read-only provenance comparison. These labels describe differences between
 * independently persisted timestamps, not provider faults or cache corruption.
 */
enum class PropagationReadTimestampRelation {
    UNKNOWN,
    BEFORE,
    EQUAL,
    AFTER,
}

data class PropagationSourceConsistencyDiagnostic(
    val sourceKey: String,
    val sourceRole: PropagationRefreshSourceRole,
    val lastSuccessToSnapshotRelation: PropagationReadTimestampRelation,
    val newestRetrievalToLastSuccessRelation: PropagationReadTimestampRelation,
    val lastSuccessMinusSnapshotCaptureMillis: Long?,
    val newestRetrievalMinusLastSuccessMillis: Long?,
    val lastAttemptIsFutureDated: Boolean,
    val newestRetrievalIsFutureDated: Boolean,
    val evidenceCount: Int,
    val freshEvidenceCount: Int,
    val agingEvidenceCount: Int,
    val staleEvidenceCount: Int,
    val futureDatedEvidenceCount: Int,
) {
    init {
        require(sourceKey.isNotBlank()) { "Diagnostic source key must not be blank" }
        require(evidenceCount >= 0) { "Diagnostic evidence count cannot be negative" }
        require(listOf(freshEvidenceCount, agingEvidenceCount,
            staleEvidenceCount, futureDatedEvidenceCount).all { it >= 0 }) {
            "Diagnostic freshness counts cannot be negative"
        }
        require(freshEvidenceCount + agingEvidenceCount +
            staleEvidenceCount + futureDatedEvidenceCount == evidenceCount) {
            "Diagnostic counts must represent all evidence"
        }
    }
}

/** Per-view timestamps are trustworthy as *reported provenance* only. */
data class PropagationReadModelConsistencyDiagnostics(
    val queriedAtUtcMillis: Long,
    val snapshotId: String?,
    val snapshotCapturedAtUtcMillis: Long?,
    val snapshotAgeMillis: Long?,
    val snapshotCaptureIsFutureDated: Boolean,
    val newestSourceSuccessUtcMillis: Long?,
    val sourceSuccessAfterSnapshotCount: Int,
    val evidenceRetrievedAfterLastSuccessCount: Int,
    val futureDatedSourceAttemptCount: Int,
    val futureDatedRetrievalSourceCount: Int,
    val sourceDiagnostics: List<PropagationSourceConsistencyDiagnostic>,
) {
    /** Always false: two different stores cannot promise a transactional read. */
    val crossStoreAtomicityVerified: Boolean get() = false

    init {
        require(queriedAtUtcMillis >= 0) { "Diagnostic UTC cannot be negative" }
        require((snapshotId == null) == (snapshotCapturedAtUtcMillis == null)) {
            "Snapshot identity and timestamp must be supplied together"
        }
        require(sourceDiagnostics.map { it.sourceKey }.distinct().size ==
            sourceDiagnostics.size) { "Duplicate diagnostic source" }
        require(sourceSuccessAfterSnapshotCount ==
            sourceDiagnostics.count {
                it.lastSuccessToSnapshotRelation == PropagationReadTimestampRelation.AFTER
            }) { "Snapshot lag count mismatch" }
        require(evidenceRetrievedAfterLastSuccessCount ==
            sourceDiagnostics.count {
                it.newestRetrievalToLastSuccessRelation ==
                    PropagationReadTimestampRelation.AFTER
            }) { "Retrieval lag count mismatch" }
    }
}

data class PropagationOperatingPictureWithDiagnostics(
    val operatingPicture: PropagationOperatingPicture,
    val diagnostics: PropagationReadModelConsistencyDiagnostics,
) {
    init {
        require(operatingPicture.queriedAtUtcMillis == diagnostics.queriedAtUtcMillis) {
            "Operating picture and diagnostics must share UTC"
        }
        require(operatingPicture.sourceStatus.snapshotId == diagnostics.snapshotId) {
            "Operating picture and diagnostics must share snapshot identity"
        }
        require(operatingPicture.sourceStatus.snapshotCapturedAtUtcMillis ==
            diagnostics.snapshotCapturedAtUtcMillis) {
            "Operating picture and diagnostics must share snapshot capture UTC"
        }
    }
}

/**
 * Pure diagnostics over the existing CP-0008M read model. No independent
 * access to refresh state or snapshots, no wall clock, no fetch and no writes.
 *
 * Comparisons are observational timing hints. In particular, a newer source
 * success than cached snapshot does not prove an error: a successful empty
 * source fetch need not replace existing cached evidence.
 */
object PropagationReadModelConsistencyService {
    fun diagnose(picture: PropagationOperatingPicture): PropagationReadModelConsistencyDiagnostics {
        val queried = picture.queriedAtUtcMillis
        val sourceView = picture.sourceStatus
        val captured = sourceView.snapshotCapturedAtUtcMillis
        val rows = sourceView.sources.sortedBy { it.sourceKey }
        val diagnostics = rows.map { row ->
            val success = row.lastSuccessUtcMillis
            val retrieval = row.newestEvidenceRetrievedUtcMillis
            val counts = row.evidenceFreshnessCounts
            PropagationSourceConsistencyDiagnostic(
                sourceKey = row.sourceKey,
                sourceRole = row.role,
                lastSuccessToSnapshotRelation = compare(success, captured),
                newestRetrievalToLastSuccessRelation = compare(retrieval, success),
                lastSuccessMinusSnapshotCaptureMillis =
                    subtractIfPresent(success, captured),
                newestRetrievalMinusLastSuccessMillis =
                    subtractIfPresent(retrieval, success),
                lastAttemptIsFutureDated =
                    row.lastAttemptUtcMillis?.let { it > queried } ?: false,
                newestRetrievalIsFutureDated =
                    retrieval?.let { it > queried } ?: false,
                evidenceCount = row.evidenceCount,
                freshEvidenceCount = counts[PropagationFreshness.FRESH] ?: 0,
                agingEvidenceCount = counts[PropagationFreshness.AGING] ?: 0,
                staleEvidenceCount = counts[PropagationFreshness.STALE] ?: 0,
                futureDatedEvidenceCount = counts[PropagationFreshness.FUTURE_DATED] ?: 0,
            )
        }
        return PropagationReadModelConsistencyDiagnostics(
            queriedAtUtcMillis = queried,
            snapshotId = sourceView.snapshotId,
            snapshotCapturedAtUtcMillis = captured,
            snapshotAgeMillis = captured?.takeIf { it <= queried }?.let { queried - it },
            snapshotCaptureIsFutureDated = sourceView.snapshotIsFutureDated,
            newestSourceSuccessUtcMillis = rows.mapNotNull { it.lastSuccessUtcMillis }.maxOrNull(),
            sourceSuccessAfterSnapshotCount = diagnostics.count {
                it.lastSuccessToSnapshotRelation == PropagationReadTimestampRelation.AFTER
            },
            evidenceRetrievedAfterLastSuccessCount = diagnostics.count {
                it.newestRetrievalToLastSuccessRelation == PropagationReadTimestampRelation.AFTER
            },
            futureDatedSourceAttemptCount = diagnostics.count { it.lastAttemptIsFutureDated },
            futureDatedRetrievalSourceCount = diagnostics.count {
                it.newestRetrievalIsFutureDated
            },
            sourceDiagnostics = diagnostics,
        )
    }

    fun withDiagnostics(picture: PropagationOperatingPicture) =
        PropagationOperatingPictureWithDiagnostics(picture, diagnose(picture))

    private fun compare(a: Long?, b: Long?): PropagationReadTimestampRelation =
        when {
            a == null || b == null -> PropagationReadTimestampRelation.UNKNOWN
            a < b -> PropagationReadTimestampRelation.BEFORE
            a > b -> PropagationReadTimestampRelation.AFTER
            else -> PropagationReadTimestampRelation.EQUAL
        }

    private fun subtractIfPresent(a: Long?, b: Long?): Long? =
        if (a == null || b == null) null else a - b
}
