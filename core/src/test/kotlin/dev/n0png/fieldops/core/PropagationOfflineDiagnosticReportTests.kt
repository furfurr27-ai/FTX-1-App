package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.util.Comparator

object PropagationOfflineDiagnosticReportTests {
    private var assertions = 0
    private fun assertThat(ok: Boolean, name: String) {
        assertions++
        check(ok) { name }
    }
    private fun <T> eq(expected: T, actual: T, name: String) {
        assertions++
        check(expected == actual) { "$name: expected=$expected actual=$actual" }
    }
    private fun invalid(name: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected rejection: $name" }
    }
    private const val NOW = 2_300_000_000L
    private fun state(
        key: String = "NOAA_SWPC_PLANETARY_KP",
        role: PropagationRefreshSourceRole = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        attempt: Long? = NOW - 1000,
        success: Long? = NOW - 1000,
        failure: Boolean = false,
    ) = PropagationRefreshSourceState(
        sourceKey = key, role = role,
        lastAttemptUtcMillis = attempt,
        lastSuccessUtcMillis = success,
        consecutiveFailures = if (failure) 1 else 0,
        nextEligibleRefreshUtcMillis = NOW + 60_000,
        lastFailureMessage = if (failure) "synthetic error" else null,
        lastFailureRetryable = if (failure) true else null,
    )
    private fun observation(
        id: String,
        retrieved: Long = NOW - 100,
        observed: Long = NOW - 500,
    ) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = "NOAA_SWPC_PLANETARY_KP",
            providerName = "offline fixture", sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "fixture-v1", retrievedAtUtcMillis = retrieved,
        ),
        observedAtUtcMillis = observed,
        confidence = PropagationConfidence(
            value = 0.8, basis = PropagationConfidenceBasis.SYNTHETIC,
            explanation = "Synthetic offline evidence only",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 4.0,
    )
    private fun cached(
        captured: Long = NOW,
        vararg values: SolarGeomagneticObservation,
    ) = PropagationSnapshot(
        snapshotId = "offline-snapshot",
        capturedAtUtcMillis = captured,
        solarGeomagnetic = values.toList(),
    )
    private fun sourceStore(vararg states: PropagationRefreshSourceState) =
        InMemoryPropagationRefreshStateStore().also { s -> states.forEach { s.save(it) } }
    private fun snapshotStore(vararg snapshots: PropagationSnapshot) =
        InMemoryPropagationSnapshotStore().also { s -> snapshots.forEach { s.save(it) } }
    private fun composition(
        store: PropagationSnapshotStore, state: PropagationRefreshStateStore,
        query: PropagationProjectionQuery = PropagationProjectionQuery(NOW),
    ): PropagationOperatingPictureWithDiagnostics =
        PropagationReadModelConsistencyService.withDiagnostics(
            PropagationOperatingPictureService(store, state).read(query)
        )
    @JvmStatic
    fun main(args: Array<String>) {
        emptySnapshotAndNoSources()
        completeProvenanceAndSourceTiming()
        filteredWorkspaceKeepsFullSourceAccounting()
        futureSnapshotAndUnknownDates()
        rejectsMismatchedCrossLayerInputs()
        exactlyOneStoreReadAndNoMutation()
        persistedRuntimeRecreationAndNoNetwork()
        println("CP-0008O offline diagnostic report tests: PASS assertions=$assertions")
    }
    private fun emptySnapshotAndNoSources() {
        val model = composition(snapshotStore(), sourceStore())
        val report = PropagationOfflineDiagnosticReportService.build(model)
        eq(1, report.schemaVersion, "explicit first schema version")
        eq(NOW, report.queriedAtUtcMillis, "explicit UTC")
        eq(null, report.snapshot.snapshotId, "no invented snapshot")
        eq(null, report.snapshot.capturedAtUtcMillis, "unknown captured")
        eq(null, report.snapshot.snapshotAgeMillis, "unknown age")
        eq(null, report.snapshot.offlineCacheAvailable, "cache support unknown without snapshot")
        eq(null, report.snapshot.sourceCount, "unknown cached source count")
        eq(null, report.workspace, "no fabricated sections")
        eq(0, report.sources.size, "no invented configured sources")
        eq(0, report.visibleEvidenceIndex.size, "no fabricated evidence index")
        eq(0, report.summary.visibleEvidenceCount, "zero visible")
        eq(0, report.summary.attributedCachedEvidenceCount, "zero source-attributed")
        assertThat(!report.summary.crossStoreAtomicityVerified, "no atomicity claim")
        eq(report, PropagationOfflineDiagnosticReportService.build(model),
            "repeated payload deterministic")
        val withState = PropagationOfflineDiagnosticReportService.build(
            composition(snapshotStore(), sourceStore(state(attempt = null, success = null)))
        )
        eq(1, withState.summary.sourceCount, "configured state is visible without snapshot")
        eq(0, withState.sources.single().status.evidenceCount, "no fabricated cached source evidence")
        eq(PropagationReadTimestampRelation.UNKNOWN,
            withState.sources.single().timing.lastSuccessToSnapshotRelation,
            "unavailable clock relation remains unknown")
    }
    private fun completeProvenanceAndSourceTiming() {
        val model = composition(
            snapshotStore(cached(NOW,
                observation("observed", observed = NOW - 1000, retrieved = NOW - 50),
                observation("old", observed = NOW - 8 * 60 * 60_000,
                    retrieved = NOW - 8 * 60 * 60_000))),
            sourceStore(
                state(success = NOW - 5000, attempt = NOW - 5000, failure = true),
                state("NOAA_SWPC_F107_SUMMARY",
                    PropagationRefreshSourceRole.NOAA_F107,
                    attempt = NOW + 1, success = NOW + 1),
            )
        )
        val report = PropagationOfflineDiagnosticReportService.build(model)
        eq("offline-snapshot", report.snapshot.snapshotId, "real snapshot provenance")
        eq(NOW, report.snapshot.capturedAtUtcMillis, "cached capture")
        eq(0L, report.snapshot.snapshotAgeMillis, "inclusive age zero")
        eq(2, report.summary.sourceCount, "both configured sources")
        eq(1, report.summary.failedSourceCount, "failed attempt preserved")
        eq(1, report.summary.sourcesWithCachedEvidenceCount, "one source has evidence")
        eq(2, report.summary.attributedCachedEvidenceCount, "both underlying cached items")
        eq(2, report.summary.visibleEvidenceCount, "both visible items")
        eq(1, report.summary.successesNewerThanSnapshotCount, "future source success after snapshot")
        eq(1, report.summary.futureAttemptSourceCount, "future attempt preserved")
        eq(listOf("NOAA_SWPC_F107_SUMMARY", "NOAA_SWPC_PLANETARY_KP"),
            report.sources.map { it.sourceKey }, "source order canonical")
        val kp = report.sources.single { it.sourceKey == "NOAA_SWPC_PLANETARY_KP" }
        eq(model.operatingPicture.sourceStatus.sources.single {
            it.sourceKey == kp.sourceKey
        }, kp.status, "original status object preserved")
        eq(model.diagnostics.sourceDiagnostics.single {
            it.sourceKey == kp.sourceKey
        }, kp.timing, "original timeline diagnostics preserved")
        eq(PropagationReadTimestampRelation.BEFORE,
            kp.timing.lastSuccessToSnapshotRelation, "signed time relation")
        eq(PropagationSourceLastAttempt.FAILED, kp.status.lastAttempt,
            "failed attempt not collapsed into readiness")
        eq(model.operatingPicture.workspace!!.solarGeomagnetic,
            report.workspace!!.solarGeomagnetic, "complete Kp payload preserved")
        eq(listOf("observed", "old"),
            report.visibleEvidenceIndex.map { it.evidenceId }, "indexed existing ordering")
        eq(listOf(PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC,
            PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC),
            report.visibleEvidenceIndex.map { it.kind }, "explicit evidence categories")
        eq("NOAA_SWPC_PLANETARY_KP", report.visibleEvidenceIndex[0].sourceId,
            "source attribution")
        eq(NOW - 50, report.visibleEvidenceIndex[0].retrievedAtUtcMillis,
            "original retrieval UTC")
        eq(NOW - 1000, report.visibleEvidenceIndex[0].observedAtUtcMillis,
            "original observed UTC")
        eq(PropagationFreshness.STALE, report.visibleEvidenceIndex[1].freshness,
            "source freshness from projection unchanged")
        eq(null, requireNotNull(report.workspace).selectedPathAssessment, "no invented selected path")
        assertThat(!report.summary.crossStoreAtomicityVerified, "unverified cross-store atomicity")
    }
    private fun filteredWorkspaceKeepsFullSourceAccounting() {
        val snap = snapshotStore(cached(NOW, observation("filtered")))
        val states = sourceStore(state(failure = true))
        val filtered = PropagationOfflineDiagnosticReportService.build(
            composition(snap, states, PropagationProjectionQuery(
                NOW, PropagationProjectionFilter(sourceIds = setOf("unmatched"))
            ))
        )
        eq(0, filtered.summary.visibleEvidenceCount, "filtered workspace empty")
        eq(0, filtered.visibleEvidenceIndex.size, "no index for hidden evidence")
        eq(1, filtered.summary.attributedCachedEvidenceCount,
            "cached source attribution not destroyed by filter")
        eq(1, filtered.sources.single().status.evidenceCount,
            "full cached evidence remains available in status")
        eq(1, filtered.summary.failedSourceCount, "filtered workspace retains failure")
        eq(0, filtered.workspace!!.solarGeomagnetic.size,
            "workspace faithfully filtered")
        val unfiltered = PropagationOfflineDiagnosticReportService.build(
            composition(snap, states)
        )
        eq(unfiltered.sources, filtered.sources,
            "source status/diagnostics unaffected by workspace filter")
    }
    private fun futureSnapshotAndUnknownDates() {
        val model = composition(
            snapshotStore(cached(NOW + 9000,
                observation("future", observed = NOW + 3000, retrieved = NOW + 3000))),
            sourceStore(state(attempt = null, success = null))
        )
        val report = PropagationOfflineDiagnosticReportService.build(model)
        assertThat(report.snapshot.isFutureDated, "future-dated cache explicit")
        eq(null, report.snapshot.snapshotAgeMillis, "no negative cache age")
        eq(1, report.summary.futureRetrievalSourceCount, "future retrieval indicator")
        eq(0, report.summary.futureAttemptSourceCount, "missing attempt not future")
        eq(PropagationReadTimestampRelation.UNKNOWN,
            report.sources.single().timing.newestRetrievalToLastSuccessRelation,
            "missing success represented unknown")
        eq(PropagationFreshness.FUTURE_DATED,
            report.visibleEvidenceIndex.single().freshness, "future observed evidence preserved")
        assertThat(report.visibleEvidenceIndex.single().retrievalIsFutureDated,
            "future retrieval marked")
    }
    private fun rejectsMismatchedCrossLayerInputs() {
        val picture = composition(snapshotStore(cached()), sourceStore(state()))
        val diag = picture.diagnostics
        val source = picture.operatingPicture.sourceStatus.sources.single()
        val row = diag.sourceDiagnostics.single()
        invalid("missing diagnostic key") {
            PropagationOfflineDiagnosticReportService.build(
                picture.copy(diagnostics = diag.copy(sourceDiagnostics = emptyList()))
            )
        }
        invalid("diagnostic role mismatch") {
            PropagationOfflineDiagnosticReportService.build(
                picture.copy(diagnostics = diag.copy(sourceDiagnostics =
                    listOf(row.copy(sourceRole = PropagationRefreshSourceRole.NOAA_F107))))
            )
        }
        invalid("diagnostic evidence mismatch") {
            PropagationOfflineDiagnosticReportService.build(
                picture.copy(diagnostics = diag.copy(sourceDiagnostics =
                    listOf(row.copy(evidenceCount = row.evidenceCount + 1,
                        freshEvidenceCount = row.freshEvidenceCount + 1))))
            )
        }
        val good = PropagationOfflineDiagnosticReportService.build(picture)
        invalid("wrong report schema version") { good.copy(schemaVersion = 7) }
        invalid("lossy evidence index") { good.copy(summary =
            good.summary.copy(visibleEvidenceCount = 5)) }
        invalid("invented cross-store guarantee") {
            good.copy(summary = good.summary.copy(crossStoreAtomicityVerified = true))
        }
        invalid("source identity cannot drift") {
            PropagationOfflineSourceReport("not-the-source", source, row)
        }
        eq(1, good.sources.size, "valid report still works")
    }
    private fun exactlyOneStoreReadAndNoMutation() {
        var latestCalls = 0
        var stateCalls = 0
        val snap = cached(NOW, observation("one"))
        val snapshotsBacking = snapshotStore(snap)
        val stateBacking = sourceStore(state())
        val snaps = object : PropagationSnapshotStore by snapshotsBacking {
            override fun latest(): PropagationSnapshot? {
                latestCalls++
                return snap
            }
        }
        val states = object : PropagationRefreshStateStore by stateBacking {
            override fun all(): List<PropagationRefreshSourceState> {
                stateCalls++
                return stateBacking.all()
            }
        }
        val model = composition(snaps, states)
        val original = model.operatingPicture
        val report = PropagationOfflineDiagnosticReportService.build(model)
        eq(1, latestCalls, "one store snapshot latest read, no extra report fetch")
        eq(1, stateCalls, "one source-state all read, no extra report fetch")
        eq(original.workspace!!.solarGeomagnetic,
            report.workspace!!.solarGeomagnetic, "workspace projections preserved")
        val repeated = PropagationOfflineDiagnosticReportService.build(model)
        eq(report, repeated, "idempotent read only transformation")
        eq(1, latestCalls, "no repeat snapshot read")
        eq(1, stateCalls, "no repeat source read")
    }
    private fun persistedRuntimeRecreationAndNoNetwork() {
        val root = Files.createTempDirectory("cp0008o-report-")
        try {
            val stateDir = root.resolve("states")
            val snapDir = root.resolve("snapshots")
            val states = FilePropagationRefreshStateStore(stateDir)
            states.save(state(failure = true))
            val snaps = FilePropagationSnapshotStore(snapDir)
            snaps.save(cached(NOW, observation("saved")))
            val noFetch = PublicPropagationTransport {
                error("CP-0008O must never contact public providers")
            }
            val runtime = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noFetch,
                snapshotStore = snaps,
                refreshStateStore = states,
            )
            val first = runtime.offlineDiagnosticReport(NOW)
            eq(5, first.summary.sourceCount,
                "runtime retains five configured provider source rows")
            eq(1, first.summary.attributedCachedEvidenceCount, "one cached item")
            eq(1, first.summary.failedSourceCount, "failed state persisted")
            val statesBefore = Files.readAllBytes(stateDir.resolve(
                FilePropagationRefreshStateStore.FILE_NAME))
            val snapBefore = Files.readAllBytes(snapDir.resolve(
                FilePropagationSnapshotStore.FILE_NAME))
            val runtime2 = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noFetch,
                snapshotStore = FilePropagationSnapshotStore(snapDir),
                refreshStateStore = FilePropagationRefreshStateStore(stateDir),
            )
            eq(first, runtime2.offlineDiagnosticReport(
                PropagationProjectionQuery(NOW)),
                "full report identical after runtime recreation")
            eq(first, PropagationOfflineDiagnosticReportService.build(
                runtime2.operatingPictureWithDiagnostics(NOW)),
                "report derives exactly from existing model")
            assertThat(statesBefore.contentEquals(Files.readAllBytes(
                stateDir.resolve(FilePropagationRefreshStateStore.FILE_NAME))),
                "file-backed source state unchanged")
            assertThat(snapBefore.contentEquals(Files.readAllBytes(
                snapDir.resolve(FilePropagationSnapshotStore.FILE_NAME))),
                "file-backed snapshot unchanged")
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
