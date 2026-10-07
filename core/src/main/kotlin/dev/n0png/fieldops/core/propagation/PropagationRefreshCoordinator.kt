package dev.n0png.fieldops.core.propagation

class PropagationSourceRefreshCoordinator(
    sources: List<PropagationRefreshSourceDefinition>,
    private val snapshotStore: PropagationSnapshotStore,
    private val stateStore: PropagationRefreshStateStore =
        InMemoryPropagationRefreshStateStore(),
    private val aggregator: PropagationSnapshotAggregator =
        PropagationSnapshotAggregator(),
) {
    private val sources = sources.sortedBy { it.sourceKey }

    init {
        require(this.sources.isNotEmpty()) {
            "Propagation refresh coordinator requires at least one source"
        }
        require(this.sources.map { it.sourceKey }.distinct().size == this.sources.size) {
            "Propagation refresh source keys must be unique"
        }
        val builtInRoles = this.sources
            .filter { it.role != PropagationRefreshSourceRole.GENERIC }
            .map { it.role }
        require(builtInRoles.distinct().size == builtInRoles.size) {
            "Built-in propagation refresh source roles must be unique"
        }

        this.sources.forEach { source ->
            val existing = stateStore.state(source.sourceKey)
            require(existing == null || existing.role == source.role) {
                "Propagation refresh source role changed for " + source.sourceKey
            }
            if (existing == null) {
                stateStore.save(
                    PropagationRefreshSourceState(
                        sourceKey = source.sourceKey,
                        role = source.role,
                    )
                )
            }
        }
    }

    fun refresh(nowUtcMillis: Long): PropagationRefreshCycleResult {
        require(nowUtcMillis >= 0) {
            "Propagation refresh UTC must be non-negative"
        }

        val latestBefore = snapshotStore.latest()
        latestBefore?.let {
            require(nowUtcMillis >= it.capturedAtUtcMillis) {
                "Propagation refresh UTC cannot precede latest cached snapshot"
            }
        }

        val successful = linkedMapOf<String, PropagationAggregationInput>()
        val attempts = mutableListOf<PropagationRefreshAttempt>()
        val skipped = mutableListOf<String>()

        for (source in sources) {
            val previous = requireNotNull(stateStore.state(source.sourceKey))
            previous.lastAttemptUtcMillis?.let {
                require(nowUtcMillis >= it) {
                    "Propagation refresh UTC cannot move backward for " + source.sourceKey
                }
            }

            if (nowUtcMillis < previous.nextEligibleRefreshUtcMillis) {
                skipped += source.sourceKey
                continue
            }

            val result = fetchSafely(source, nowUtcMillis)
            when (result) {
                is PropagationSourceFetchResult.Success -> {
                    val canonical = try {
                        canonicalize(
                            source = source,
                            input = result.input,
                            nowUtcMillis = nowUtcMillis,
                        )
                    } catch (e: IllegalArgumentException) {
                        val failure = PropagationSourceFetchResult.Failure(
                            message = sanitizeFailure(
                                "Normalized source contract rejected: " +
                                    (e.message ?: e::class.java.simpleName)
                            ),
                            retryable = false,
                        )
                        attempts += recordFailure(
                            source = source,
                            previous = previous,
                            nowUtcMillis = nowUtcMillis,
                            failure = failure,
                        )
                        continue
                    }

                    val nextEligible = saturatingAdd(
                        nowUtcMillis,
                        source.policy.cadenceMillis,
                    )
                    val state = PropagationRefreshSourceState(
                        sourceKey = source.sourceKey,
                        role = source.role,
                        lastAttemptUtcMillis = nowUtcMillis,
                        lastSuccessUtcMillis = nowUtcMillis,
                        consecutiveFailures = 0,
                        nextEligibleRefreshUtcMillis = nextEligible,
                    )
                    stateStore.save(state)
                    successful[source.sourceKey] = canonical
                    attempts += PropagationRefreshAttempt(
                        sourceKey = source.sourceKey,
                        role = source.role,
                        attemptedAtUtcMillis = nowUtcMillis,
                        outcome = PropagationRefreshAttemptOutcome.SUCCESS,
                        normalizedEvidenceCount = canonical.allEvidence().size,
                        nextEligibleRefreshUtcMillis = nextEligible,
                    )
                }

                is PropagationSourceFetchResult.Failure -> {
                    attempts += recordFailure(
                        source = source,
                        previous = previous,
                        nowUtcMillis = nowUtcMillis,
                        failure = result.copy(
                            message = sanitizeFailure(result.message),
                        ),
                    )
                }
            }
        }

        if (successful.isEmpty()) {
            return PropagationRefreshCycleResult(
                attemptedAtUtcMillis = nowUtcMillis,
                attempts = attempts,
                skippedSourceKeys = skipped.sorted(),
                sourceStates = statesForConfiguredSources(),
                savedSnapshot = null,
                latestSnapshot = latestBefore,
                carriedForwardEvidenceCount = 0,
            )
        }

        val successfulSources = sources.filter { it.sourceKey in successful.keys }
        val carriedForward = latestBefore?.let {
            carryForwardUnrefreshed(
                snapshot = it,
                refreshedSources = successfulSources,
            )
        }
        val carriedCount = carriedForward?.allEvidence()?.size ?: 0

        val inputs = buildList {
            if (carriedForward != null && carriedForward.allEvidence().isNotEmpty()) {
                add(carriedForward)
            }
            successful.values.forEach { add(it) }
        }

        if (inputs.flatMap { it.allEvidence() }.isEmpty()) {
            return PropagationRefreshCycleResult(
                attemptedAtUtcMillis = nowUtcMillis,
                attempts = attempts,
                skippedSourceKeys = skipped.sorted(),
                sourceStates = statesForConfiguredSources(),
                savedSnapshot = null,
                latestSnapshot = latestBefore,
                carriedForwardEvidenceCount = carriedCount,
            )
        }

        val snapshot = try {
            aggregator.aggregate(
                capturedAtUtcMillis = nowUtcMillis,
                inputs = inputs,
            )
        } catch (e: IllegalArgumentException) {
            return PropagationRefreshCycleResult(
                attemptedAtUtcMillis = nowUtcMillis,
                attempts = attempts,
                skippedSourceKeys = skipped.sorted(),
                sourceStates = statesForConfiguredSources(),
                savedSnapshot = null,
                latestSnapshot = latestBefore,
                carriedForwardEvidenceCount = carriedCount,
                aggregationFailure = sanitizeFailure(
                    "Propagation snapshot aggregation rejected: " +
                        (e.message ?: e::class.java.simpleName)
                ),
            )
        }

        snapshotStore.save(snapshot)

        return PropagationRefreshCycleResult(
            attemptedAtUtcMillis = nowUtcMillis,
            attempts = attempts,
            skippedSourceKeys = skipped.sorted(),
            sourceStates = statesForConfiguredSources(),
            savedSnapshot = snapshot,
            latestSnapshot = snapshotStore.latest(),
            carriedForwardEvidenceCount = carriedCount,
        )
    }

    private fun fetchSafely(
        source: PropagationRefreshSourceDefinition,
        nowUtcMillis: Long,
    ): PropagationSourceFetchResult =
        try {
            source.fetcher.fetch(nowUtcMillis)
        } catch (e: Exception) {
            PropagationSourceFetchResult.Failure(
                message = sanitizeFailure(
                    "Source fetch threw " +
                        e::class.java.simpleName +
                        ": " +
                        (e.message ?: "no message")
                ),
                retryable = true,
            )
        }

    private fun canonicalize(
        source: PropagationRefreshSourceDefinition,
        input: PropagationAggregationInput,
        nowUtcMillis: Long,
    ): PropagationAggregationInput {
        val all = input.allEvidence()
        val unmanaged = all.firstOrNull {
            !source.managesSourceId(it.source.sourceId)
        }
        require(unmanaged == null) {
            "Source " + source.sourceKey +
                " returned unmanaged provenance " +
                unmanaged?.source?.sourceId
        }

        all.forEach { evidence ->
            require(evidence.source.retrievedAtUtcMillis <= nowUtcMillis) {
                "Source " + source.sourceKey +
                    " returned retrieval provenance after refresh UTC"
            }
        }

        requireExpectedEvidenceCategory(source, input)

        return when (source.role) {
            PropagationRefreshSourceRole.NOAA_KP_FORECAST ->
                input.copy(
                    solarGeomagnetic = input.solarGeomagnetic.filterNot {
                        it.source.sourceId == "NOAA_SWPC_KP_FORECAST_OBSERVED"
                    }
                )

            else -> input
        }
    }

    private fun requireExpectedEvidenceCategory(
        source: PropagationRefreshSourceDefinition,
        input: PropagationAggregationInput,
    ) {
        when (source.role) {
            PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
            PropagationRefreshSourceRole.NOAA_KP_FORECAST,
            PropagationRefreshSourceRole.NOAA_F107,
            -> require(
                input.ionosphericProducts.isEmpty() &&
                    input.heardPaths.isEmpty() &&
                    input.modeledPaths.isEmpty()
            ) {
                "NOAA solar/geomagnetic refresh source returned a non-solar evidence category"
            }

            PropagationRefreshSourceRole.NOAA_GLOTEC -> require(
                input.solarGeomagnetic.isEmpty() &&
                    input.heardPaths.isEmpty() &&
                    input.modeledPaths.isEmpty()
            ) {
                "GloTEC refresh source returned a non-ionospheric evidence category"
            }

            PropagationRefreshSourceRole.PSK_REPORTER -> require(
                input.solarGeomagnetic.isEmpty() &&
                    input.ionosphericProducts.isEmpty() &&
                    input.modeledPaths.isEmpty()
            ) {
                "PSK Reporter refresh source returned a non-heard evidence category"
            }

            PropagationRefreshSourceRole.GENERIC -> Unit
        }
    }

    private fun recordFailure(
        source: PropagationRefreshSourceDefinition,
        previous: PropagationRefreshSourceState,
        nowUtcMillis: Long,
        failure: PropagationSourceFetchResult.Failure,
    ): PropagationRefreshAttempt {
        val failures = (previous.consecutiveFailures + 1)
            .coerceAtMost(MAX_TRACKED_CONSECUTIVE_FAILURES)
        val delay =
            if (failure.retryable) {
                retryDelay(source.policy, failures)
            } else {
                source.policy.cadenceMillis
            }
        val nextEligible = saturatingAdd(nowUtcMillis, delay)
        val message = sanitizeFailure(failure.message)
        val state = PropagationRefreshSourceState(
            sourceKey = source.sourceKey,
            role = source.role,
            lastAttemptUtcMillis = nowUtcMillis,
            lastSuccessUtcMillis = previous.lastSuccessUtcMillis,
            consecutiveFailures = failures,
            nextEligibleRefreshUtcMillis = nextEligible,
            lastFailureMessage = message,
            lastFailureRetryable = failure.retryable,
        )
        stateStore.save(state)
        return PropagationRefreshAttempt(
            sourceKey = source.sourceKey,
            role = source.role,
            attemptedAtUtcMillis = nowUtcMillis,
            outcome = PropagationRefreshAttemptOutcome.FAILURE,
            normalizedEvidenceCount = 0,
            failureMessage = message,
            failureRetryable = failure.retryable,
            nextEligibleRefreshUtcMillis = nextEligible,
        )
    }

    private fun retryDelay(
        policy: PropagationRefreshPolicy,
        failures: Int,
    ): Long {
        var delay = policy.initialRetryBackoffMillis
        repeat((failures - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)) {
            if (delay >= policy.maximumRetryBackoffMillis) {
                return policy.maximumRetryBackoffMillis
            }
            delay =
                if (delay > policy.maximumRetryBackoffMillis / 2L) {
                    policy.maximumRetryBackoffMillis
                } else {
                    delay * 2L
                }
        }
        return delay.coerceAtMost(policy.maximumRetryBackoffMillis)
    }

    private fun carryForwardUnrefreshed(
        snapshot: PropagationSnapshot,
        refreshedSources: List<PropagationRefreshSourceDefinition>,
    ): PropagationAggregationInput {
        fun keep(evidence: PropagationEvidence): Boolean =
            refreshedSources.none {
                it.managesSourceId(evidence.source.sourceId)
            }

        return PropagationAggregationInput(
            solarGeomagnetic = snapshot.solarGeomagnetic.filter(::keep),
            ionosphericProducts = snapshot.ionosphericProducts.filter(::keep),
            heardPaths = snapshot.heardPaths.filter(::keep),
            modeledPaths = snapshot.modeledPaths.filter(::keep),
        )
    }

    private fun statesForConfiguredSources(): List<PropagationRefreshSourceState> =
        sources.map { source ->
            requireNotNull(stateStore.state(source.sourceKey))
        }.sortedBy { it.sourceKey }

    private fun sanitizeFailure(message: String): String {
        val normalized = message
            .trim()
            .replace(Regex("""\s+"""), " ")
            .take(MAX_FAILURE_MESSAGE_LENGTH)
        return normalized.ifBlank { "unspecified propagation source failure" }
    }

    private fun saturatingAdd(
        base: Long,
        delta: Long,
    ): Long =
        if (Long.MAX_VALUE - base < delta) Long.MAX_VALUE else base + delta

    private companion object {
        const val MAX_TRACKED_CONSECUTIVE_FAILURES = 30
        const val MAX_BACKOFF_DOUBLINGS = 30
        const val MAX_FAILURE_MESSAGE_LENGTH = 240
    }
}
