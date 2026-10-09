package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import dev.n0png.fieldops.core.map.GeoCoordinate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** CP-0008R synthetic import boundary tests; never uses real providers or accounts. */
object PropagationOfflineReportImportTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0
    private fun yes(ok: Boolean, label: String) {
        assertions++
        check(ok) { label }
    }
    private fun equal(expected: Any?, actual: Any?, label: String) {
        assertions++
        check(expected == actual) { "$" + "{label} expected=$" + "{expected} actual=$" + "{actual}" }
    }
    private fun denied(label: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Accepted invalid " + label }
    }
    private fun source(id: String) = PropagationSourceRef(
        sourceId = id, providerName = "synthetic fixture 🌍",
        sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
        sourceVersion = "v1", retrievedAtUtcMillis = NOW - 50
    )
    private fun confidence() = PropagationConfidence(
        0.9, PropagationConfidenceBasis.SYNTHETIC, "offline only"
    )
    private fun place(lat: Double, lon: Double) = PropagationPosition(
        coordinate = GeoCoordinate(latitude = lat, longitude = lon),
        method = PropagationLocationMethod.EXPLICIT_COORDINATE
    )
    private fun solar(id: String) = SolarGeomagneticObservation(
        evidenceId = id, source = source("NOAA_SWPC_PLANETARY_KP"),
        observedAtUtcMillis = NOW - 120, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = 3.5
    )
    private fun heard() = HeardPathObservation(
        evidenceId = "heard-40m", source = source("PSK_REPORTER_PUBLIC_QUERY"),
        observedAtUtcMillis = NOW - 125, confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        transmitter = PropagationEndpoint(place(50.0, 8.2), callsign = "N0PNG"),
        receiver = PropagationEndpoint(place(49.9, 7.7), callsign = "DL1TEST"),
        frequencyHz = 7_075_000L, band = "40m", mode = "FT8",
        snrDb = -10.5, reportCount = 2
    )
    private fun state(key: String, role: PropagationRefreshSourceRole) =
        PropagationRefreshSourceState(
            sourceKey = key, role = role,
            lastAttemptUtcMillis = NOW - 10,
            lastSuccessUtcMillis = NOW - 10,
            nextEligibleRefreshUtcMillis = NOW + 60
        )
    private fun fixture(
        snapshot: PropagationSnapshot? = null,
        states: List<PropagationRefreshSourceState> = emptyList()
    ): Triple<PropagationOfflineDiagnosticReport,
        InMemoryPropagationSnapshotStore, InMemoryPropagationRefreshStateStore> {
        val snapshots = InMemoryPropagationSnapshotStore()
        snapshot?.let { snapshots.save(it) }
        val refresh = InMemoryPropagationRefreshStateStore()
        states.forEach { refresh.save(it) }
        val picture = PropagationOperatingPictureService(snapshots, refresh)
            .read(PropagationProjectionQuery(NOW))
        val report = PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(picture)
        )
        return Triple(report, snapshots, refresh)
    }
    private fun encoded(json: String): PropagationOfflineSerializedReport {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return PropagationOfflineSerializedReport(
            contentType = PropagationOfflineReportSerialization.CONTENT_TYPE,
            wireVersion = 1,
            json = json,
            utf8ByteCount = bytes.size,
            sha256Hex = digest
        )
    }
    @JvmStatic fun main(args: Array<String>) {
        val absent = fixture()
        val emptyWire = PropagationOfflineReportSerialization.serialize(absent.first)
        val emptyImported = PropagationOfflineReportImportService.importReport(emptyWire)
        equal(absent.first, emptyImported.report, "absent snapshot exact typed report")
        equal(null, emptyImported.workspace, "absent workspace is not fabricated")
        equal(null, emptyImported.snapshot.snapshotId, "absent snapshot ID")
        equal(0, emptyImported.receipt.sourceCount, "no fabricated source")
        yes(emptyImported.visibleEvidenceIndex.isEmpty(), "no fabricated evidence")
        equal(PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
            emptyImported.receipt.trust, "explicit no-auth trust class")

        val original = fixture(
            PropagationSnapshot(
                snapshotId = "cached-fixture", capturedAtUtcMillis = NOW,
                solarGeomagnetic = listOf(solar("solar-🌍")),
                heardPaths = listOf(heard())
            ),
            listOf(
                state("NOAA_SWPC_PLANETARY_KP",
                    PropagationRefreshSourceRole.NOAA_KP_OBSERVED),
                state("PSK_REPORTER_PUBLIC_QUERY",
                    PropagationRefreshSourceRole.PSK_REPORTER)
            )
        )
        val beforeSnapshot = original.second.latest()
        val beforeStates = original.third.all()
        val wire = PropagationOfflineReportSerialization.serialize(original.first)
        val imported = PropagationOfflineReportImportService.importReport(wire)
        equal(original.first, imported.report, "lossless imported report")
        equal(wire, PropagationOfflineReportSerialization.serialize(imported.report),
            "byte-identical wire after import")
        equal(wire.sha256Hex, imported.receipt.sha256Hex, "original digest retained")
        equal(wire.utf8ByteCount, imported.receipt.utf8ByteCount, "original byte count retained")
        equal(wire.contentType, imported.receipt.contentType, "content type retained")
        equal(NOW, imported.receipt.reportQueryUtcMillis, "query UTC not recalculated")
        equal("cached-fixture", imported.receipt.snapshotId, "snapshot identity retained")
        equal(NOW, imported.receipt.snapshotCapturedAtUtcMillis,
            "snapshot capture UTC not recalculated")
        equal(2, imported.receipt.sourceCount, "source count retained")
        equal(2, imported.receipt.visibleEvidenceCount, "selected evidence count")
        yes(!imported.receipt.originAuthenticated, "no invented provider authentication")
        yes(!imported.receipt.crossStoreAtomicityVerified, "no cross-store guarantee")
        equal(null, imported.sourceByKey("unknown"), "unknown source stays absent")
        equal("NOAA_SWPC_PLANETARY_KP",
            imported.sourceByKey("NOAA_SWPC_PLANETARY_KP")?.sourceKey,
            "source status lookup")
        equal(null, imported.selectedEvidenceById("not-selected"),
            "unknown evidence stays absent")
        equal("solar-🌍", imported.selectedEvidenceById("solar-🌍")?.evidenceId,
            "indexed evidence identity")
        equal("NOAA_SWPC_PLANETARY_KP",
            imported.selectedEvidenceById("solar-🌍")?.sourceId,
            "indexed provenance retained")
        equal(1, imported.selectedEvidenceByKind(
            PropagationOfflineEvidenceKind.HEARD_PATH).size,
            "kind filter for selected heard paths")
        equal(1, imported.selectedEvidenceByKind(
            PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC).size,
            "kind filter for selected solar")
        yes(imported.selectedEvidenceByKind(
            PropagationOfflineEvidenceKind.IONOSPHERIC_MAP).isEmpty(),
            "unselected kinds not manufactured")
        equal(beforeSnapshot, original.second.latest(), "import never updates snapshot store")
        equal(beforeStates, original.third.all(), "import never updates refresh state")
        yes(imported.report !== original.first, "detached typed report instance")
        yes(imported.workspace !== original.first.workspace, "detached workspace instance")

        // Independently valid and rehashed data is still not authenticated.
        val alternate = fixture(
            PropagationSnapshot("other", NOW, solarGeomagnetic = listOf(solar("other"))),
            listOf(state("NOAA_SWPC_PLANETARY_KP",
                PropagationRefreshSourceRole.NOAA_KP_OBSERVED))
        )
        val forged = PropagationOfflineReportImportService.importReport(
            PropagationOfflineReportSerialization.serialize(alternate.first))
        equal("other", forged.receipt.snapshotId, "modified valid report imported as data")
        yes(!forged.receipt.originAuthenticated,
            "recomputed SHA cannot authenticate origin")

        denied("unknown content type") { PropagationOfflineReportImportService.importReport(
            wire.copy(contentType = "application/json")) }
        denied("unsupported wire version") { PropagationOfflineReportImportService.importReport(
            wire.copy(wireVersion = 2)) }
        denied("tampered checksum") { PropagationOfflineReportImportService.importReport(
            wire.copy(sha256Hex = "0".repeat(64))) }
        denied("tampered length") { PropagationOfflineReportImportService.importReport(
            wire.copy(utf8ByteCount = wire.utf8ByteCount + 1)) }
        denied("truncated JSON") { PropagationOfflineReportImportService.importReport(
            encoded(wire.json.dropLast(1))) }
        denied("unknown payload field, with recomputed digest") {
            PropagationOfflineReportImportService.importReport(encoded(
                wire.json.replace("\"wireVersion\":1}", "\"wireVersion\":1,\"extra\":0}")))
        }
        denied("noncanonical whitespace, with recomputed digest") {
            PropagationOfflineReportImportService.importReport(encoded(
                wire.json.replaceFirst("{", "{ ")))
        }
        denied("malformed Unicode") { PropagationOfflineReportImportService.importReport(
            wire.copy(json = wire.json + '\uD800')) }
        println("CP-0008R offline import boundary: PASS assertions=" + assertions)
    }
}
