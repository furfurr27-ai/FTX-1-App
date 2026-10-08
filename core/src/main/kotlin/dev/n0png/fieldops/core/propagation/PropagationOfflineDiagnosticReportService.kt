package dev.n0png.fieldops.core.propagation

/**
 * Platform-neutral offline diagnostic report DTO. A null field means unknown
 * or unavailable, never a fabricated provider observation.
 *
 * The complete CP-0008M workspace sections and CP-0008N source status/timing
 * are retained, rather than being reduced to a speculative propagation score.
 */
data class PropagationOfflineSnapshotHeader(
    val snapshotId: String?,
    val capturedAtUtcMillis: Long?,
    val snapshotAgeMillis: Long?,
    val isFutureDated: Boolean,
    val offlineCacheAvailable: Boolean?,
    val sourceCount: Int?,
    val oldestRetrievalUtcMillis: Long?,
    val newestRetrievalUtcMillis: Long?,
) {
    init {
        require((snapshotId == null) == (capturedAtUtcMillis == null)) {
            "Snapshot ID and capture UTC must both be present or absent"
        }
        require(snapshotAgeMillis == null || snapshotAgeMillis >= 0L) {
            "Snapshot age cannot be negative"
        }
        require(sourceCount == null || sourceCount >= 0) {
            "Snapshot source count cannot be negative"
        }
    }
}

data class PropagationOfflineSourceReport(
    val sourceKey: String,
    val status: PropagationSourceStatusRow,
    val timing: PropagationSourceConsistencyDiagnostic,
) {
    init {
        require(sourceKey.isNotBlank() && sourceKey == status.sourceKey &&
            sourceKey == timing.sourceKey) {
            "Report status and diagnostic source identity must match"
        }
        require(status.role == timing.sourceRole) {
            "Report source role must match diagnostic role"
        }
        require(status.evidenceCount == timing.evidenceCount) {
            "Report evidence totals must match"
        }
    }
}

enum class PropagationOfflineEvidenceKind {
    HEARD_PATH,
    IONOSPHERIC_MAP,
    SOLAR_GEOMAGNETIC,
    MODELED_PATH,
}

/** Refers only to evidence actually selected by workspace filters. */
data class PropagationOfflineEvidenceIndexItem(
    val kind: PropagationOfflineEvidenceKind,
    val evidenceId: String,
    val sourceId: String,
    val observedAtUtcMillis: Long,
    val retrievedAtUtcMillis: Long,
    val freshness: PropagationFreshness,
    val retrievalIsFutureDated: Boolean,
)

data class PropagationOfflineWorkspaceSections(
    val heardPaths: List<HeardPathProjection>,
    val ionosphericProducts: List<IonosphericProjection>,
    val solarGeomagnetic: List<SolarGeomagneticProjection>,
    val modeledPaths: List<ModeledPathProjection>,
    val selectedPathAssessment: PropagationPathAssessment?,
) {
    val visibleEvidenceCount: Int
        get() = heardPaths.size + ionosphericProducts.size +
            solarGeomagnetic.size + modeledPaths.size
}

data class PropagationOfflineReportSummary(
    val sourceCount: Int,
    val readySourceCount: Int,
    val failedSourceCount: Int,
    val sourcesWithCachedEvidenceCount: Int,
    /** Sum of source-attributed cached evidence; not workspace-filtered. */
    val attributedCachedEvidenceCount: Int,
    /** Evidence actually visible in the filtered workspace projection. */
    val visibleEvidenceCount: Int,
    val successesNewerThanSnapshotCount: Int,
    val retrievalsNewerThanSuccessCount: Int,
    val futureAttemptSourceCount: Int,
    val futureRetrievalSourceCount: Int,
    /** Does not imply provider health, a forecast, or transactional consistency. */
    val crossStoreAtomicityVerified: Boolean = false,
)

data class PropagationOfflineDiagnosticReport(
    val schemaVersion: Int,
    val queriedAtUtcMillis: Long,
    val snapshot: PropagationOfflineSnapshotHeader,
    val summary: PropagationOfflineReportSummary,
    val sources: List<PropagationOfflineSourceReport>,
    val workspace: PropagationOfflineWorkspaceSections?,
    val visibleEvidenceIndex: List<PropagationOfflineEvidenceIndexItem>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported offline report schema version" }
        require(queriedAtUtcMillis >= 0L) { "Report UTC must be non-negative" }
        require((workspace == null) == (snapshot.snapshotId == null)) {
            "Report workspace and snapshot presence must agree"
        }
        require(summary.sourceCount == sources.size) {
            "Report summary must include every source"
        }
        require(sources.map { it.sourceKey }.distinct().size == sources.size) {
            "Duplicate report source key"
        }
        require(summary.visibleEvidenceCount == visibleEvidenceIndex.size &&
            summary.visibleEvidenceCount == (workspace?.visibleEvidenceCount ?: 0)) {
            "Report evidence index must match selected workspace evidence"
        }
        require(!summary.crossStoreAtomicityVerified) {
            "A report cannot claim cross-store atomic consistency"
        }
    }
}

/**
 * Pure data transformation from the already captured operating picture and
 * its CP-0008N diagnostics. No extra state/snapshot reads, internal clock,
 * provider fetch, scheduler or mutation.
 */
object PropagationOfflineDiagnosticReportService {
    const val SCHEMA_VERSION = 1

    fun build(composed: PropagationOperatingPictureWithDiagnostics):
        PropagationOfflineDiagnosticReport {
        val picture = composed.operatingPicture
        val d = composed.diagnostics
        val status = picture.sourceStatus
        val workspace = picture.workspace
        val sourceByKey = status.sources.associateBy { it.sourceKey }
        require(sourceByKey.size == status.sources.size) {
            "Source status must contain unique source keys"
        }
        val diagnosticByKey = d.sourceDiagnostics.associateBy { it.sourceKey }
        require(diagnosticByKey.size == d.sourceDiagnostics.size &&
            diagnosticByKey.keys == sourceByKey.keys) {
            "Every cached source status needs exactly one diagnostic row"
        }
        require(d.crossStoreAtomicityVerified.not()) {
            "Offline report cannot assert cross-store atomicity"
        }

        val sections = workspace?.let {
            PropagationOfflineWorkspaceSections(
                heardPaths = it.heardPaths.toList(),
                ionosphericProducts = it.ionosphericProducts.toList(),
                solarGeomagnetic = it.solarGeomagnetic.toList(),
                modeledPaths = it.modeledPaths.toList(),
                selectedPathAssessment = it.selectedPathAssessment,
            )
        }

        fun index(kind: PropagationOfflineEvidenceKind, meta: PropagationProjectionMetadata) =
            PropagationOfflineEvidenceIndexItem(
                kind = kind,
                evidenceId = meta.evidenceId,
                sourceId = meta.source.sourceId,
                observedAtUtcMillis = meta.observedAtUtcMillis,
                retrievedAtUtcMillis = meta.source.retrievedAtUtcMillis,
                freshness = meta.freshness,
                retrievalIsFutureDated = meta.retrievalIsFutureDated,
            )

        val visibleIndex = buildList {
            sections?.heardPaths?.forEach {
                add(index(PropagationOfflineEvidenceKind.HEARD_PATH, it.metadata))
            }
            sections?.ionosphericProducts?.forEach {
                add(index(PropagationOfflineEvidenceKind.IONOSPHERIC_MAP, it.metadata))
            }
            sections?.solarGeomagnetic?.forEach {
                add(index(PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC, it.metadata))
            }
            sections?.modeledPaths?.forEach {
                add(index(PropagationOfflineEvidenceKind.MODELED_PATH, it.metadata))
            }
        }

        val sourceRows = status.sources.sortedBy { it.sourceKey }.map { row ->
            PropagationOfflineSourceReport(
                sourceKey = row.sourceKey,
                status = row,
                timing = diagnosticByKey.getValue(row.sourceKey),
            )
        }
        val header = PropagationOfflineSnapshotHeader(
            snapshotId = status.snapshotId,
            capturedAtUtcMillis = status.snapshotCapturedAtUtcMillis,
            snapshotAgeMillis = d.snapshotAgeMillis,
            isFutureDated = status.snapshotIsFutureDated,
            offlineCacheAvailable = workspace?.status?.offlineCacheAvailable,
            sourceCount = workspace?.status?.sourceCount,
            oldestRetrievalUtcMillis = workspace?.status?.oldestSourceRetrievalAtUtcMillis,
            newestRetrievalUtcMillis = workspace?.status?.newestSourceRetrievalAtUtcMillis,
        )
        val summary = PropagationOfflineReportSummary(
            sourceCount = sourceRows.size,
            readySourceCount = status.readyCount,
            failedSourceCount = status.failedCount,
            sourcesWithCachedEvidenceCount = status.sourcesWithEvidenceCount,
            attributedCachedEvidenceCount = sourceRows.sumOf { it.status.evidenceCount },
            visibleEvidenceCount = visibleIndex.size,
            successesNewerThanSnapshotCount = d.sourceSuccessAfterSnapshotCount,
            retrievalsNewerThanSuccessCount = d.evidenceRetrievedAfterLastSuccessCount,
            futureAttemptSourceCount = d.futureDatedSourceAttemptCount,
            futureRetrievalSourceCount = d.futureDatedRetrievalSourceCount,
        )
        return PropagationOfflineDiagnosticReport(
            schemaVersion = SCHEMA_VERSION,
            queriedAtUtcMillis = picture.queriedAtUtcMillis,
            snapshot = header,
            summary = summary,
            sources = sourceRows,
            workspace = sections,
            visibleEvidenceIndex = visibleIndex,
        )
    }
}
