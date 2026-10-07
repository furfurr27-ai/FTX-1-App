package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*

object PropagationRefreshCoordinatorTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) =
        checkThat(expected == actual, "$message expected=$expected actual=$actual")

    private fun expectFailure(message: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected failure: $message" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        canonicalNoaaKpSelectionAndCadence()
        partialFailureCarriesForwardCachedEvidence()
        retryBackoffIsBoundedAndResetsAfterSuccess()
        nonRetryableFailureUsesNormalCadence()
        sourceExceptionBecomesExplicitRetryableFailure()
        invalidNormalizedSourceContractsFailClosed()
        aggregationFailurePreservesLastGoodSnapshot()
        emptySuccessfulRefreshDoesNotEraseOnlyLastGoodSnapshot()
        stateStoreSurvivesCoordinatorRecreation()
        configurationAndTimeValidation()
        platformBoundary()

        println("CP-0008G propagation refresh coordinator tests: PASS assertions=$assertions")
    }

    private fun canonicalNoaaKpSelectionAndCadence() {
        val store = InMemoryPropagationSnapshotStore()
        var observedFetches = 0
        var forecastFetches = 0
        val policy = policy(cadence = 100, initialBackoff = 10, maxBackoff = 40)

        val observed = source(
            key = "kp-observed",
            role = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
            policy = policy,
        ) { now ->
            observedFetches++
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar(
                            evidenceId = "NOAA_SWPC_KP_OBSERVED:t1",
                            sourceId = "NOAA_SWPC_PLANETARY_KP",
                            retrievedAt = now,
                            observedAt = now,
                            kp = 2.0,
                        )
                    )
                )
            )
        }

        val forecast = source(
            key = "kp-forecast",
            role = PropagationRefreshSourceRole.NOAA_KP_FORECAST,
            policy = policy,
        ) { now ->
            forecastFetches++
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar(
                            evidenceId = "NOAA_SWPC_KP_OBSERVED:t1",
                            sourceId = "NOAA_SWPC_KP_FORECAST_OBSERVED",
                            retrievedAt = now,
                            observedAt = now,
                            kp = 2.0,
                        ),
                        solar(
                            evidenceId = "NOAA_SWPC_KP_ESTIMATED:t2",
                            sourceId = "NOAA_SWPC_KP_FORECAST_ESTIMATED",
                            retrievedAt = now,
                            observedAt = now,
                            kp = 2.3,
                        ),
                        solar(
                            evidenceId = "NOAA_SWPC_KP_PREDICTED:t3",
                            sourceId = "NOAA_SWPC_KP_FORECAST_PREDICTED",
                            retrievedAt = now,
                            observedAt = now,
                            kp = 3.0,
                            sourceClass = PropagationSourceClass.FORECAST,
                        ),
                    )
                )
            )
        }

        val coordinator = PropagationSourceRefreshCoordinator(
            sources = listOf(forecast, observed),
            snapshotStore = store,
        )
        val first = coordinator.refresh(100)

        eq(2, first.attempts.size, "both NOAA sources attempted")
        eq(2, first.successfulAttemptCount, "both NOAA sources succeeded")
        eq(0, first.failedAttemptCount, "no NOAA failure")
        checkThat(!first.partialFailure, "complete success is not partial failure")
        val snapshot = requireNotNull(first.savedSnapshot)
        eq(3, snapshot.solarGeomagnetic.size, "canonical NOAA set contains dedicated observed plus forecast estimate/prediction")
        checkThat(
            snapshot.solarGeomagnetic.any { it.source.sourceId == "NOAA_SWPC_PLANETARY_KP" },
            "dedicated observed Kp retained",
        )
        checkThat(
            snapshot.solarGeomagnetic.any { it.source.sourceId == "NOAA_SWPC_KP_FORECAST_ESTIMATED" },
            "forecast estimated Kp retained",
        )
        checkThat(
            snapshot.solarGeomagnetic.any { it.source.sourceId == "NOAA_SWPC_KP_FORECAST_PREDICTED" },
            "forecast predicted Kp retained",
        )
        checkThat(
            snapshot.solarGeomagnetic.none { it.source.sourceId == "NOAA_SWPC_KP_FORECAST_OBSERVED" },
            "forecast observed Kp excluded in favor of dedicated observed feed",
        )
        eq(0, first.carriedForwardEvidenceCount, "first snapshot has no carry-forward")
        eq(listOf("kp-forecast", "kp-observed"), first.sourceStates.map { it.sourceKey }, "state ordering deterministic")
        first.sourceStates.forEach {
            eq(100L, it.lastAttemptUtcMillis, "attempt time tracked")
            eq(100L, it.lastSuccessUtcMillis, "success time tracked")
            eq(0, it.consecutiveFailures, "success clears failures")
            eq(200L, it.nextEligibleRefreshUtcMillis, "cadence sets next eligible")
        }

        val early = coordinator.refresh(150)
        eq(0, early.attempts.size, "cadence skips early refresh")
        eq(listOf("kp-forecast", "kp-observed"), early.skippedSourceKeys, "skipped keys deterministic")
        eq(null, early.savedSnapshot, "skip cycle does not write snapshot")
        eq(snapshot.snapshotId, early.latestSnapshot?.snapshotId, "skip cycle retains latest snapshot")
        eq(1, observedFetches, "observed fetcher not called while ineligible")
        eq(1, forecastFetches, "forecast fetcher not called while ineligible")
    }

    private fun partialFailureCarriesForwardCachedEvidence() {
        val store = InMemoryPropagationSnapshotStore()
        var cycle = 0
        val policy = policy(100, 10, 40)

        val a = source("GEN_A", PropagationRefreshSourceRole.GENERIC, policy) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar(
                            evidenceId = "A-$cycle",
                            sourceId = "GEN_A",
                            retrievedAt = now,
                            observedAt = now,
                            kp = 1.0 + cycle,
                        )
                    )
                )
            )
        }
        val b = source("GEN_B", PropagationRefreshSourceRole.GENERIC, policy) { now ->
            if (cycle == 0) {
                PropagationSourceFetchResult.Success(
                    PropagationAggregationInput(
                        solarGeomagnetic = listOf(
                            solar(
                                evidenceId = "B-0",
                                sourceId = "GEN_B",
                                retrievedAt = now,
                                observedAt = now,
                                kp = 4.0,
                            )
                        )
                    )
                )
            } else {
                PropagationSourceFetchResult.Failure("temporary B outage", retryable = true)
            }
        }

        val coordinator = PropagationSourceRefreshCoordinator(listOf(a, b), store)
        val initial = coordinator.refresh(100)
        val first = requireNotNull(initial.savedSnapshot)
        eq(setOf("A-0", "B-0"), first.solarGeomagnetic.map { it.evidenceId }.toSet(), "initial sources persisted")

        cycle = 1
        val partial = coordinator.refresh(200)
        checkThat(partial.partialFailure, "one success plus one failure marked partial")
        eq(1, partial.successfulAttemptCount, "one source succeeds")
        eq(1, partial.failedAttemptCount, "one source fails")
        eq(1, partial.carriedForwardEvidenceCount, "failed-source evidence carried forward")
        val second = requireNotNull(partial.savedSnapshot)
        eq(setOf("A-1", "B-0"), second.solarGeomagnetic.map { it.evidenceId }.toSet(), "successful source replaced while failed source carried")
        checkThat(second.solarGeomagnetic.none { it.evidenceId == "A-0" }, "old successful-source evidence replaced")
        val carriedB = second.solarGeomagnetic.single { it.evidenceId == "B-0" }
        eq(100L, carriedB.source.retrievedAtUtcMillis, "carry-forward preserves original retrieval provenance")
        eq(second.snapshotId, store.latest()?.snapshotId, "partial refresh becomes latest inspectable snapshot")

        val onlyFailure = coordinator.refresh(210)
        eq(1, onlyFailure.attempts.size, "only retry-eligible failed source attempted")
        eq("GEN_B", onlyFailure.attempts.single().sourceKey, "B retried while A remains cadence-blocked")
        eq(listOf("GEN_A"), onlyFailure.skippedSourceKeys, "successful A correctly cadence-skipped")
        eq(null, onlyFailure.savedSnapshot, "all attempted failures do not write new snapshot")
        eq(second.snapshotId, onlyFailure.latestSnapshot?.snapshotId, "last good snapshot preserved when attempted sources all fail")
    }

    private fun retryBackoffIsBoundedAndResetsAfterSuccess() {
        val store = InMemoryPropagationSnapshotStore()
        val states = InMemoryPropagationRefreshStateStore()
        var calls = 0
        val source = source(
            "RETRY",
            PropagationRefreshSourceRole.GENERIC,
            policy(1_000, 10, 40),
        ) { now ->
            calls++
            if (calls <= 4) {
                PropagationSourceFetchResult.Failure("retryable-$calls", retryable = true)
            } else {
                PropagationSourceFetchResult.Success(
                    PropagationAggregationInput(
                        solarGeomagnetic = listOf(
                            solar("retry-ok", "RETRY", now, now, 2.0)
                        )
                    )
                )
            }
        }
        val coordinator = PropagationSourceRefreshCoordinator(listOf(source), store, states)

        val f1 = coordinator.refresh(0).attempts.single()
        eq(10L, f1.nextEligibleRefreshUtcMillis, "first retry delay")
        eq(1, states.state("RETRY")?.consecutiveFailures, "first failure counted")

        val skipped = coordinator.refresh(9)
        eq(0, skipped.attempts.size, "backoff prevents early retry")
        eq(1, calls, "fetcher not called during backoff")

        val f2 = coordinator.refresh(10).attempts.single()
        eq(30L, f2.nextEligibleRefreshUtcMillis, "second delay doubles to 20")
        val f3 = coordinator.refresh(30).attempts.single()
        eq(70L, f3.nextEligibleRefreshUtcMillis, "third delay caps at 40")
        val f4 = coordinator.refresh(70).attempts.single()
        eq(110L, f4.nextEligibleRefreshUtcMillis, "fourth delay remains capped at 40")
        eq(4, states.state("RETRY")?.consecutiveFailures, "failure streak retained")

        val ok = coordinator.refresh(110)
        eq(PropagationRefreshAttemptOutcome.SUCCESS, ok.attempts.single().outcome, "source recovers")
        eq(1_110L, ok.attempts.single().nextEligibleRefreshUtcMillis, "success returns to normal cadence")
        eq(0, states.state("RETRY")?.consecutiveFailures, "success resets failure streak")
        eq(null, states.state("RETRY")?.lastFailureMessage, "success clears failure message")
        eq(110L, states.state("RETRY")?.lastSuccessUtcMillis, "success timestamp tracked")
        checkThat(ok.savedSnapshot != null, "successful recovery persists snapshot")
    }

    private fun nonRetryableFailureUsesNormalCadence() {
        val states = InMemoryPropagationRefreshStateStore()
        val source = source(
            "NONRETRY",
            PropagationRefreshSourceRole.GENERIC,
            policy(500, 10, 40),
        ) {
            PropagationSourceFetchResult.Failure("schema rejected", retryable = false)
        }
        val coordinator = PropagationSourceRefreshCoordinator(
            listOf(source),
            InMemoryPropagationSnapshotStore(),
            states,
        )
        val result = coordinator.refresh(100)
        val attempt = result.attempts.single()
        eq(PropagationRefreshAttemptOutcome.FAILURE, attempt.outcome, "non-retryable result is failure")
        eq(false, attempt.failureRetryable, "non-retryable flag retained")
        eq(600L, attempt.nextEligibleRefreshUtcMillis, "non-retryable failure waits normal cadence")
        eq(false, states.state("NONRETRY")?.lastFailureRetryable, "state retains non-retryable flag")
        eq(null, result.savedSnapshot, "failure writes no snapshot")
    }

    private fun sourceExceptionBecomesExplicitRetryableFailure() {
        val source = source(
            "THROWER",
            PropagationRefreshSourceRole.GENERIC,
            policy(500, 10, 40),
        ) {
            throw IllegalStateException("simulated transport exception")
        }
        val result = PropagationSourceRefreshCoordinator(
            listOf(source),
            InMemoryPropagationSnapshotStore(),
        ).refresh(50)
        val attempt = result.attempts.single()
        eq(PropagationRefreshAttemptOutcome.FAILURE, attempt.outcome, "exception isolated as source failure")
        eq(true, attempt.failureRetryable, "source exception treated as retryable")
        checkThat(attempt.failureMessage!!.contains("IllegalStateException"), "exception class retained")
        checkThat(attempt.failureMessage!!.contains("simulated transport exception"), "exception detail retained")
        eq(60L, attempt.nextEligibleRefreshUtcMillis, "exception uses retry backoff")
    }

    private fun invalidNormalizedSourceContractsFailClosed() {
        val wrongProvenance = source(
            "EXPECTED",
            PropagationRefreshSourceRole.GENERIC,
            policy(100, 10, 40),
        ) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("bad-provenance", "OTHER", now, now, 2.0)
                    )
                )
            )
        }
        val provenanceResult = PropagationSourceRefreshCoordinator(
            listOf(wrongProvenance),
            InMemoryPropagationSnapshotStore(),
        ).refresh(100)
        val provenanceAttempt = provenanceResult.attempts.single()
        eq(PropagationRefreshAttemptOutcome.FAILURE, provenanceAttempt.outcome, "unmanaged provenance rejected")
        eq(false, provenanceAttempt.failureRetryable, "normalized contract rejection not rapid-retryable")
        checkThat(provenanceAttempt.failureMessage!!.contains("unmanaged provenance"), "provenance failure explicit")
        eq(null, provenanceResult.savedSnapshot, "invalid normalized input not persisted")

        val wrongCategory = source(
            "KP",
            PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
            policy(100, 10, 40),
        ) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    heardPaths = listOf(
                        heard(
                            evidenceId = "wrong-category",
                            sourceId = "NOAA_SWPC_PLANETARY_KP",
                            retrievedAt = now,
                            observedAt = now,
                        )
                    )
                )
            )
        }
        val categoryAttempt = PropagationSourceRefreshCoordinator(
            listOf(wrongCategory),
            InMemoryPropagationSnapshotStore(),
        ).refresh(100).attempts.single()
        eq(PropagationRefreshAttemptOutcome.FAILURE, categoryAttempt.outcome, "wrong evidence category rejected")
        eq(false, categoryAttempt.failureRetryable, "category contract rejection not rapid-retryable")
        checkThat(categoryAttempt.failureMessage!!.contains("non-solar"), "category failure explicit")

        val futureRetrieval = source(
            "FUTURE",
            PropagationRefreshSourceRole.GENERIC,
            policy(100, 10, 40),
        ) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("future-retrieval", "FUTURE", now + 1, now, 2.0)
                    )
                )
            )
        }
        val futureAttempt = PropagationSourceRefreshCoordinator(
            listOf(futureRetrieval),
            InMemoryPropagationSnapshotStore(),
        ).refresh(100).attempts.single()
        eq(PropagationRefreshAttemptOutcome.FAILURE, futureAttempt.outcome, "future retrieval provenance rejected")
        eq(false, futureAttempt.failureRetryable, "future provenance is normalized-contract failure")
        checkThat(futureAttempt.failureMessage!!.contains("after refresh UTC"), "future retrieval failure explicit")
    }

    private fun aggregationFailurePreservesLastGoodSnapshot() {
        val store = InMemoryPropagationSnapshotStore()
        val seed = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = 100,
            inputs = listOf(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("seed", "SEED", 100, 100, 1.0)
                    )
                )
            ),
        )
        store.save(seed)

        val a = source("COLLIDE_A", PropagationRefreshSourceRole.GENERIC, policy(100, 10, 40)) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("collision", "COLLIDE_A", now, now, 2.0)
                    )
                )
            )
        }
        val b = source("COLLIDE_B", PropagationRefreshSourceRole.GENERIC, policy(100, 10, 40)) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("collision", "COLLIDE_B", now, now, 3.0)
                    )
                )
            )
        }
        val result = PropagationSourceRefreshCoordinator(listOf(a, b), store).refresh(200)

        eq(2, result.successfulAttemptCount, "both sources fetched successfully before aggregate conflict")
        checkThat(result.aggregationFailure != null, "aggregate conflict exposed")
        checkThat(result.aggregationFailure!!.contains("Conflicting solar/geomagnetic evidence"), "aggregate conflict reason retained")
        eq(null, result.savedSnapshot, "aggregate conflict writes no snapshot")
        eq(seed.snapshotId, result.latestSnapshot?.snapshotId, "last good snapshot returned")
        eq(seed.snapshotId, store.latest()?.snapshotId, "last good snapshot remains authoritative")
        eq(1, store.history(10).size, "failed aggregation does not mutate snapshot history")
        eq(1, result.carriedForwardEvidenceCount, "unmanaged seed evidence remained available to attempted aggregate")
    }

    private fun emptySuccessfulRefreshDoesNotEraseOnlyLastGoodSnapshot() {
        val store = InMemoryPropagationSnapshotStore()
        val seed = PropagationSnapshotAggregator().aggregate(
            capturedAtUtcMillis = 100,
            inputs = listOf(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("old-empty-source", "EMPTY", 100, 100, 1.0)
                    )
                )
            ),
        )
        store.save(seed)
        val empty = source(
            "EMPTY",
            PropagationRefreshSourceRole.GENERIC,
            policy(100, 10, 40),
        ) {
            PropagationSourceFetchResult.Success(PropagationAggregationInput())
        }
        val result = PropagationSourceRefreshCoordinator(listOf(empty), store).refresh(200)
        eq(1, result.successfulAttemptCount, "empty source response still counts as successful refresh")
        eq(0, result.attempts.single().normalizedEvidenceCount, "empty success exposes zero evidence")
        eq(null, result.savedSnapshot, "empty-only replacement does not create invalid empty snapshot")
        eq(seed.snapshotId, result.latestSnapshot?.snapshotId, "last good nonempty snapshot remains inspectable")
        eq(0, result.carriedForwardEvidenceCount, "old managed evidence intentionally not carried as current")
    }

    private fun stateStoreSurvivesCoordinatorRecreation() {
        val snapshots = InMemoryPropagationSnapshotStore()
        val states = InMemoryPropagationRefreshStateStore()
        var calls = 0
        val definition = source(
            "PERSIST_STATE",
            PropagationRefreshSourceRole.GENERIC,
            policy(100, 10, 40),
        ) { now ->
            calls++
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(
                        solar("state-$calls", "PERSIST_STATE", now, now, 2.0)
                    )
                )
            )
        }

        PropagationSourceRefreshCoordinator(
            listOf(definition),
            snapshots,
            states,
        ).refresh(100)
        val recreated = PropagationSourceRefreshCoordinator(
            listOf(definition),
            snapshots,
            states,
        )
        val early = recreated.refresh(150)
        eq(0, early.attempts.size, "recreated coordinator respects stored cadence state")
        eq(1, calls, "recreation does not bypass cadence")
        eq(200L, states.state("PERSIST_STATE")?.nextEligibleRefreshUtcMillis, "next eligible state survives recreation")

        val due = recreated.refresh(200)
        eq(1, due.attempts.size, "recreated coordinator refreshes when stored cadence becomes due")
        eq(2, calls, "due refresh calls source after recreation")
        eq(2, snapshots.history(10).size, "both successful refresh snapshots retained")
    }

    private fun configurationAndTimeValidation() {
        expectFailure("empty source list") {
            PropagationSourceRefreshCoordinator(
                emptyList(),
                InMemoryPropagationSnapshotStore(),
            )
        }

        val p = policy(100, 10, 40)
        val one = source("DUP", PropagationRefreshSourceRole.GENERIC, p) {
            PropagationSourceFetchResult.Success(PropagationAggregationInput())
        }
        val two = source("DUP", PropagationRefreshSourceRole.GENERIC, p) {
            PropagationSourceFetchResult.Success(PropagationAggregationInput())
        }
        expectFailure("duplicate source keys") {
            PropagationSourceRefreshCoordinator(listOf(one, two), InMemoryPropagationSnapshotStore())
        }

        val kp1 = source("K1", PropagationRefreshSourceRole.NOAA_KP_OBSERVED, p) {
            PropagationSourceFetchResult.Success(PropagationAggregationInput())
        }
        val kp2 = source("K2", PropagationRefreshSourceRole.NOAA_KP_OBSERVED, p) {
            PropagationSourceFetchResult.Success(PropagationAggregationInput())
        }
        expectFailure("duplicate built-in role") {
            PropagationSourceRefreshCoordinator(listOf(kp1, kp2), InMemoryPropagationSnapshotStore())
        }

        expectFailure("zero cadence") { PropagationRefreshPolicy(0, 1, 1) }
        expectFailure("zero initial backoff") { PropagationRefreshPolicy(1, 0, 1) }
        expectFailure("max backoff below initial") { PropagationRefreshPolicy(1, 2, 1) }
        expectFailure("blank source key") {
            PropagationRefreshSourceDefinition(
                sourceKey = " ",
                role = PropagationRefreshSourceRole.GENERIC,
                policy = p,
                fetcher = PropagationSourceFetcher {
                    PropagationSourceFetchResult.Success(PropagationAggregationInput())
                },
            )
        }

        val store = InMemoryPropagationSnapshotStore()
        val time = source("TIME", PropagationRefreshSourceRole.GENERIC, p) { now ->
            PropagationSourceFetchResult.Success(
                PropagationAggregationInput(
                    solarGeomagnetic = listOf(solar("time", "TIME", now, now, 2.0))
                )
            )
        }
        val coordinator = PropagationSourceRefreshCoordinator(listOf(time), store)
        coordinator.refresh(100)
        expectFailure("refresh cannot move before latest snapshot") { coordinator.refresh(99) }
        expectFailure("negative refresh time") { coordinator.refresh(-1) }

        val states = InMemoryPropagationRefreshStateStore()
        states.save(
            PropagationRefreshSourceState(
                sourceKey = "ROLE",
                role = PropagationRefreshSourceRole.GENERIC,
            )
        )
        expectFailure("state role cannot change") {
            states.save(
                PropagationRefreshSourceState(
                    sourceKey = "ROLE",
                    role = PropagationRefreshSourceRole.NOAA_F107,
                )
            )
        }
    }

    private fun platformBoundary() {
        val classes = listOf(
            PropagationRefreshSourceDefinition::class.java,
            PropagationRefreshSourceState::class.java,
            PropagationRefreshCycleResult::class.java,
            PropagationSourceRefreshCoordinator::class.java,
        )
        val fields = classes.flatMap { it.declaredFields.toList() }
        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("WorkManager") ||
                    it.type.name.contains("OkHttp") ||
                    it.type.name.contains("Retrofit") ||
                    it.type.name.contains("HttpURLConnection")
            },
            "refresh coordinator has no Android or concrete network field types",
        )
        checkThat(
            fields.none {
                it.name.contains("password", ignoreCase = true) ||
                    it.name.contains("credential", ignoreCase = true) ||
                    it.name.contains("apiKey", ignoreCase = true)
            },
            "refresh coordinator has no credential fields",
        )
    }

    private fun source(
        key: String,
        role: PropagationRefreshSourceRole,
        policy: PropagationRefreshPolicy,
        fetch: (Long) -> PropagationSourceFetchResult,
    ) = PropagationRefreshSourceDefinition(
        sourceKey = key,
        role = role,
        policy = policy,
        fetcher = PropagationSourceFetcher(fetch),
    )

    private fun policy(
        cadence: Long,
        initialBackoff: Long,
        maxBackoff: Long,
    ) = PropagationRefreshPolicy(
        cadenceMillis = cadence,
        initialRetryBackoffMillis = initialBackoff,
        maximumRetryBackoffMillis = maxBackoff,
    )

    private fun solar(
        evidenceId: String,
        sourceId: String,
        retrievedAt: Long,
        observedAt: Long,
        kp: Double,
        sourceClass: PropagationSourceClass = PropagationSourceClass.DERIVED_PRODUCT,
    ) = SolarGeomagneticObservation(
        evidenceId = evidenceId,
        source = PropagationSourceRef(
            sourceId = sourceId,
            providerName = "CP-0008G fake " + sourceId,
            sourceClass = sourceClass,
            sourceVersion = "cp0008g-test-v1",
            retrievedAtUtcMillis = retrievedAt,
            sourceUrl = "https://example.invalid/" + sourceId,
        ),
        observedAtUtcMillis = observedAt,
        confidence = PropagationConfidence(
            value = 0.8,
            basis = PropagationConfidenceBasis.PROVIDER_REPORTED,
            explanation = "Deterministic CP-0008G fake source",
        ),
        quality = setOf(PropagationDataQuality.PROVISIONAL),
        planetaryKp = kp,
    )

    private fun heard(
        evidenceId: String,
        sourceId: String,
        retrievedAt: Long,
        observedAt: Long,
    ) = HeardPathObservation(
        evidenceId = evidenceId,
        source = PropagationSourceRef(
            sourceId = sourceId,
            providerName = "CP-0008G fake heard",
            sourceClass = PropagationSourceClass.MEASUREMENT,
            sourceVersion = "cp0008g-test-v1",
            retrievedAtUtcMillis = retrievedAt,
            sourceUrl = "https://example.invalid/heard",
        ),
        observedAtUtcMillis = observedAt,
        confidence = PropagationConfidence(
            value = 0.8,
            basis = PropagationConfidenceBasis.PROVIDER_REPORTED,
            explanation = "Deterministic CP-0008G fake heard path",
        ),
        quality = setOf(PropagationDataQuality.PROVISIONAL),
        transmitter = PropagationEndpoint(
            location = PropagationPosition(
                maidenheadGrid = "JO40",
                method = PropagationLocationMethod.EXPLICIT_GRID,
                sourceReference = "cp0008g-test",
            ),
            callsign = "N0PNG",
        ),
        receiver = PropagationEndpoint(
            location = PropagationPosition(
                maidenheadGrid = "FN31",
                method = PropagationLocationMethod.EXPLICIT_GRID,
                sourceReference = "cp0008g-test",
            ),
            callsign = "K1ABC",
        ),
        frequencyHz = 14_074_000,
        band = "20m",
        mode = "FT8",
        snrDb = -8.0,
    )
}
