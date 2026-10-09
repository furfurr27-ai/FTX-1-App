package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Comparator

object PropagationOfflineReportSerializationTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0
    private fun checkThat(ok: Boolean, name: String) { assertions++; check(ok) { name } }
    private fun <T> eq(a: T, b: T, name: String) {
        assertions++
        check(a == b) { "$name expected=$a actual=$b" }
    }
    private fun reject(name: String, task: () -> Unit) {
        assertions++
        check(runCatching(task).isFailure) { "Expected rejection: $name" }
    }
    private fun state(
        attempt: Long? = NOW - 5000,
        success: Long? = NOW - 5000,
        failure: Boolean = false,
        message: String? = if (failure) "synthetic-error" else null,
    ) = PropagationRefreshSourceState(
        sourceKey = "NOAA_SWPC_PLANETARY_KP",
        role = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
        lastAttemptUtcMillis = attempt, lastSuccessUtcMillis = success,
        consecutiveFailures = if (failure) 1 else 0,
        nextEligibleRefreshUtcMillis = NOW + 1000,
        lastFailureMessage = message,
        lastFailureRetryable = if (failure) true else null,
    )
    private fun observation(
        id: String, observed: Long = NOW - 1000,
        retrieved: Long = NOW - 200,
        kp: Double = 4.25,
    ) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = "NOAA_SWPC_PLANETARY_KP",
            providerName = "offline fixture",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "test-v1",
            retrievedAtUtcMillis = retrieved,
        ),
        observedAtUtcMillis = observed,
        confidence = PropagationConfidence(
            value = 0.8, basis = PropagationConfidenceBasis.SYNTHETIC,
            explanation = "pure fixture",
        ),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = kp,
    )
    private fun build(
        snapshot: PropagationSnapshot?,
        source: PropagationRefreshSourceState? = null,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
    ): PropagationOfflineDiagnosticReport {
        val snapStore = InMemoryPropagationSnapshotStore()
        if (snapshot != null) snapStore.save(snapshot)
        val states = InMemoryPropagationRefreshStateStore()
        if (source != null) states.save(source)
        return PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(
                PropagationOperatingPictureService(snapStore, states)
                    .read(PropagationProjectionQuery(NOW, filter))
            )
        )
    }
    private fun snapshot(capture: Long = NOW, vararg items: SolarGeomagneticObservation) =
        PropagationSnapshot(
            snapshotId = "cache-fixture", capturedAtUtcMillis = capture,
            solarGeomagnetic = items.toList()
        )
    @JvmStatic
    fun main(args: Array<String>) {
        missingSnapshotStableGoldenShape()
        fullNestedProvenanceAndEscapes()
        filteredWorkspaceAndFutureDates()
        validationRejectsMutationAndInvalidPayload()
        writeFreeRuntimeRecreationAndHash()
        println("CP-0008P offline serialization tests: PASS assertions=$assertions")
    }
    private fun missingSnapshotStableGoldenShape() {
        val report = build(null)
        val ser = PropagationOfflineReportSerialization.serialize(report)
        eq(1, ser.wireVersion, "wire version")
        eq(PropagationOfflineReportSerialization.CONTENT_TYPE,
            ser.contentType, "media type is pinned")
        checkThat(ser.json.startsWith(
            "{\"format\":\"fieldops.propagation.offline-diagnostic\",\"payload\":{"),
            "stable envelope ordering")
        checkThat(ser.json.endsWith("},\"wireVersion\":1}"),
            "stable trailing schema version")
        checkThat(ser.json.contains("\"snapshotId\":null"),
            "missing snapshot stays null")
        checkThat(ser.json.contains("\"workspace\":null"),
            "missing workspace stays null")
        checkThat(ser.json.contains("\"crossStoreAtomicityVerified\":false"),
            "no transactional guarantee")
        checkThat(!ser.json.contains("NaN"), "no invented numeric marker")
        eq(ser.json.toByteArray(StandardCharsets.UTF_8).size,
            ser.utf8ByteCount, "exact byte count")
        eq(64, ser.sha256Hex.length, "256-bit hex digest")
        eq(ser, PropagationOfflineReportSerialization.serialize(report),
            "repeat deterministic empty report")
        checkThat(PropagationOfflineReportSerialization.verify(report, ser),
            "canonical artifact verification")
        eq("d333296023c2c9e640fe35c349a04ea361d8760d593ccf102ac32801c14ea384",
            ser.sha256Hex, "pinned canonical JSON V1 golden output fingerprint")
        println("CP-0008P golden empty-report SHA256: " + ser.sha256Hex)
    }
    private fun fullNestedProvenanceAndEscapes() {
        val strange = "message \"quoted\" \\ path\ncontrol\u0001 ü 🌍"
        val report = build(
            snapshot(NOW,
                observation("signal-1"),
                observation("signal-2", observed = NOW - 8 * 60 * 60_000,
                    retrieved = NOW - 8 * 60 * 60_000)),
            state(failure = true, message = strange),
        )
        val result = PropagationOfflineReportSerialization.serialize(report)
        val json = result.json
        eq(2, report.visibleEvidenceIndex.size, "two original observations")
        checkThat(json.contains("\"evidenceId\":\"signal-1\""), "first evidence provenance")
        checkThat(json.contains("\"evidenceId\":\"signal-2\""), "second evidence provenance")
        checkThat(json.indexOf("\"evidenceId\":\"signal-1\"") <
            json.indexOf("\"evidenceId\":\"signal-2\""),
            "semantically ordered evidence list retained")
        checkThat(json.contains("\"planetaryKp\":4.25"), "original metric preserved")
        checkThat(json.contains("\"observedAtUtcMillis\":"),
            "observed provenance in report")
        checkThat(json.contains("\"retrievedAtUtcMillis\":"),
            "retrieval provenance in report")
        checkThat(json.contains("\"lastFailureMessage\":\"message \\\"quoted\\\" \\\\ path\\ncontrol\\u0001 ü 🌍\""),
            "all JSON escapes and valid unicode carried")
        checkThat(json.contains("\"freshness\":\"STALE\""), "source freshness carried")
        checkThat(json.contains("\"readiness\":\"RETRY_BACKOFF\""), "backoff carried")
        checkThat(json.contains("\"lastSuccessToSnapshotRelation\":\"BEFORE\""),
            "CP-0008N timing diagnostic carried")
        checkThat(!json.contains('\u0001'), "control byte JSON escaped")
        eq(json.toByteArray(StandardCharsets.UTF_8).size,
            result.utf8ByteCount, "non-ASCII UTF-8 length")
        checkThat(PropagationOfflineReportSerialization.verify(report, result),
            "non-ASCII report validates")
    }
    private fun filteredWorkspaceAndFutureDates() {
        val s = snapshot(NOW + 2_000,
            observation("future", observed = NOW + 200, retrieved = NOW + 400))
        val original = build(s, state(success = null, attempt = null))
        val visible = PropagationOfflineReportSerialization.serialize(original)
        checkThat(visible.json.contains("\"snapshotAgeMillis\":null"),
            "future cache age never negative")
        checkThat(visible.json.contains("\"isFutureDated\":true"),
            "future cache marker")
        checkThat(visible.json.contains("\"retrievalIsFutureDated\":true"),
            "future retrieval marker")
        checkThat(visible.json.contains("\"lastSuccessToSnapshotRelation\":\"UNKNOWN\""),
            "missing source success remains unknown")
        val hidden = build(s, state(success = null, attempt = null),
            PropagationProjectionFilter(sourceIds = setOf("source-not-matched")))
        val serialized = PropagationOfflineReportSerialization.serialize(hidden)
        eq(1, hidden.summary.attributedCachedEvidenceCount,
            "full cached evidence remains")
        eq(0, hidden.summary.visibleEvidenceCount,
            "filtered workspace omits evidence")
        checkThat(serialized.json.contains("\"attributedCachedEvidenceCount\":1"),
            "unfiltered cached source counts serialized")
        checkThat(serialized.json.contains("\"visibleEvidenceCount\":0"),
            "filtered visible count serialized")
        checkThat(serialized.json.contains("\"visibleEvidenceIndex\":[]"),
            "hidden evidence never leaks into visible index")
    }
    private fun validationRejectsMutationAndInvalidPayload() {
        val good = build(snapshot(NOW, observation("valid")), state())
        val serialized = PropagationOfflineReportSerialization.serialize(good)
        checkThat(!PropagationOfflineReportSerialization.verify(
            good, serialized.copy(json = serialized.json + "garbage")),
            "tampering with JSON fails")
        checkThat(!PropagationOfflineReportSerialization.verify(
            good, serialized.copy(sha256Hex = "0".repeat(64))),
            "tampering with digest fails")
        checkThat(!PropagationOfflineReportSerialization.verify(
            good, serialized.copy(wireVersion = 2)),
            "wrong version fails")
        reject("index provenance corruption") {
            PropagationOfflineReportSerialization.serialize(good.copy(
                visibleEvidenceIndex = good.visibleEvidenceIndex.map {
                    it.copy(sourceId = "invented")
                }
            ))
        }
        reject("source count corruption") {
            PropagationOfflineReportSerialization.serialize(good.copy(
                summary = good.summary.copy(failedSourceCount = 1)
            ))
        }
        reject("future flag corruption") {
            PropagationOfflineReportSerialization.serialize(good.copy(
                snapshot = good.snapshot.copy(isFutureDated = true)
            ))
        }
        reject("non-finite nested metric") {
            PropagationOfflineReportSerialization.serialize(good.copy(
                workspace = requireNotNull(good.workspace).copy(
                    solarGeomagnetic = requireNotNull(good.workspace)
                        .solarGeomagnetic.map { it.copy(planetaryKp = Double.NaN) }
                )
            ))
        }
        reject("unpaired Unicode surrogate") {
            val row = good.sources.single()
            PropagationOfflineReportSerialization.serialize(good.copy(
                sources = listOf(row.copy(status =
                    row.status.copy(lastFailureMessage = "\uD800")))
            ))
        }
    }
    private fun writeFreeRuntimeRecreationAndHash() {
        val root = Files.createTempDirectory("cp0008p-")
        try {
            val sourceDir = root.resolve("states")
            val cacheDir = root.resolve("snapshots")
            val states = FilePropagationRefreshStateStore(sourceDir)
            states.save(state(failure = true))
            val cache = FilePropagationSnapshotStore(cacheDir)
            cache.save(snapshot(NOW, observation("persisted")))
            val noNetwork = PublicPropagationTransport {
                error("No providers may be contacted by serialization")
            }
            val runtime = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noNetwork,
                snapshotStore = cache,
                refreshStateStore = states,
            )
            val report = runtime.offlineDiagnosticReport(NOW)
            val a = runtime.serializedOfflineDiagnosticReport(NOW)
            eq(PropagationOfflineReportSerialization.serialize(report), a,
                "runtime convenience reports exact existing CP-0008O data")
            val stateBefore = Files.readAllBytes(sourceDir.resolve(
                FilePropagationRefreshStateStore.FILE_NAME))
            val cacheBefore = Files.readAllBytes(cacheDir.resolve(
                FilePropagationSnapshotStore.FILE_NAME))
            val again = PropagationRuntimeFactory.create(
                config = PropagationRuntimeConfig("N0PNG"),
                transport = noNetwork,
                snapshotStore = FilePropagationSnapshotStore(cacheDir),
                refreshStateStore = FilePropagationRefreshStateStore(sourceDir),
            ).serializedOfflineDiagnosticReport(PropagationProjectionQuery(NOW))
            eq(a, again, "runtime recreated file-backed deterministic canonical bytes")
            eq(a.sha256Hex, MessageDigest.getInstance("SHA-256")
                .digest(a.json.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
                "independent SHA-256 integrity digest")
            checkThat(stateBefore.contentEquals(Files.readAllBytes(
                sourceDir.resolve(FilePropagationRefreshStateStore.FILE_NAME))),
                "no source-state persisted mutation")
            checkThat(cacheBefore.contentEquals(Files.readAllBytes(
                cacheDir.resolve(FilePropagationSnapshotStore.FILE_NAME))),
                "no snapshot persisted mutation")
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
