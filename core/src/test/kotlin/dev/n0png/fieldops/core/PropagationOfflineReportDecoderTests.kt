package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import dev.n0png.fieldops.core.map.GeoCoordinate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Entirely synthetic, offline CP-0008Q decoding and V1 regression tests. */
object PropagationOfflineReportDecoderTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0
    private fun yes(value: Boolean, label: String) {
        assertions++
        check(value) { label }
    }
    private fun equal(expected: Any?, actual: Any?, label: String) {
        assertions++
        check(expected == actual) { label + " expected=" + expected + " actual=" + actual }
    }
    private fun rejected(label: String, task: () -> Unit) {
        assertions++
        check(runCatching(task).isFailure) { "Accepted invalid " + label }
    }
    private fun sourceRef(id: String, retrieval: Long = NOW - 100) =
        PropagationSourceRef(
            sourceId = id, providerName = "synthetic \"source\" 🌍",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "v1", retrievedAtUtcMillis = retrieval
        )
    private fun sourceState(
        id: String,
        role: PropagationRefreshSourceRole,
        failure: String? = null,
    ) = PropagationRefreshSourceState(
        sourceKey = id, role = role,
        lastAttemptUtcMillis = NOW - 50,
        lastSuccessUtcMillis = NOW - 50,
        consecutiveFailures = if (failure == null) 0 else 1,
        nextEligibleRefreshUtcMillis = NOW + 100,
        lastFailureMessage = failure,
        lastFailureRetryable = if (failure == null) null else true
    )
    private fun confidence() = PropagationConfidence(
        value = 0.95, basis = PropagationConfidenceBasis.SYNTHETIC,
        explanation = "synthetic source"
    )
    private fun solar(id: String, retrieval: Long = NOW - 100) =
        SolarGeomagneticObservation(
            evidenceId = id, source = sourceRef("NOAA_SWPC_PLANETARY_KP", retrieval),
            observedAtUtcMillis = NOW - 150, confidence = confidence(),
            quality = setOf(PropagationDataQuality.SYNTHETIC),
            planetaryKp = 3.75
        )
    private fun iono() = IonosphericMapProduct(
        evidenceId = "vtec-grid",
        source = sourceRef("NOAA_SWPC_GLOTEC_VTEC"),
        observedAtUtcMillis = NOW - 200,
        confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        coverage = GlobalPropagationCoverage,
        metric = IonosphericMetric.VTEC_TECU,
        samples = listOf(IonosphericSample(
            position = PropagationPosition(
                coordinate = GeoCoordinate(longitude = 8.2, latitude = 50.0),
                method = PropagationLocationMethod.EXPLICIT_COORDINATE
            ),
            value = 17.5
        ))
    )
    private fun place(longitude: Double, latitude: Double) = PropagationPosition(
        coordinate = GeoCoordinate(longitude = longitude, latitude = latitude),
        method = PropagationLocationMethod.EXPLICIT_COORDINATE
    )
    private fun heard() = HeardPathObservation(
        evidenceId = "heard-rf",
        source = sourceRef("PSK_REPORTER_PUBLIC_QUERY"),
        observedAtUtcMillis = NOW - 180, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        transmitter = PropagationEndpoint(place(8.2, 50.0), callsign = "N0PNG"),
        receiver = PropagationEndpoint(place(7.7, 49.9), callsign = "DL1TEST"),
        frequencyHz = 7_075_000L, band = "40m", mode = "FT8",
        snrDb = -13.5, reportCount = 3
    )
    private fun modeled() = ModeledPathEstimate(
        evidenceId = "path-model",
        source = sourceRef("SYNTHETIC_MODEL"),
        observedAtUtcMillis = NOW - 180, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        origin = place(8.2, 50.0), destination = place(7.7, 49.9),
        maximumUsableFrequencyHz = 12_000_000L,
        lowestUsableFrequencyHz = 4_000_000L,
        modelInputsSummary = "offline fixture"
    )
    private fun report(
        snapshot: PropagationSnapshot? = null,
        states: List<PropagationRefreshSourceState> = emptyList(),
    ): PropagationOfflineDiagnosticReport {
        val store = InMemoryPropagationSnapshotStore()
        snapshot?.let { store.save(it) }
        val stateStore = InMemoryPropagationRefreshStateStore()
        states.forEach { stateStore.save(it) }
        return PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(
                PropagationOperatingPictureService(store, stateStore)
                    .read(PropagationProjectionQuery(NOW))
            )
        )
    }
    private fun snapshot(vararg obs: SolarGeomagneticObservation) =
        PropagationSnapshot(
            snapshotId = "snapshot-1", capturedAtUtcMillis = NOW,
            solarGeomagnetic = obs.toList()
        )
    private fun encoded(json: String): PropagationOfflineSerializedReport {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        return PropagationOfflineSerializedReport(
            PropagationOfflineReportSerialization.CONTENT_TYPE,
            1, json, bytes.size,
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        )
    }
    private fun roundTrip(value: PropagationOfflineDiagnosticReport) {
        val wire = PropagationOfflineReportSerialization.serialize(value)
        val decoded = PropagationOfflineReportDecoder.decode(wire)
        equal(value, decoded, "typed report round trip")
        equal(wire, PropagationOfflineReportSerialization.serialize(decoded),
            "canonical byte/digest round trip")
        equal(decoded, PropagationOfflineReportDecoder.decodeCanonical(wire.json),
            "digest-free canonical decode")
    }
    @JvmStatic fun main(args: Array<String>) {
        val absent = report()
        roundTrip(absent)
        val golden = PropagationOfflineReportSerialization.serialize(absent)
        equal("d333296023c2c9e640fe35c349a04ea361d8760d593ccf102ac32801c14ea384",
            golden.sha256Hex, "preserve CP-0008P golden hash")
        yes(golden.json.contains("\"workspace\":null"), "missing cache remains null")

        val good = report(
            snapshot(solar("a-é-🌍")),
            listOf(sourceState("NOAA_SWPC_PLANETARY_KP",
                PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
                "quoted \"error\"\nbackslash \\ and ü \u0001 🌍"))
        )
        roundTrip(good)
        yes(good.visibleEvidenceIndex.size == 1, "indexed evidence")
        val rich = report(
            PropagationSnapshot(
                "rich", NOW, solarGeomagnetic = listOf(solar("solar")),
                ionosphericProducts = listOf(iono()),
                heardPaths = listOf(heard()),
                modeledPaths = listOf(modeled())
            ),
            listOf(
                sourceState("NOAA_SWPC_PLANETARY_KP",
                    PropagationRefreshSourceRole.NOAA_KP_OBSERVED),
                sourceState("NOAA_SWPC_GLOTEC_VTEC",
                    PropagationRefreshSourceRole.NOAA_GLOTEC),
                sourceState("PSK_REPORTER_PUBLIC_QUERY",
                    PropagationRefreshSourceRole.PSK_REPORTER),
                sourceState("SYNTHETIC_MODEL",
                    PropagationRefreshSourceRole.GENERIC)
            )
        )
        roundTrip(rich)
        equal(4, rich.visibleEvidenceIndex.size, "four projection/evidence kinds")
        val withAssessment = rich.copy(workspace = requireNotNull(rich.workspace).copy(
            selectedPathAssessment = PropagationPathAssessment(
                usability = PropagationUsability.MARGINAL,
                confidence = confidence(),
                reasons = listOf(PropagationAssessmentReason(
                    PropagationAssessmentReasonCode.MODEL_ONLY_NO_OBSERVED_PATH,
                    "synthetic analysis", listOf("path-model")
                )),
                evidenceIds = listOf("path-model")
            )
        ))
        roundTrip(withAssessment)
        yes(PropagationOfflineReportSerialization.serialize(rich).json.contains(
            "\"coordinate\":{\"latitude\":50.0,\"longitude\":8.2}"),
            "nested geometry preserved")
        yes(PropagationOfflineReportSerialization.serialize(rich).json.contains(
            "\"coverage\":{}"), "global coverage V1 singleton supported")

        val future = report(PropagationSnapshot(
            "future", NOW + 1_000,
            solarGeomagnetic = listOf(solar("future", NOW + 200))
        ), listOf(sourceState("NOAA_SWPC_PLANETARY_KP",
            PropagationRefreshSourceRole.NOAA_KP_OBSERVED)))
        roundTrip(future)
        yes(future.snapshot.isFutureDated && future.snapshot.snapshotAgeMillis == null,
            "future UTC never becomes a false age")
        yes(future.visibleEvidenceIndex.single().retrievalIsFutureDated,
            "future evidence remains labeled")
        val wire = PropagationOfflineReportSerialization.serialize(good)
        rejected("media type") { PropagationOfflineReportDecoder.decode(
            wire.copy(contentType = "application/json")) }
        rejected("wire version") { PropagationOfflineReportDecoder.decode(
            wire.copy(wireVersion = 2)) }
        rejected("digest") { PropagationOfflineReportDecoder.decode(
            wire.copy(sha256Hex = "0".repeat(64))) }
        rejected("metadata length") { PropagationOfflineReportDecoder.decode(
            wire.copy(utf8ByteCount = wire.utf8ByteCount + 1)) }
        rejected("truncation") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.dropLast(1))) }
        rejected("trailing garbage") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json + "false")) }
        rejected("unknown format") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace(PropagationOfflineReportSerialization.FORMAT, "unknown"))) }
        rejected("incompatible root version") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"wireVersion\":1}", "\"wireVersion\":2}"))) }
        rejected("schema version") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))) }
        rejected("duplicate key") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"wireVersion\":1}", "\"wireVersion\":1,\"wireVersion\":1}"))) }
        rejected("extra field") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"wireVersion\":1}", "\"wireVersion\":1,\"new\":0}"))) }
        rejected("reordered root") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("{\"format\":\"" + PropagationOfflineReportSerialization.FORMAT +
                "\",\"payload\":", "{\"payload\":").replace(
                ",\"wireVersion\":1}", ",\"wireVersion\":1,\"format\":\"" +
                    PropagationOfflineReportSerialization.FORMAT + "\"}"))) }
        rejected("noncanonical whitespace") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replaceFirst("{", "{ "))) }
        rejected("duplicated evidence id") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replaceFirst("\"evidenceId\":\"a-é-🌍\"", "\"evidenceId\":\"tampered\""))) }
        rejected("source summary drift") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"failedSourceCount\":1", "\"failedSourceCount\":0"))) }
        rejected("status provenance drift") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"evidenceCount\":1", "\"evidenceCount\":2"))) }
        rejected("future timestamp marker") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"retrievalIsFutureDated\":false",
                "\"retrievalIsFutureDated\":true"))) }
        rejected("invalid boolean") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"isFutureDated\":false", "\"isFutureDated\":maybe"))) }
        rejected("invalid enum") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"freshness\":\"FRESH\"", "\"freshness\":\"BOGUS\""))) }
        rejected("fractional integer") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"queriedAtUtcMillis\":2400000000",
                "\"queriedAtUtcMillis\":2400000000.0"))) }
        rejected("Long overflow") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"queriedAtUtcMillis\":2400000000",
                "\"queriedAtUtcMillis\":9223372036854775808"))) }
        rejected("floating overflow") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"planetaryKp\":3.75", "\"planetaryKp\":1e309"))) }
        rejected("noncanonical exponent") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"planetaryKp\":3.75", "\"planetaryKp\":3.75e0"))) }
        rejected("negative zero") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\"evidenceCount\":1", "\"evidenceCount\":-0"))) }
        rejected("unpaired escape") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("a-é-🌍", "a-\\uD800"))) }
        rejected("unpaired UTF16") { PropagationOfflineReportDecoder.decodeCanonical(
            wire.json + "\uD800") }
        rejected("raw control") { PropagationOfflineReportDecoder.decode(encoded(
            wire.json.replace("\\u0001", "\u0001"))) }
        rejected("oversize bytes") { PropagationOfflineReportDecoder.decodeCanonical(
            " ".repeat(PropagationOfflineReportSerialization.MAX_UTF8_BYTES + 1)) }
        rejected("oversize nesting") { PropagationOfflineReportDecoder.decodeCanonical(
            "[".repeat(60) + "0" + "]".repeat(60)) }
        rejected("array trailing comma") { PropagationOfflineReportDecoder.decodeCanonical(
            wire.json.replace("\"visibleEvidenceIndex\":[", "\"visibleEvidenceIndex\":[,")) }
        println("CP-0008Q canonical offline decoder: PASS assertions=" + assertions)
    }
}
