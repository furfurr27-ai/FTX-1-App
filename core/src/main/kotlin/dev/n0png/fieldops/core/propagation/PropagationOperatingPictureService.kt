package dev.n0png.fieldops.core.propagation

/**
 * Lossless platform-neutral read view. The workspace projection (including its
 * filter/assessment semantics) and source-status model remain independent.
 *
 * One explicit UTC and one captured snapshot feed both projections. This does
 * NOT provide atomicity across independent stores or guarantee live health.
 */
data class PropagationOperatingPicture(
    val queriedAtUtcMillis: Long,
    val workspace: PropagationWorkspaceProjection?,
    val sourceStatus: PropagationSourcesStatusProjection,
) {
    init {
        require(queriedAtUtcMillis >= 0) {
            "Operating-picture UTC must be non-negative"
        }
        require(sourceStatus.queriedAtUtcMillis == queriedAtUtcMillis) {
            "Operating-picture status UTC must match query"
        }
        require(workspace?.status?.snapshotId == sourceStatus.snapshotId) {
            "Operating-picture workspace and source-status snapshot ids must match"
        }
        require(workspace?.status?.capturedAtUtcMillis ==
            sourceStatus.snapshotCapturedAtUtcMillis) {
            "Operating-picture snapshot capture timestamps must match"
        }
        require(workspace?.status?.snapshotIsFutureDated ==
            sourceStatus.snapshotIsFutureDated || workspace == null) {
            "Operating-picture snapshot future-date markers must match"
        }
        require(workspace != null || sourceStatus.snapshotId == null) {
            "A cached snapshot must have a workspace projection"
        }
    }

    val hasSnapshot: Boolean get() = workspace != null
}

/**
 * Read-only orchestration. Snapshot selection happens ONCE, followed by a
 * single read of refresh source states. Both projections see that same
 * snapshot instance and explicit UTC, rather than racing separate latest()
 * calls. No refresh, HTTP, scheduler, persisted writes or internal clock.
 */
class PropagationOperatingPictureService(
    private val snapshotStore: PropagationSnapshotStore,
    private val refreshStateStore: PropagationRefreshStateStore,
    assessmentEngine: PropagationAssessmentEngine = PropagationAssessmentEngine(),
) {
    private val projectionService =
        PropagationWorkspaceProjectionService(snapshotStore, assessmentEngine)
    private val sourceStatusService =
        PropagationSourceStatusService(refreshStateStore, snapshotStore)

    fun read(query: PropagationProjectionQuery): PropagationOperatingPicture {
        val snapshot = snapshotStore.latest()
        val states = refreshStateStore.all()
        val workspace = snapshot?.let { projectionService.project(it, query) }
        val status = sourceStatusService.projectFrom(
            nowUtcMillis = query.nowUtcMillis,
            states = states,
            snapshot = snapshot,
        )
        return PropagationOperatingPicture(
            queriedAtUtcMillis = query.nowUtcMillis,
            workspace = workspace,
            sourceStatus = status,
        )
    }

    fun read(
        nowUtcMillis: Long,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
        selectedPath: PropagationAssessmentQuery? = null,
    ): PropagationOperatingPicture =
        read(PropagationProjectionQuery(nowUtcMillis, filter, selectedPath))
}
