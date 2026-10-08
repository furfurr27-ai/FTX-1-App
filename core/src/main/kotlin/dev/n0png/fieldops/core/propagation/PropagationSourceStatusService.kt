package dev.n0png.fieldops.core.propagation

/**
 * Read-only status of the source scheduler, not an assessment of RF conditions
 * or proof that a successfully queried provider returned usable evidence.
 */
enum class PropagationSourceRefreshReadiness {
    READY,
    CADENCE_WAIT,
    RETRY_BACKOFF,
    FAILURE_COOLDOWN,
}

enum class PropagationSourceLastAttempt {
    NEVER_ATTEMPTED,
    SUCCEEDED,
    FAILED,
}

data class PropagationSourceStatusRow(
    val sourceKey: String,
    val role: PropagationRefreshSourceRole,
    val readiness: PropagationSourceRefreshReadiness,
    val lastAttempt: PropagationSourceLastAttempt,
    val lastAttemptUtcMillis: Long?,
    val lastSuccessUtcMillis: Long?,
    val lastSuccessAgeMillis: Long?,
    val lastSuccessIsFutureDated: Boolean,
    val consecutiveFailures: Int,
    val nextEligibleRefreshUtcMillis: Long,
    val remainingWaitMillis: Long,
    val lastFailureMessage: String?,
    val lastFailureRetryable: Boolean?,
    val evidenceCount: Int,
    val oldestEvidenceObservedUtcMillis: Long?,
    val newestEvidenceObservedUtcMillis: Long?,
    val oldestEvidenceRetrievedUtcMillis: Long?,
    val newestEvidenceRetrievedUtcMillis: Long?,
    val evidenceFreshnessCounts: Map<PropagationFreshness, Int>,
) {
    val hasCachedEvidence: Boolean get() = evidenceCount != 0
}

data class PropagationSourcesStatusProjection(
    val queriedAtUtcMillis: Long,
    val snapshotId: String?,
    val snapshotCapturedAtUtcMillis: Long?,
    val snapshotIsFutureDated: Boolean,
    val sources: List<PropagationSourceStatusRow>,
) {
    val readyCount: Int get() = sources.count {
        it.readiness == PropagationSourceRefreshReadiness.READY
    }
    val failedCount: Int get() = sources.count {
        it.lastAttempt == PropagationSourceLastAttempt.FAILED
    }
    val sourcesWithEvidenceCount: Int get() = sources.count { it.hasCachedEvidence }
}

/**
 * A pure projection of stores. Does not fetch, refresh, write, advance
 * scheduling, guess station geography or filter the source snapshot.
 *
 * Evidence "freshness" reuses the established observed-UTC classification.
 * Retrieval UTC and successful refresh UTC are retained independently.
 * An empty successful provider response never creates invented evidence.
 */
class PropagationSourceStatusService(
    private val stateStore: PropagationRefreshStateStore,
    private val snapshotStore: PropagationSnapshotStore,
) {
    fun project(nowUtcMillis: Long): PropagationSourcesStatusProjection {
        require(nowUtcMillis >= 0) { "Propagation source-status UTC must be non-negative" }
        val states = stateStore.all()
        require(states.map { it.sourceKey }.distinct().size == states.size) {
            "Propagation source-status records must have unique keys"
        }
        val snapshot = snapshotStore.latest()
        val evidence = snapshot?.allEvidence().orEmpty()

        val rows = states.sortedBy { it.sourceKey }.map { state ->
            val ownEvidence = evidence.filter {
                state.role.managesSourceId(it.source.sourceId, state.sourceKey)
            }
            val remaining = if (state.nextEligibleRefreshUtcMillis > nowUtcMillis) {
                state.nextEligibleRefreshUtcMillis - nowUtcMillis
            } else 0L
            val readiness = when {
                remaining == 0L -> PropagationSourceRefreshReadiness.READY
                state.consecutiveFailures > 0 && state.lastFailureRetryable == true ->
                    PropagationSourceRefreshReadiness.RETRY_BACKOFF
                state.consecutiveFailures > 0 ->
                    PropagationSourceRefreshReadiness.FAILURE_COOLDOWN
                else -> PropagationSourceRefreshReadiness.CADENCE_WAIT
            }
            val attempt = when {
                state.consecutiveFailures > 0 -> PropagationSourceLastAttempt.FAILED
                state.lastAttemptUtcMillis != null -> PropagationSourceLastAttempt.SUCCEEDED
                else -> PropagationSourceLastAttempt.NEVER_ATTEMPTED
            }
            val freshnessCounts = PropagationFreshness.entries.associateWith { freshness ->
                ownEvidence.count {
                    PropagationSourceFreshnessDefaults.classify(it, nowUtcMillis) == freshness
                }
            }
            val success = state.lastSuccessUtcMillis
            PropagationSourceStatusRow(
                sourceKey = state.sourceKey,
                role = state.role,
                readiness = readiness,
                lastAttempt = attempt,
                lastAttemptUtcMillis = state.lastAttemptUtcMillis,
                lastSuccessUtcMillis = success,
                lastSuccessAgeMillis = success?.takeIf { it <= nowUtcMillis }
                    ?.let { nowUtcMillis - it },
                lastSuccessIsFutureDated = success != null && success > nowUtcMillis,
                consecutiveFailures = state.consecutiveFailures,
                nextEligibleRefreshUtcMillis = state.nextEligibleRefreshUtcMillis,
                remainingWaitMillis = remaining,
                lastFailureMessage = state.lastFailureMessage,
                lastFailureRetryable = state.lastFailureRetryable,
                evidenceCount = ownEvidence.size,
                oldestEvidenceObservedUtcMillis =
                    ownEvidence.minOfOrNull { it.observedAtUtcMillis },
                newestEvidenceObservedUtcMillis =
                    ownEvidence.maxOfOrNull { it.observedAtUtcMillis },
                oldestEvidenceRetrievedUtcMillis =
                    ownEvidence.minOfOrNull { it.source.retrievedAtUtcMillis },
                newestEvidenceRetrievedUtcMillis =
                    ownEvidence.maxOfOrNull { it.source.retrievedAtUtcMillis },
                evidenceFreshnessCounts = freshnessCounts,
            )
        }
        return PropagationSourcesStatusProjection(
            queriedAtUtcMillis = nowUtcMillis,
            snapshotId = snapshot?.snapshotId,
            snapshotCapturedAtUtcMillis = snapshot?.capturedAtUtcMillis,
            snapshotIsFutureDated = snapshot != null &&
                snapshot.capturedAtUtcMillis > nowUtcMillis,
            sources = rows,
        )
    }
}
