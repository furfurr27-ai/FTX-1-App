package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.util.Comparator

object PropagationReadModelConsistencyTests {
    private var assertions = 0
    private fun checkThat(ok: Boolean, label: String) {
        assertions++
        check(ok) { label }
    }
    private fun <T> eq(expected: T, actual: T, label: String) {
        assertions++
        check(expected == actual) { "$label expected=$expected actual=$actual" }
    }
    private fun expectFailure(label: String, task: () -> Unit) {
        assertions++
        check(runCatching(task).isFailure) { "Expected rejection: $label" }
    }

    private const val NOW = 2_200_000_000L
    private fun state(
        key: String = "NOAA_SWPC_PLANETARY_KP",
        role: PropagationRefreshSourceRole = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        attempt: Long? = NOW - 60_000,
        success: Long? = NOW - 60_000,
        failure: Boolean = false,
    ) = PropagationRefreshSourceState(
        sourceKey = key, role = role,
        lastAttemptUtcMillis = attempt,
        lastSuccessUtcMillis = success,
        consecutiveFailures = if (failure) 2 else 0,
        nextEligibleRefreshUtcMillis = NOW + 60_000,
        lastFailureMessage = if (failure) "synthetic timeout" else null,
        lastFailureRetryable = if (failure) true else null,
    )
    private fun evidence(
        id: String,
        provider: String = "NOAA_SWPC_PLANETARY_KP",
        observed: Long = NOW - 1000,
        retrieved: Long = NOW - 1000,
    ) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = provider, providerName = "synthetic fixture",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "ci", retrievedAtUtcMillis = retrieved,
        ),
        observedAtUtcMillis = observed,
        confidence = PropagationConfidence(
            value = 0.8, basis = PropagationConfidenceBasis.SYNTHETIC,
            explanation = "offline test",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 3.0,
    )
    private fun snapshot(
        id: String = "snap-ci", captured: Long = NOW,
        vararg items: SolarGeomagneticObservation,
    ) = PropagationSnapshot(
        snapshotId = id, capturedAtUtcMillis = captured,
        solarGeomagnetic = items.toList(),
    )
    private fun sources(vararg items: PropagationRefreshSourceState) =
        InMemoryPropagationRefreshStateStore().also { store ->
            items.forEach { store.save(it) }
        }
    private fun cache(vararg items: PropagationSnapshot) =
        InMemoryPropagationSnapshotStore().also { store ->
            items.forEach { store.save(it) }
        }

    @JvmStatic
    fun main(args: Array<String>) {
        noSnapshotAndNeverAttempted()
        allTimestampOrderingRelations()
        freshnessAndFutureTimestamps()
        filtersCannotAlterDiagnostics()
        duplicateRowsAndViewMismatchReject()
        singleStoreReadAndPureRepeatedEvaluation()
        persistentRuntimeRecreationAndNoProviderAccess()
        println("CP-0008N consistency diagnostics tests: PASS assertions=$assertions")
    }

    private fun noSnapshotAndNeverAttempted() {
        val stores = sources(state(attempt = null, success = null))
        val picture = PropagationOperatingPictureService(cache(), stores).read(NOW)
        val d = PropagationReadModelConsistencyService.diagnose(picture)
        eq(NOW, d.queriedAtUtcMillis, "explicit UTC preserved")
        eq(null, d.snapshotId, "missing snapshot has no id")
        eq(null, d.snapshotAgeMillis, "missing snapshot age unknown")
        eq(null, d.snapshotCapturedAtUtcMillis, "no fake capture")
        eq(null, d.newestSourceSuccessUtcMillis, "no fake source success")
        eq(0, d.sourceSuccessAfterSnapshotCount, "no false skew")
        eq(0, d.evidenceRetrievedAfterLastSuccessCount, "no false retrieval skew")
        eq(1, d.sourceDiagnostics.size, "sources visible without snapshot")
        val row = d.sourceDiagnostics.single()
        eq(PropagationReadTimestampRelation.UNKNOWN,
            row.lastSuccessToSnapshotRelation, "unknown cannot become success")
        eq(PropagationReadTimestampRelation.UNKNOWN,
            row.newestRetrievalToLastSuccessRelation, "no retrieval comparison")
        eq(null, row.lastSuccessMinusSnapshotCaptureMillis, "null delta")
        eq(0, row.evidenceCount, "no fabricated evidence")
        checkThat(!d.crossStoreAtomicityVerified, "never claim atomicity")
        eq(PropagationReadModelConsistencyService.withDiagnostics(picture).operatingPicture,
            picture, "wrapper contains original exact picture")
        val empty = PropagationReadModelConsistencyService.diagnose(
            PropagationOperatingPictureService(cache(), sources()).read(NOW)
        )
        eq(0, empty.sourceDiagnostics.size, "empty state rows")
    }

    private fun allTimestampOrderingRelations() {
        val cached = snapshot("ordered", NOW,
            evidence("obs", retrieved = NOW - 6000))
        val stateRows = sources(
            state(success = NOW - 10_000, attempt = NOW - 5_000),
            state(key = "NOAA_SWPC_F107_SUMMARY",
                role = PropagationRefreshSourceRole.NOAA_F107,
                success = NOW + 100, attempt = NOW + 100),
            state(key = "PSK_REPORTER_PUBLIC_QUERY",
                role = PropagationRefreshSourceRole.PSK_REPORTER,
                success = NOW, attempt = NOW),
        )
        val picture = PropagationOperatingPictureService(
            cache(cached), stateRows
        ).read(NOW)
        val d = PropagationReadModelConsistencyService.diagnose(picture)
        val rows = d.sourceDiagnostics.associateBy { it.sourceKey }
        val kp = rows.getValue("NOAA_SWPC_PLANETARY_KP")
        eq(PropagationReadTimestampRelation.BEFORE,
            kp.lastSuccessToSnapshotRelation, "old success before cache")
        eq(PropagationReadTimestampRelation.AFTER,
            kp.newestRetrievalToLastSuccessRelation, "retrieval newer than success")
        eq(-10_000L, kp.lastSuccessMinusSnapshotCaptureMillis, "signed cache skew")
        eq(4_000L, kp.newestRetrievalMinusLastSuccessMillis, "signed retrieval skew")
        val f107 = rows.getValue("NOAA_SWPC_F107_SUMMARY")
        eq(PropagationReadTimestampRelation.AFTER, f107.lastSuccessToSnapshotRelation,
            "source success newer than cached snapshot")
        eq(null, f107.newestRetrievalMinusLastSuccessMillis,
            "no evidence cannot create retrieval delta")
        eq(PropagationReadTimestampRelation.UNKNOWN,
            f107.newestRetrievalToLastSuccessRelation, "missing evidence unknown")
        val psk = rows.getValue("PSK_REPORTER_PUBLIC_QUERY")
        eq(PropagationReadTimestampRelation.EQUAL, psk.lastSuccessToSnapshotRelation,
            "equal timestamps stay equal")
        eq(0L, psk.lastSuccessMinusSnapshotCaptureMillis, "zero skew")
        eq(1, d.sourceSuccessAfterSnapshotCount, "one source newer than cache")
        eq(1, d.evidenceRetrievedAfterLastSuccessCount, "one retrieval newer than success")
        eq(NOW + 100, d.newestSourceSuccessUtcMillis, "newest success explicit")
        eq(1, d.futureDatedSourceAttemptCount, "future attempt count")
        eq(listOf("NOAA_SWPC_F107_SUMMARY", "NOAA_SWPC_PLANETARY_KP",
            "PSK_REPORTER_PUBLIC_QUERY"), d.sourceDiagnostics.map { it.sourceKey },
            "stable source ordering")
        checkThat(!d.crossStoreAtomicityVerified, "timing hints do not grant atomicity")
    }

    private fun freshnessAndFutureTimestamps() {
        val input = snapshot("future", NOW + 60_000,
            evidence("fresh", observed = NOW - 1000, retrieved = NOW - 100),
            evidence("stale", observed = NOW - 8 * 60 * 60_000,
                retrieved = NOW - 8 * 60 * 60_000),
            evidence("future-observed", observed = NOW + 9000,
                retrieved = NOW + 3000))
        val state = state(success = NOW - 20_000)
        val picture = PropagationOperatingPictureService(
            cache(input), sources(state)
        ).read(NOW)
        val d = PropagationReadModelConsistencyService.diagnose(picture)
        checkThat(d.snapshotCaptureIsFutureDated, "future snapshot marked")
        eq(null, d.snapshotAgeMillis, "no negative snapshot age")
        val r = d.sourceDiagnostics.single()
        eq(3, r.evidenceCount, "three source observations")
        eq(1, r.freshEvidenceCount, "fresh count preserved")
        eq(1, r.staleEvidenceCount, "stale count preserved")
        eq(1, r.futureDatedEvidenceCount, "future count preserved")
        eq(0, r.agingEvidenceCount, "no aging evidence")
        checkThat(r.newestRetrievalIsFutureDated, "future retrieval marked")
        eq(1, d.futureDatedRetrievalSourceCount, "future retrieval count")
        eq(0, d.futureDatedSourceAttemptCount, "past attempt not future")
        eq(PropagationReadTimestampRelation.BEFORE,
            r.lastSuccessToSnapshotRelation, "future capture later than source success")
        val again = PropagationReadModelConsistencyService.diagnose(picture)
        eq(d, again, "deterministic projection")
        eq(PropagationReadModelConsistencyService.withDiagnostics(picture).operatingPicture,
            picture, "original picture unchanged")
    }

    private fun filtersCannotAlterDiagnostics() {
        val cache = cache(snapshot("filter", NOW,
            evidence("first", observed = NOW - 500),
            evidence("second", observed = NOW - 10_000)))
        val source = sources(state())
        val runtime = PropagationOperatingPictureService(cache, source)
        val unrestricted = runtime.read(NOW)
        val filtered = runtime.read(
            PropagationProjectionQuery(NOW,
                PropagationProjectionFilter(sourceIds = setOf("not-found")))
        )
        eq(2, requireNotNull(unrestricted.workspace).projectedEvidenceCount,
            "unfiltered workspace evidence")
        eq(0, requireNotNull(filtered.workspace).projectedEvidenceCount,
            "filter affects workspace")
        val left = PropagationReadModelConsistencyService.diagnose(unrestricted)
        val right = PropagationReadModelConsistencyService.diagnose(filtered)
        eq(left, right, "diagnostics derived from full status, not filtered workspace")
        eq(2, right.sourceDiagnostics.single().evidenceCount,
            "source evidence count unfiltered")
        eq(unrestricted.sourceStatus, filtered.sourceStatus, "source status untouched")
    }

    private fun duplicateRowsAndViewMismatchReject() {
        val pic = PropagationOperatingPictureService(
            cache(snapshot()), sources(state())
        ).read(NOW)
        val good = PropagationReadModelConsistencyService.diagnose(pic)
        val row = good.sourceDiagnostics.single()
        expectFailure("duplicate diagnostics") {
            good.copy(sourceDiagnostics = listOf(row, row))
        }
        expectFailure("incorrect skew count") {
            good.copy(sourceSuccessAfterSnapshotCount = 77)
        }
        expectFailure("bad freshness total") {
            row.copy(evidenceCount = row.evidenceCount + 1)
        }
        expectFailure("cross-view UTC mismatch") {
            PropagationOperatingPictureWithDiagnostics(
                pic, good.copy(queriedAtUtcMillis = NOW + 1))
        }
        expectFailure("cross-view snapshot mismatch") {
            PropagationOperatingPictureWithDiagnostics(
                pic, good.copy(snapshotId = "other"))
        }
        expectFailure("negative query") {
            PropagationOperatingPictureService(cache(), sources()).read(-1)
        }
        eq(0, good.sourceDiagnostics.single().evidenceCount, "no evidence in blank snapshot")
    }

    private fun singleStoreReadAndPureRepeatedEvaluation() {
        val actualSnap = snapshot("one", NOW, evidence("signal"))
        val secondSnap = snapshot("two", NOW, evidence("different"))
        val snapBacking = cache(actualSnap)
        var reads = 0
        val snapshotStore = object : PropagationSnapshotStore by snapBacking {
            override fun latest(): PropagationSnapshot? {
                reads++
                return if (reads == 1) actualSnap else secondSnap
            }
        }
        var stateReads = 0
        val stateBacking = sources(state())
        val stateStore = object : PropagationRefreshStateStore by stateBacking {
            override fun all(): List<PropagationRefreshSourceState> {
                stateReads++
                return stateBacking.all()
            }
        }
        val picture = PropagationOperatingPictureService(
            snapshotStore, stateStore
        ).read(NOW)
        val view = PropagationReadModelConsistencyService.withDiagnostics(picture)
        eq(1, reads, "single snapshot read including diagnostics")
        eq(1, stateReads, "single state read including diagnostics")
        eq("one", view.diagnostics.snapshotId, "same cached snapshot identity")
        eq(view.operatingPicture, picture, "same instance data, no second read")
        eq(view.diagnostics,
            PropagationReadModelConsistencyService.diagnose(picture),
            "pure repeated diagnosis")
        eq(1, reads, "diagnosis never fetches snapshot")
        eq(1, stateReads, "diagnosis never fetches states")
    }

    private fun persistentRuntimeRecreationAndNoProviderAccess() {
        val root = Files.createTempDirectory("cp0008n-")
        try {
            val stateDir = root.resolve("states")
            val cacheDir = root.resolve("snapshots")
            val diskStates = FilePropagationRefreshStateStore(stateDir)
            diskStates.save(state(success = NOW - 60_000, failure = true))
            val snapshots = FilePropagationSnapshotStore(cacheDir)
            snapshots.save(snapshot("persisted", NOW, evidence("persistent")))
            val transport = PublicPropagationTransport { error("Provider must not be called") }
            val runtime1 = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = transport, snapshotStore = snapshots,
                refreshStateStore = diskStates)
            val first = runtime1.operatingPictureWithDiagnostics(NOW)
            eq(runtime1.operatingPicture(NOW), first.operatingPicture,
                "original operating picture unchanged")
            eq(5, first.diagnostics.sourceDiagnostics.size,
                "five configured runtime sources")
            eq(1, first.operatingPicture.sourceStatus.failedCount,
                "failure carried to operating picture")
            eq(1, first.diagnostics.sourceDiagnostics.sumOf { it.evidenceCount },
                "one persisted observation")
            val fileBefore = Files.readAllBytes(stateDir.resolve(
                FilePropagationRefreshStateStore.FILE_NAME))
            val cacheBefore = Files.readAllBytes(cacheDir.resolve(
                FilePropagationSnapshotStore.FILE_NAME))
            val runtime2 = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = transport,
                snapshotStore = FilePropagationSnapshotStore(cacheDir),
                refreshStateStore = FilePropagationRefreshStateStore(stateDir))
            eq(first, runtime2.operatingPictureWithDiagnostics(NOW),
                "file-backed restart preserves diagnostics")
            eq(first, runtime2.operatingPictureWithDiagnostics(
                PropagationProjectionQuery(nowUtcMillis = NOW)),
                "query and convenience overload same result")
            checkThat(fileBefore.contentEquals(Files.readAllBytes(
                stateDir.resolve(FilePropagationRefreshStateStore.FILE_NAME))),
                "diagnostics do not modify persisted state")
            checkThat(cacheBefore.contentEquals(Files.readAllBytes(
                cacheDir.resolve(FilePropagationSnapshotStore.FILE_NAME))),
                "diagnostics do not modify snapshot")
            checkThat(!first.diagnostics.crossStoreAtomicityVerified,
                "no transactional claim")
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
