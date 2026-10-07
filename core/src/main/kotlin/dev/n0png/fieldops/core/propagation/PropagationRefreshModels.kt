package dev.n0png.fieldops.core.propagation

enum class PropagationRefreshSourceRole {
    NOAA_KP_OBSERVED,
    NOAA_KP_FORECAST,
    NOAA_F107,
    NOAA_GLOTEC,
    PSK_REPORTER,
    GENERIC;

    fun managesSourceId(sourceId: String, sourceKey: String): Boolean =
        when (this) {
            NOAA_KP_OBSERVED -> sourceId == "NOAA_SWPC_PLANETARY_KP"
            NOAA_KP_FORECAST -> sourceId.startsWith("NOAA_SWPC_KP_FORECAST_")
            NOAA_F107 -> sourceId == "NOAA_SWPC_F107_SUMMARY"
            NOAA_GLOTEC -> sourceId == "NOAA_SWPC_GLOTEC_VTEC"
            PSK_REPORTER -> sourceId == "PSK_REPORTER_PUBLIC_QUERY"
            GENERIC -> sourceId == sourceKey
        }
}

data class PropagationRefreshPolicy(
    val cadenceMillis: Long,
    val initialRetryBackoffMillis: Long,
    val maximumRetryBackoffMillis: Long,
) {
    init {
        require(cadenceMillis > 0) { "Propagation refresh cadence must be positive" }
        require(initialRetryBackoffMillis > 0) {
            "Propagation refresh initial retry backoff must be positive"
        }
        require(maximumRetryBackoffMillis >= initialRetryBackoffMillis) {
            "Propagation refresh maximum retry backoff must not be below initial backoff"
        }
    }
}

sealed interface PropagationSourceFetchResult {
    data class Success(
        val input: PropagationAggregationInput,
    ) : PropagationSourceFetchResult

    data class Failure(
        val message: String,
        val retryable: Boolean = true,
    ) : PropagationSourceFetchResult {
        init {
            require(message.isNotBlank()) {
                "Propagation source failure message must not be blank"
            }
        }
    }
}

fun interface PropagationSourceFetcher {
    fun fetch(attemptedAtUtcMillis: Long): PropagationSourceFetchResult
}

data class PropagationRefreshSourceDefinition(
    val sourceKey: String,
    val role: PropagationRefreshSourceRole,
    val policy: PropagationRefreshPolicy,
    val fetcher: PropagationSourceFetcher,
) {
    init {
        require(sourceKey.isNotBlank()) {
            "Propagation refresh source key must not be blank"
        }
    }

    fun managesSourceId(sourceId: String): Boolean =
        role.managesSourceId(sourceId, sourceKey)
}

data class PropagationRefreshSourceState(
    val sourceKey: String,
    val role: PropagationRefreshSourceRole,
    val lastAttemptUtcMillis: Long? = null,
    val lastSuccessUtcMillis: Long? = null,
    val consecutiveFailures: Int = 0,
    val nextEligibleRefreshUtcMillis: Long = 0L,
    val lastFailureMessage: String? = null,
    val lastFailureRetryable: Boolean? = null,
) {
    init {
        require(sourceKey.isNotBlank()) {
            "Propagation refresh state source key must not be blank"
        }
        lastAttemptUtcMillis?.let {
            require(it >= 0) {
                "Propagation refresh last-attempt UTC must be non-negative"
            }
        }
        lastSuccessUtcMillis?.let {
            require(it >= 0) {
                "Propagation refresh last-success UTC must be non-negative"
            }
            lastAttemptUtcMillis?.let { attempt ->
                require(it <= attempt) {
                    "Propagation refresh last success cannot follow last attempt"
                }
            }
        }
        require(consecutiveFailures >= 0) {
            "Propagation refresh consecutive failures must be non-negative"
        }
        require(nextEligibleRefreshUtcMillis >= 0) {
            "Propagation refresh next-eligible UTC must be non-negative"
        }
        if (consecutiveFailures == 0) {
            require(lastFailureMessage == null && lastFailureRetryable == null) {
                "Successful refresh state must clear failure metadata"
            }
        } else {
            require(!lastFailureMessage.isNullOrBlank()) {
                "Failed refresh state requires a failure message"
            }
            require(lastFailureRetryable != null) {
                "Failed refresh state requires retryability metadata"
            }
        }
    }
}

interface PropagationRefreshStateStore {
    fun state(sourceKey: String): PropagationRefreshSourceState?
    fun save(state: PropagationRefreshSourceState)
    fun all(): List<PropagationRefreshSourceState>
}

class InMemoryPropagationRefreshStateStore : PropagationRefreshStateStore {
    private val states = linkedMapOf<String, PropagationRefreshSourceState>()

    override fun state(sourceKey: String): PropagationRefreshSourceState? {
        require(sourceKey.isNotBlank()) {
            "Propagation refresh source key must not be blank"
        }
        return states[sourceKey]
    }

    override fun save(state: PropagationRefreshSourceState) {
        val existing = states[state.sourceKey]
        require(existing == null || existing.role == state.role) {
            "Propagation refresh source role cannot change for " + state.sourceKey
        }
        states[state.sourceKey] = state
    }

    override fun all(): List<PropagationRefreshSourceState> =
        states.values.sortedBy { it.sourceKey }
}

enum class PropagationRefreshAttemptOutcome {
    SUCCESS,
    FAILURE,
}

data class PropagationRefreshAttempt(
    val sourceKey: String,
    val role: PropagationRefreshSourceRole,
    val attemptedAtUtcMillis: Long,
    val outcome: PropagationRefreshAttemptOutcome,
    val normalizedEvidenceCount: Int,
    val failureMessage: String? = null,
    val failureRetryable: Boolean? = null,
    val nextEligibleRefreshUtcMillis: Long,
) {
    init {
        require(sourceKey.isNotBlank()) {
            "Propagation refresh attempt source key must not be blank"
        }
        require(attemptedAtUtcMillis >= 0) {
            "Propagation refresh attempt UTC must be non-negative"
        }
        require(normalizedEvidenceCount >= 0) {
            "Propagation refresh normalized evidence count must be non-negative"
        }
        require(nextEligibleRefreshUtcMillis >= attemptedAtUtcMillis) {
            "Propagation refresh next-eligible UTC must not precede attempt UTC"
        }
        when (outcome) {
            PropagationRefreshAttemptOutcome.SUCCESS -> {
                require(failureMessage == null && failureRetryable == null) {
                    "Successful refresh attempt must not expose failure metadata"
                }
            }
            PropagationRefreshAttemptOutcome.FAILURE -> {
                require(!failureMessage.isNullOrBlank()) {
                    "Failed refresh attempt requires failure message"
                }
                require(failureRetryable != null) {
                    "Failed refresh attempt requires retryability"
                }
                require(normalizedEvidenceCount == 0) {
                    "Failed refresh attempt cannot report normalized evidence"
                }
            }
        }
    }
}

data class PropagationRefreshCycleResult(
    val attemptedAtUtcMillis: Long,
    val attempts: List<PropagationRefreshAttempt>,
    val skippedSourceKeys: List<String>,
    val sourceStates: List<PropagationRefreshSourceState>,
    val savedSnapshot: PropagationSnapshot?,
    val latestSnapshot: PropagationSnapshot?,
    val carriedForwardEvidenceCount: Int,
    val aggregationFailure: String? = null,
) {
    init {
        require(attemptedAtUtcMillis >= 0) {
            "Propagation refresh cycle UTC must be non-negative"
        }
        require(attempts.map { it.sourceKey }.distinct().size == attempts.size) {
            "Propagation refresh cycle cannot attempt one source twice"
        }
        require(skippedSourceKeys.distinct().size == skippedSourceKeys.size) {
            "Propagation refresh skipped source keys must be distinct"
        }
        require(
            attempts.map { it.sourceKey }.intersect(skippedSourceKeys.toSet()).isEmpty()
        ) {
            "Propagation refresh source cannot be both attempted and skipped"
        }
        require(carriedForwardEvidenceCount >= 0) {
            "Propagation refresh carried-forward evidence count must be non-negative"
        }
        aggregationFailure?.let {
            require(it.isNotBlank()) {
                "Propagation aggregation failure message must not be blank"
            }
            require(savedSnapshot == null) {
                "Failed aggregation cannot report a saved snapshot"
            }
        }
    }

    val successfulAttemptCount: Int
        get() = attempts.count { it.outcome == PropagationRefreshAttemptOutcome.SUCCESS }

    val failedAttemptCount: Int
        get() = attempts.count { it.outcome == PropagationRefreshAttemptOutcome.FAILURE }

    val partialFailure: Boolean
        get() = successfulAttemptCount > 0 && failedAttemptCount > 0
}
