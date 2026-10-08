package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

object PropagationSourceStatusTests {
    private var assertions = 0
    private fun yes(condition: Boolean, note: String) {
        assertions++
        check(condition) { note }
    }
    private fun <T> eq(expected: T, actual: T, note: String) {
        assertions++
        check(expected == actual) { "$note: expected=$expected actual=$actual" }
    }
    private fun failure(note: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected failure: $note" }
    }
    private fun temporary(block: (Path) -> Unit) {
        val path = Files.createTempDirectory("cp0008l-status-")
        try { block(path) } finally {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private const val NOW = 2_000_000_000L
    private fun state(
        key: String = "NOAA_SWPC_PLANETARY_KP",
        role: PropagationRefreshSourceRole = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        attempt: Long? = NOW - 60_000L,
        success: Long? = NOW - 60_000L,
        eligible: Long = NOW + 60_000L,
        failures: Int = 0,
        message: String? = null,
        retryable: Boolean? = null,
    ) = PropagationRefreshSourceState(
        sourceKey = key, role = role, lastAttemptUtcMillis = attempt,
        lastSuccessUtcMillis = success, consecutiveFailures = failures,
        nextEligibleRefreshUtcMillis = eligible,
        lastFailureMessage = message, lastFailureRetryable = retryable,
    )

    private fun evidence(
        id: String,
        source: String = "NOAA_SWPC_PLANETARY_KP",
        observed: Long = NOW - 1_000L,
        retrieved: Long = NOW - 500L,
    ) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = source,
            providerName = "deterministic test fixture",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "v1",
            retrievedAtUtcMillis = retrieved,
        ),
        observedAtUtcMillis = observed,
        confidence = PropagationConfidence(
            value = 0.9,
            basis = PropagationConfidenceBasis.SYNTHETIC,
            explanation = "Synthetic offline test only",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 3.0,
    )

    private fun snapshot(vararg records: SolarGeomagneticObservation) =
        PropagationSnapshot(
            snapshotId = "test-readonly-snapshot",
            capturedAtUtcMillis = NOW + 30_000L,
            solarGeomagnetic = records.toList(),
        )

    private fun row(
        store: PropagationRefreshStateStore,
        snapshotStore: PropagationSnapshotStore = InMemoryPropagationSnapshotStore(),
        now: Long = NOW,
    ) = PropagationSourceStatusService(store, snapshotStore).project(now).sources.single()

    @JvmStatic
    fun main(args: Array<String>) {
        emptyProjectionAndExplicitUtc()
        statusReadinessAndCountdownBoundaries()
        retryableBackoffAndFailureCooldown()
        noEvidenceVsSuccessfulFetch()
        perEvidenceFreshnessAndProvenance()
        forecastMatchingAndSourceIsolation()
        futureDatedDoesNotBecomeFresh()
        deterministicOrderAndDuplicateFailure()
        persistedStateAndSnapshotSurviveRuntimeRecreation()
        readOnlyOperationAndNoProviderInvocation()
        println("CP-0008L source-status presentation tests: PASS assertions=$assertions")
    }

    private fun emptyProjectionAndExplicitUtc() {
        val states = InMemoryPropagationRefreshStateStore()
        val result = PropagationSourceStatusService(
            states, InMemoryPropagationSnapshotStore()
        ).project(NOW)
        eq(emptyList<PropagationSourceStatusRow>(), result.sources, "no sources")
        eq(null, result.snapshotId, "no snapshot is not fabricated")
        eq(null, result.snapshotCapturedAtUtcMillis, "no invented capture time")
        yes(!result.snapshotIsFutureDated, "no snapshot is not future")
        eq(0, result.readyCount, "zero ready")
        eq(0, result.sourcesWithEvidenceCount, "zero evidence")
        failure("negative UTC") {
            PropagationSourceStatusService(states, InMemoryPropagationSnapshotStore()).project(-1L)
        }
    }

    private fun statusReadinessAndCountdownBoundaries() {
        val states = InMemoryPropagationRefreshStateStore()
        val never = state(attempt = null, success = null, eligible = 0L)
        states.save(never)
        val first = row(states)
        eq(PropagationSourceLastAttempt.NEVER_ATTEMPTED, first.lastAttempt, "unattempted")
        eq(PropagationSourceRefreshReadiness.READY, first.readiness, "new state ready")
        eq(0L, first.remainingWaitMillis, "no waiting for never attempted")
        states.save(state(eligible = NOW + 90_000L))
        val waiting = row(states)
        eq(PropagationSourceRefreshReadiness.CADENCE_WAIT, waiting.readiness, "cadence wait")
        eq(90_000L, waiting.remainingWaitMillis, "exact time until eligible")
        eq(PropagationSourceLastAttempt.SUCCEEDED, waiting.lastAttempt, "last attempt success")
        eq(NOW - 60_000L, waiting.lastSuccessUtcMillis, "success timestamp separate")
        eq(60_000L, waiting.lastSuccessAgeMillis, "success age")
        eq(0L, row(states, now = NOW + 90_000L).remainingWaitMillis, "exact boundary ready")
        eq(PropagationSourceRefreshReadiness.READY,
            row(states, now = NOW + 90_000L).readiness, "inclusive due")
        eq(0L, row(states, now = NOW + 999_999L).remainingWaitMillis, "overdue clamped")
    }

    private fun retryableBackoffAndFailureCooldown() {
        val states = InMemoryPropagationRefreshStateStore()
        val failed = state(failures = 3, message = "HTTP 503", retryable = true,
            success = NOW - 15 * 60_000L, eligible = NOW + 4 * 60_000L)
        states.save(failed)
        val backoff = row(states)
        eq(PropagationSourceRefreshReadiness.RETRY_BACKOFF, backoff.readiness,
            "retryable failure respects backoff")
        eq(PropagationSourceLastAttempt.FAILED, backoff.lastAttempt, "failure visible")
        eq(3, backoff.consecutiveFailures, "failure count")
        eq("HTTP 503", backoff.lastFailureMessage, "last failure visible")
        eq(true, backoff.lastFailureRetryable, "retryable visible")
        eq(240_000L, backoff.remainingWaitMillis, "remaining backoff")
        eq(NOW - 15 * 60_000L, backoff.lastSuccessUtcMillis,
            "past success not rewritten on failure")
        states.save(failed.copy(lastFailureRetryable = false))
        eq(PropagationSourceRefreshReadiness.FAILURE_COOLDOWN, row(states).readiness,
            "nonretryable failure waits for normal cadence")
        val readyFailed = row(states, now = NOW + 4 * 60_000L)
        eq(PropagationSourceRefreshReadiness.READY, readyFailed.readiness, "failure due again")
        eq(PropagationSourceLastAttempt.FAILED, readyFailed.lastAttempt,
            "ready does not imply last attempt was successful")
        eq("HTTP 503", readyFailed.lastFailureMessage, "ready retains failure")
    }

    private fun noEvidenceVsSuccessfulFetch() {
        val states = InMemoryPropagationRefreshStateStore().also { it.save(state()) }
        val status = row(states)
        eq(0, status.evidenceCount, "success does not imply evidence")
        yes(!status.hasCachedEvidence, "no fabricated evidence")
        eq(null, status.newestEvidenceObservedUtcMillis, "no invented observed time")
        eq(null, status.newestEvidenceRetrievedUtcMillis, "no invented retrieved time")
        eq(0, status.evidenceFreshnessCounts.values.sum(), "all freshness counts zero")
    }

    private fun perEvidenceFreshnessAndProvenance() {
        val states = InMemoryPropagationRefreshStateStore().also { it.save(state()) }
        val oldest = evidence("stale", observed = NOW - 7 * 60 * 60_000L,
            retrieved = NOW - 7 * 60 * 60_000L)
        val recent = evidence("recent", observed = NOW - 500L, retrieved = NOW - 100L)
        val snapshotStore = InMemoryPropagationSnapshotStore().also {
            it.save(snapshot(recent, oldest))
        }
        val projection = PropagationSourceStatusService(states, snapshotStore).project(NOW)
        val line = projection.sources.single()
        eq(2, line.evidenceCount, "two source-matched records")
        eq(NOW - 7 * 60 * 60_000L, line.oldestEvidenceObservedUtcMillis,
            "oldest observed from evidence")
        eq(NOW - 500L, line.newestEvidenceObservedUtcMillis, "newest observed")
        eq(NOW - 7 * 60 * 60_000L, line.oldestEvidenceRetrievedUtcMillis,
            "oldest retrieval independent of observation")
        eq(NOW - 100L, line.newestEvidenceRetrievedUtcMillis, "latest retrieved")
        eq(1, line.evidenceFreshnessCounts[PropagationFreshness.FRESH], "fresh count")
        eq(1, line.evidenceFreshnessCounts[PropagationFreshness.STALE], "stale count")
        eq(0, line.evidenceFreshnessCounts[PropagationFreshness.AGING], "no aging")
        eq("test-readonly-snapshot", projection.snapshotId, "snapshot identity")
        yes(projection.snapshotIsFutureDated, "future-dated cache capture explicit")
        eq(1, projection.sourcesWithEvidenceCount, "one source with evidence")
    }

    private fun forecastMatchingAndSourceIsolation() {
        val states = InMemoryPropagationRefreshStateStore()
        states.save(state())
        states.save(state(key = "NOAA_SWPC_KP_FORECAST",
            role = PropagationRefreshSourceRole.NOAA_KP_FORECAST))
        states.save(state(key = "NOAA_SWPC_F107_SUMMARY",
            role = PropagationRefreshSourceRole.NOAA_F107))
        val snapshotStore = InMemoryPropagationSnapshotStore()
        snapshotStore.save(snapshot(
            evidence("forecast-a", "NOAA_SWPC_KP_FORECAST_ESTIMATED"),
            evidence("forecast-b", "NOAA_SWPC_KP_FORECAST_PREDICTED"),
            evidence("observed"),
            evidence("f107", "NOAA_SWPC_F107_SUMMARY"),
        ))
        val projection = PropagationSourceStatusService(states, snapshotStore).project(NOW)
        val byKey = projection.sources.associateBy { it.sourceKey }
        eq(2, byKey.getValue("NOAA_SWPC_KP_FORECAST").evidenceCount,
            "forecast evidence variants mapped to forecast role")
        eq(1, byKey.getValue("NOAA_SWPC_PLANETARY_KP").evidenceCount,
            "observed NOAA stays separate")
        eq(1, byKey.getValue("NOAA_SWPC_F107_SUMMARY").evidenceCount,
            "F107 stays separate")
        eq(4, projection.sources.sumOf { it.evidenceCount },
            "each evidence record assigned only once")
    }

    private fun futureDatedDoesNotBecomeFresh() {
        val states = InMemoryPropagationRefreshStateStore().also {
            it.save(state(attempt = NOW + 30_000L, success = NOW + 30_000L,
                eligible = NOW + 60_000L))
        }
        val snapshotStore = InMemoryPropagationSnapshotStore().also {
            it.save(snapshot(evidence("future", observed = NOW + 10_000L,
                retrieved = NOW - 100L)))
        }
        val line = row(states, snapshotStore)
        eq(null, line.lastSuccessAgeMillis, "future success age not negative")
        yes(line.lastSuccessIsFutureDated, "future success marked")
        eq(1, line.evidenceFreshnessCounts[PropagationFreshness.FUTURE_DATED],
            "future observation not fresh")
        eq(0, line.evidenceFreshnessCounts[PropagationFreshness.FRESH], "not misclassified")
    }

    private fun deterministicOrderAndDuplicateFailure() {
        val first = state(key = "PSK_REPORTER_PUBLIC_QUERY",
            role = PropagationRefreshSourceRole.PSK_REPORTER)
        val second = state()
        val store = InMemoryPropagationRefreshStateStore().also {
            it.save(first)
            it.save(second)
        }
        val result = PropagationSourceStatusService(
            store, InMemoryPropagationSnapshotStore()
        ).project(NOW)
        eq(listOf("NOAA_SWPC_PLANETARY_KP", "PSK_REPORTER_PUBLIC_QUERY"),
            result.sources.map { it.sourceKey }, "ordered independently of insertion")
        eq(0, result.failedCount, "no failed rows")
        val duplicates = object : PropagationRefreshStateStore {
            override fun state(sourceKey: String) = second
            override fun all() = listOf(second, second)
            override fun save(state: PropagationRefreshSourceState) = Unit
        }
        failure("duplicate source rows fail closed") {
            PropagationSourceStatusService(
                duplicates, InMemoryPropagationSnapshotStore()
            ).project(NOW)
        }
    }

    private fun persistedStateAndSnapshotSurviveRuntimeRecreation() = temporary { dir ->
        val stateDir = dir.resolve("state")
        val cacheDir = dir.resolve("cache")
        val stateStore = FilePropagationRefreshStateStore(stateDir)
        stateStore.save(state(failures = 2, message = "bounded provider 503",
            retryable = true, eligible = NOW + 3 * 60_000L))
        val cache = FilePropagationSnapshotStore(cacheDir)
        cache.save(snapshot(evidence("persistent-evidence")))
        val transport = PublicPropagationTransport { error("No provider request should occur") }
        val before = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig("N0PNG"), transport = transport,
            snapshotStore = cache, refreshStateStore = stateStore,
        )
        val first = before.sourceStatus(NOW)
        val firstKp = first.sources.single { it.sourceKey == "NOAA_SWPC_PLANETARY_KP" }
        eq(PropagationSourceRefreshReadiness.RETRY_BACKOFF, firstKp.readiness,
            "runtime status reports persisted backoff")
        eq(1, firstKp.evidenceCount, "runtime status sees cached evidence")
        val after = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig("N0PNG"), transport = transport,
            snapshotStore = FilePropagationSnapshotStore(cacheDir),
            refreshStateStore = FilePropagationRefreshStateStore(stateDir),
        )
        val second = after.sourceStatus(NOW)
        eq(first, second, "runtime/store restart does not change status projection")
        eq(5, second.sources.size, "all configured sources displayed")
        eq(1, second.failedCount, "only persisted failed source counted")
        eq(1, second.sourcesWithEvidenceCount, "no fabricated evidence for other sources")
    }

    private fun readOnlyOperationAndNoProviderInvocation() = temporary { dir ->
        val stateDir = dir.resolve("state")
        val cacheDir = dir.resolve("cache")
        val states = FilePropagationRefreshStateStore(stateDir)
        states.save(state())
        val cache = FilePropagationSnapshotStore(cacheDir)
        cache.save(snapshot(evidence("last-good")))
        val statePath = stateDir.resolve(FilePropagationRefreshStateStore.FILE_NAME)
        val cachePath = cacheDir.resolve(FilePropagationSnapshotStore.FILE_NAME)
        val beforeState = Files.readAllBytes(statePath)
        val beforeCache = Files.readAllBytes(cachePath)
        val service = PropagationSourceStatusService(states, cache)
        val one = service.project(NOW)
        val two = service.project(NOW)
        eq(one, two, "deterministic read-only projection")
        yes(beforeState.contentEquals(Files.readAllBytes(statePath)),
            "no state write while reading")
        yes(beforeCache.contentEquals(Files.readAllBytes(cachePath)),
            "no snapshot write while reading")
        val runtime = PropagationRuntimeFactory.create(
            PropagationRuntimeConfig("N0PNG"),
            transport = PublicPropagationTransport { error("No network during status") },
            snapshotStore = cache, refreshStateStore = states,
        )
        eq(one.sources.size + 4, runtime.sourceStatus(NOW).sources.size,
            "runtime builds only missing state records through existing factory")
        // The above initialization is ordinary runtime behavior, not status-service mutation.
        val afterFactoryBytes = Files.readAllBytes(statePath)
        runtime.sourceStatus(NOW + 1L)
        yes(afterFactoryBytes.contentEquals(Files.readAllBytes(statePath)),
            "runtime status does not advance scheduler")
    }
}
