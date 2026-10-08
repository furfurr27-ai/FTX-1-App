package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

object PropagationOperatingPictureTests {
    private var assertions = 0
    private fun checkThat(condition: Boolean, label: String) {
        assertions++
        check(condition) { label }
    }
    private fun <T> eq(expected: T, actual: T, label: String) {
        assertions++
        check(expected == actual) { "$label expected=$expected actual=$actual" }
    }
    private fun expectFailure(label: String, action: () -> Unit) {
        assertions++
        check(runCatching(action).isFailure) { "Expected failure: $label" }
    }

    private const val NOW = 2_100_000_000L
    private fun state(key: String = "NOAA_SWPC_PLANETARY_KP",
        role: PropagationRefreshSourceRole = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        failure: Boolean = false,
    ) = PropagationRefreshSourceState(
        sourceKey = key, role = role,
        lastAttemptUtcMillis = NOW - 100_000L,
        lastSuccessUtcMillis = if (failure) NOW - 200_000L else NOW - 100_000L,
        consecutiveFailures = if (failure) 2 else 0,
        nextEligibleRefreshUtcMillis = NOW + 180_000L,
        lastFailureMessage = if (failure) "synthetic timeout" else null,
        lastFailureRetryable = if (failure) true else null,
    )
    private fun evidence(
        id: String,
        sourceId: String = "NOAA_SWPC_PLANETARY_KP",
        observed: Long = NOW - 1_000L,
        retrieved: Long = NOW - 100L,
    ) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = sourceId,
            providerName = "synthetic offline provider",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "fixture",
            retrievedAtUtcMillis = retrieved,
        ),
        observedAtUtcMillis = observed,
        confidence = PropagationConfidence(
            value = 0.9,
            basis = PropagationConfidenceBasis.SYNTHETIC,
            explanation = "Synthetic offline only",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 2.5,
    )
    private fun snapshot(id: String = "snapshot-1",
        vararg observations: SolarGeomagneticObservation,
    ) = PropagationSnapshot(
        snapshotId = id,
        capturedAtUtcMillis = NOW + 100L,
        solarGeomagnetic = observations.toList(),
    )
    private fun states(vararg values: PropagationRefreshSourceState) =
        InMemoryPropagationRefreshStateStore().also { store ->
            values.forEach { store.save(it) }
        }
    private fun cache(vararg snapshots: PropagationSnapshot) =
        InMemoryPropagationSnapshotStore().also { store ->
            snapshots.forEach { store.save(it) }
        }

    @JvmStatic
    fun main(args: Array<String>) {
        emptyCacheStillShowsRefreshStatus()
        sharesOneSnapshotAndOneStateRead()
        bothViewsRetainExistingProjectionSemantics()
        stableFiltersDoNotHideSourceHealth()
        mixedStaleFutureAndFailureRemainIndependent()
        validatesClockAndSnapshotIntegrity()
        readOnlyFileStoreAndRuntimeRecreation()
        println("CP-0008M propagation operating-picture tests: PASS assertions=$assertions")
    }

    private fun emptyCacheStillShowsRefreshStatus() {
        val sourceStates = states(state(failure = true))
        val picture = PropagationOperatingPictureService(
            cache(), sourceStates
        ).read(NOW)
        eq(NOW, picture.queriedAtUtcMillis, "one explicit UTC")
        checkThat(!picture.hasSnapshot, "no data snapshot")
        eq(null, picture.workspace, "no fabricated workspace")
        eq(null, picture.sourceStatus.snapshotId, "no fabricated snapshot ID")
        eq(1, picture.sourceStatus.failedCount, "failure remains visible")
        eq(1, picture.sourceStatus.sources.size, "one configured source")
        eq(0, picture.sourceStatus.sources[0].evidenceCount, "no invented evidence")
        eq(PropagationSourceRefreshReadiness.RETRY_BACKOFF,
            picture.sourceStatus.sources[0].readiness, "backoff retained")
        val blank = PropagationOperatingPictureService(cache(), states()).read(NOW)
        eq(0, blank.sourceStatus.sources.size, "empty state handled")
        eq(null, blank.workspace, "empty workspace")
    }

    private fun sharesOneSnapshotAndOneStateRead() {
        val first = snapshot("stable", evidence("old"))
        val second = snapshot("changed", evidence("new"))
        val backing = cache(first)
        var snapshotReads = 0
        val snapshotStore = object : PropagationSnapshotStore by backing {
            override fun latest(): PropagationSnapshot? {
                snapshotReads++
                return if (snapshotReads == 1) first else second
            }
        }
        val stateBacking = states(state())
        var stateReads = 0
        val stateStore = object : PropagationRefreshStateStore by stateBacking {
            override fun all(): List<PropagationRefreshSourceState> {
                stateReads++
                return stateBacking.all()
            }
        }
        val picture = PropagationOperatingPictureService(
            snapshotStore, stateStore
        ).read(NOW)
        eq(1, snapshotReads, "latest() captured once")
        eq(1, stateReads, "states captured once")
        eq("stable", picture.workspace!!.status.snapshotId, "workspace uses first snapshot")
        eq("stable", picture.sourceStatus.snapshotId, "status uses same snapshot")
        eq(requireNotNull(picture.workspace).status.capturedAtUtcMillis,
            picture.sourceStatus.snapshotCapturedAtUtcMillis,
            "same capture timestamp")
        eq(requireNotNull(picture.workspace).status.snapshotIsFutureDated,
            picture.sourceStatus.snapshotIsFutureDated, "same future flag")
        eq(1, requireNotNull(picture.workspace).projectedEvidenceCount, "one cached observation")
        eq(1, picture.sourceStatus.sources.single().evidenceCount,
            "same evidence is source-matched")
    }

    private fun bothViewsRetainExistingProjectionSemantics() {
        val snapshot = snapshot("same",
            evidence("late", observed = NOW - 600L),
            evidence("early", observed = NOW - 60_000L))
        val snapshotStore = cache(snapshot)
        val sourceStore = states(state())
        val query = PropagationProjectionQuery(nowUtcMillis = NOW)
        val picture = PropagationOperatingPictureService(
            snapshotStore, sourceStore
        ).read(query)
        val independentWorkspace =
            PropagationWorkspaceProjectionService(snapshotStore).latest(query)
        val independentSources = PropagationSourceStatusService(
            sourceStore, snapshotStore
        ).project(NOW)
        eq(independentWorkspace, picture.workspace,
            "workspace projection exactly unchanged")
        eq(independentSources, picture.sourceStatus,
            "status projection exactly unchanged")
        val workspace = requireNotNull(picture.workspace)
        eq(listOf("late", "early"),
            workspace.solarGeomagnetic.map { it.metadata.evidenceId },
            "existing observed-time ordering")
        eq("same", picture.sourceStatus.snapshotId, "matching snapshot")
        eq(2, picture.sourceStatus.sources.single().evidenceCount,
            "full evidence reflected")
        eq(1, picture.sourceStatus.sourcesWithEvidenceCount, "one evidenced source")
    }

    private fun stableFiltersDoNotHideSourceHealth() {
        val picture = PropagationOperatingPictureService(
            cache(snapshot("filter", evidence("old"))),
            states(state(), state("NOAA_SWPC_F107_SUMMARY", PropagationRefreshSourceRole.NOAA_F107, true)),
        ).read(PropagationProjectionQuery(
            nowUtcMillis = NOW,
            filter = PropagationProjectionFilter(sourceIds = setOf("unmatched")),
        ))
        eq(0, picture.workspace!!.projectedEvidenceCount,
            "existing workspace filter hides unmatched evidence")
        eq(2, picture.sourceStatus.sources.size,
            "workspace filter does not hide source status")
        eq(1, picture.sourceStatus.failedCount,
            "failed source survives evidence filter")
        eq(1, picture.sourceStatus.sources.sumOf { it.evidenceCount },
            "unfiltered provenance counts remain visible")
    }

    private fun mixedStaleFutureAndFailureRemainIndependent() {
        val observations = arrayOf(
            evidence("fresh", observed = NOW - 1000L),
            evidence("stale", observed = NOW - 8 * 60 * 60_000L,
                retrieved = NOW - 8 * 60 * 60_000L),
            evidence("future", observed = NOW + 60_000L),
        )
        val picture = PropagationOperatingPictureService(
            cache(snapshot("future-capture", *observations)),
            states(state(failure = true)),
        ).read(NOW)
        val row = picture.sourceStatus.sources.single()
        eq(PropagationSourceLastAttempt.FAILED, row.lastAttempt, "last attempt failed")
        eq(PropagationSourceRefreshReadiness.RETRY_BACKOFF,
            row.readiness, "still in backoff")
        eq(1, row.evidenceFreshnessCounts[PropagationFreshness.FRESH], "fresh evidence")
        eq(1, row.evidenceFreshnessCounts[PropagationFreshness.STALE], "stale evidence")
        eq(1, row.evidenceFreshnessCounts[PropagationFreshness.FUTURE_DATED],
            "future evidence explicitly marked")
        checkThat(picture.workspace!!.status.containsStaleEvidence,
            "workspace retains stale evidence metadata")
        checkThat(requireNotNull(picture.workspace).status.containsFutureDatedEvidence,
            "workspace retains future evidence marker")
        eq("future-capture", requireNotNull(picture.workspace).status.snapshotId, "provenance")
        checkThat(picture.sourceStatus.snapshotIsFutureDated,
            "source status snapshot is future-dated")
    }

    private fun validatesClockAndSnapshotIntegrity() {
        val picture = PropagationOperatingPictureService(
            cache(), states()
        )
        expectFailure("negative UTC") { picture.read(-1L) }
        expectFailure("invalid filter") {
            PropagationProjectionFilter(minimumFrequencyHz = 15L,
                maximumFrequencyHz = 12L)
        }
        val goodStatus = PropagationSourceStatusService(
            states(), cache()
        ).project(NOW)
        expectFailure("mismatched UTC") {
            PropagationOperatingPicture(NOW + 1L, null, goodStatus)
        }
        val hasSnapshot = cache(snapshot("unique", evidence("one")))
        val snapshotPicture = PropagationOperatingPictureService(
            hasSnapshot, states(state())
        ).read(NOW)
        expectFailure("workspace/status snapshot mismatch") {
            PropagationOperatingPicture(NOW, snapshotPicture.workspace, goodStatus)
        }
        eq(NOW, snapshotPicture.queriedAtUtcMillis,
            "valid matching views still construct")
    }

    private fun readOnlyFileStoreAndRuntimeRecreation() {
        val root = Files.createTempDirectory("cp0008m-")
        try {
            val stateDir = root.resolve("state")
            val cacheDir = root.resolve("cache")
            val sourceStore = FilePropagationRefreshStateStore(stateDir)
            sourceStore.save(state(failure = true))
            val snapshotStore = FilePropagationSnapshotStore(cacheDir)
            snapshotStore.save(snapshot("disk", evidence("persisted")))
            val beforeState = Files.readAllBytes(stateDir.resolve(
                FilePropagationRefreshStateStore.FILE_NAME))
            val beforeCache = Files.readAllBytes(cacheDir.resolve(
                FilePropagationSnapshotStore.FILE_NAME))
            val noNetwork = PublicPropagationTransport {
                error("No provider fetch is permitted in read model")
            }
            val runtime = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noNetwork,
                snapshotStore = snapshotStore,
                refreshStateStore = sourceStore,
            )
            val first = runtime.operatingPicture(NOW)
            eq(5, first.sourceStatus.sources.size,
                "runtime includes five configured providers")
            eq("disk", first.workspace!!.status.snapshotId, "file cache on runtime")
            eq(1, first.sourceStatus.failedCount, "file-backed failure visible")
            eq(1, first.sourceStatus.sourcesWithEvidenceCount, "one cached provider")
            val persistedAfterRuntime = Files.readAllBytes(stateDir.resolve(
                FilePropagationRefreshStateStore.FILE_NAME))
            val restored = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noNetwork,
                snapshotStore = FilePropagationSnapshotStore(cacheDir),
                refreshStateStore = FilePropagationRefreshStateStore(stateDir),
            )
            eq(first, restored.operatingPicture(NOW),
                "same operating picture after complete runtime recreation")
            checkThat(persistedAfterRuntime.contentEquals(Files.readAllBytes(
                stateDir.resolve(FilePropagationRefreshStateStore.FILE_NAME))),
                "repeated operating-picture reads never change persisted refresh state")
            checkThat(beforeCache.contentEquals(Files.readAllBytes(
                cacheDir.resolve(FilePropagationSnapshotStore.FILE_NAME))),
                "operating-picture reads never write snapshots")
            checkThat(!beforeState.isEmpty(), "state existed before runtime initiation")
            val filtered = restored.operatingPicture(NOW,
                PropagationProjectionFilter(sourceIds = setOf("none")))
            eq(0, filtered.workspace!!.projectedEvidenceCount,
                "runtime convenience filter follows existing projection")
            eq(1, filtered.sourceStatus.sourcesWithEvidenceCount,
                "runtime filter cannot erase source evidence")
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
