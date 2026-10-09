package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008U strict comparison decode validation. */
object PropagationOfflineReportComparisonDecoderTests {
    private const val NOW = 2_400_000_000L
    private var assertions = 0

    private fun eq(a: Any?, b: Any?, name: String) {
        assertions++
        check(a == b) { "$name expected=$a actual=$b" }
    }
    private fun yes(ok: Boolean, name: String) {
        assertions++
        check(ok) { name }
    }
    private fun denied(name: String, f: () -> Unit) {
        assertions++
        check(runCatching(f).isFailure) { "Accepted invalid $name" }
    }

    private fun state(key: String) = PropagationRefreshSourceState(
        sourceKey = key, role = PropagationRefreshSourceRole.GENERIC,
        lastAttemptUtcMillis = NOW - 100,
        lastSuccessUtcMillis = NOW - 100,
        nextEligibleRefreshUtcMillis = NOW + 200,
    )

    private fun report(
        snapshot: PropagationSnapshot? = null,
        sources: List<PropagationRefreshSourceState> = emptyList(),
        time: Long = NOW,
    ): PropagationOfflineDiagnosticReport {
        val snapshots = InMemoryPropagationSnapshotStore()
        snapshot?.let(snapshots::save)
        val refresh = InMemoryPropagationRefreshStateStore()
        sources.forEach(refresh::save)
        val picture = PropagationOperatingPictureService(snapshots, refresh)
            .read(PropagationProjectionQuery(time))
        return PropagationOfflineDiagnosticReportService.build(
            PropagationReadModelConsistencyService.withDiagnostics(picture)
        )
    }

    private fun artifact(report: PropagationOfflineDiagnosticReport) =
        PropagationOfflineReportSerialization.serialize(report)

    private fun confidence() = PropagationConfidence(
        0.85, PropagationConfidenceBasis.SYNTHETIC, "fixture"
    )

    private fun observation(id: String, kp: Double) = SolarGeomagneticObservation(
        evidenceId = id,
        source = PropagationSourceRef(
            sourceId = "offline-solar", providerName = "synthetic 🌍",
            sourceClass = PropagationSourceClass.SYNTHETIC_FIXTURE,
            sourceVersion = "v1", retrievedAtUtcMillis = NOW - 50
        ),
        observedAtUtcMillis = NOW - 100,
        confidence = confidence(),
        quality = setOf(PropagationDataQuality.SYNTHETIC),
        planetaryKp = kp
    )


    private fun rewrap(original: PropagationOfflineSerializedComparison, json: String):
        PropagationOfflineSerializedComparison {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        return original.copy(
            json = json,
            utf8ByteCount = bytes.size,
            sha256Hex = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        )
    }

    private fun changed(
        original: PropagationOfflineSerializedComparison,
        old: String, replacement: String,
    ): PropagationOfflineSerializedComparison {
        check(original.json.contains(old)) { "Missing intended fixture field: $old" }
        return rewrap(original, original.json.replaceFirst(old, replacement))
    }

    @JvmStatic fun main(args: Array<String>) {
        val missing = artifact(report())
        val old = artifact(report(
            PropagationSnapshot("snap-a", NOW - 20,
                solarGeomagnetic = listOf(observation("solar-é🌍", 2.0))),
            listOf(state("offline-solar"))
        ))
        val current = artifact(report(
            PropagationSnapshot("snap-b", NOW,
                solarGeomagnetic = listOf(observation("solar-é🌍", 4.0),
                    observation("second", 3.0))),
            listOf(state("offline-solar"), state("other-source")),
            time = NOW + 120
        ))
        val comparison = PropagationOfflineReportComparisonService.compare(old, current)
        val wire = PropagationOfflineReportComparisonSerialization.serialize(comparison)
        val decoded = PropagationOfflineReportComparisonDecoder.decode(wire)

        eq(comparison, decoded, "typed DTO exact round trip")
        eq(comparison, PropagationOfflineReportComparisonDecoder.decodeCanonical(wire.json),
            "canonical JSON typed round trip")
        eq(wire, PropagationOfflineReportComparisonSerialization.serialize(decoded),
            "exact canonical bytes, digest, UTF-8 byte count")
        eq(old.sha256Hex, decoded.beforeReceipt.sha256Hex, "before original receipt")
        eq(current.sha256Hex, decoded.afterReceipt.sha256Hex, "after original receipt")
        eq(comparison.beforeReceipt, decoded.beforeReceipt, "all original before receipt fields")
        eq(comparison.afterReceipt, decoded.afterReceipt, "all original after receipt fields")
        eq(comparison.sourceChanges, decoded.sourceChanges, "nested source status/timing DTOs")
        eq(comparison.evidenceChanges, decoded.evidenceChanges, "nested selected evidence DTOs")
        eq(PropagationOfflineComparisonTimeOrder.LATER,
            decoded.afterQueryTimeRelativeToBefore, "original UTC query-time order")
        yes(decoded.evidenceChanges.any { it.projectionContentChanged },
            "full projection content-change classification retained")
        yes(decoded.evidenceChanges.any {
            it.change == PropagationOfflineComparisonChange.ADDED_TO_VIEW
        }, "selected-view presence semantics retained")
        yes(!decoded.originAuthenticated && !decoded.crossStoreAtomicityVerified,
            "no authenticated origin or atomicity invented")
        yes(decoded.beforeReceipt.trust ==
            PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
            "no authenticated provenance invented")

        for ((first, second, label) in listOf(
            Triple(current, old, "reverse UTC order"),
            Triple(old, old, "same report"),
            Triple(missing, old, "selected evidence absent before"),
            Triple(old, missing, "selected evidence absent after"),
            Triple(missing, missing, "both empty selected views"),
        )) {
            val input = PropagationOfflineReportComparisonSerialization.serialize(first, second)
            val parsed = PropagationOfflineReportComparisonDecoder.decode(input)
            eq(input, PropagationOfflineReportComparisonSerialization.serialize(parsed),
                "$label canonical round trip")
            eq(PropagationOfflineReportComparisonService.compare(first, second), parsed,
                "$label original report comparison DTO")
        }
        val reverse = PropagationOfflineReportComparisonDecoder.decode(
            PropagationOfflineReportComparisonSerialization.serialize(current, old))
        eq(PropagationOfflineComparisonTimeOrder.EARLIER,
            reverse.afterQueryTimeRelativeToBefore, "reverse original UTC order")
        val same = PropagationOfflineReportComparisonDecoder.decode(
            PropagationOfflineReportComparisonSerialization.serialize(old, old))
        eq(PropagationOfflineComparisonTimeOrder.SAME,
            same.afterQueryTimeRelativeToBefore, "same UTC order")

        denied("wrong media type") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(contentType = "application/json")) }
        denied("report media type masquerades as comparison") {
            PropagationOfflineReportComparisonDecoder.decode(
                wire.copy(contentType = PropagationOfflineReportSerialization.CONTENT_TYPE))
        }
        denied("metadata version") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(wireVersion = 2)) }
        denied("zero byte count") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(utf8ByteCount = 0)) }
        denied("incorrect byte count") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(utf8ByteCount = wire.utf8ByteCount + 1)) }
        denied("uppercase SHA format") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(sha256Hex = wire.sha256Hex.uppercase())) }
        denied("malformed SHA") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(sha256Hex = "xyz")) }
        denied("wrong SHA") { PropagationOfflineReportComparisonDecoder.decode(
            wire.copy(sha256Hex = "0".repeat(64))) }
        denied("stale digest after byte mutation") {
            PropagationOfflineReportComparisonDecoder.decode(
                wire.copy(json = wire.json.replaceFirst("snap-a", "snap-z")))
        }
        denied("truncated JSON") { PropagationOfflineReportComparisonDecoder.decode(
            rewrap(wire, wire.json.dropLast(1))) }
        denied("trailing JSON") { PropagationOfflineReportComparisonDecoder.decode(
            rewrap(wire, wire.json + "null")) }
        denied("leading whitespace noncanonical") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire, " " + wire.json))
        }
        denied("trailing whitespace noncanonical") {
            PropagationOfflineReportComparisonDecoder.decodeCanonical(wire.json + " ")
        }
        denied("noncanonical escaped slash") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire,
                wire.json.replaceFirst("offline-solar", "offline\\/solar")))
        }
        denied("wrong format with corrected checksum") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "fieldops.propagation.offline-comparison",
                "fieldops.propagation.offline-diagnostic"))
        }
        denied("embedded wrong wire version with corrected checksum") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"wireVersion\":1}", "\"wireVersion\":2}"))
        }
        denied("missing root payload") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"payload\":", "\"extra\":"))
        }
        denied("duplicate JSON object key") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire,
                wire.json.replaceFirst("\"payload\":",
                    "\"format\":\"duplicate\",\"payload\":")))
        }
        denied("unknown change enum") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"change\":\"ADDED_TO_VIEW\"",
                "\"change\":\"NOT_A_CHANGE\""))
        }
        denied("invalid original receipt digest, recomputed outer digest") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                old.sha256Hex, "bad-original-report-digest"))
        }
        denied("incompatible original report receipt content type") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                PropagationOfflineReportSerialization.CONTENT_TYPE,
                "application/json"))
        }
        denied("nested receipt origin authentication claim") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"originAuthenticated\":false",
                "\"originAuthenticated\":true"))
        }
        denied("nested receipt cross-store atomicity claim") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"crossStoreAtomicityVerified\":false",
                "\"crossStoreAtomicityVerified\":true"))
        }
        denied("receipt source count inconsistent with selected view") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"sourceCount\":1", "\"sourceCount\":999"))
        }
        denied("original receipt query time negative") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"reportQueryUtcMillis\":2400000000",
                "\"reportQueryUtcMillis\":-1"))
        }
        denied("changed-view enum inconsistent with row") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"change\":\"ADDED_TO_VIEW\"",
                "\"change\":\"UNCHANGED\""))
        }
        denied("evidence metadata-change flag mismatch") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"selectedIndexChanged\":false",
                "\"selectedIndexChanged\":true"))
        }
        val topAuthKey = "\"originAuthenticated\":false"
        val topAuthPosition = wire.json.lastIndexOf(topAuthKey)
        check(topAuthPosition >= 0)
        denied("top-level flag claims authenticated") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire,
                wire.json.replaceRange(topAuthPosition,
                    topAuthPosition + topAuthKey.length,
                    "\"originAuthenticated\":true")))
        }
        denied("noncanonical wire line endings") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire,
                wire.json.replaceFirst(",", ",\n")))
        }
        denied("unknown nested comparison DTO key") {
            PropagationOfflineReportComparisonDecoder.decode(changed(wire,
                "\"snapshotChanged\":", "\"unrecognizedChanged\":"))
        }
        denied("invalid Unicode escape") {
            PropagationOfflineReportComparisonDecoder.decode(rewrap(wire,
                wire.json.replaceFirst("solar-é🌍", "\\uD800")))
        }
        denied("unpaired UTF-16") {
            PropagationOfflineReportComparisonDecoder.decodeCanonical(wire.json + '\uD800')
        }
        denied("oversized raw comparison") {
            PropagationOfflineReportComparisonDecoder.decodeCanonical(
                " ".repeat(PropagationOfflineReportComparisonSerialization.MAX_UTF8_BYTES + 1))
        }
        denied("too deep JSON") {
            PropagationOfflineReportComparisonDecoder.decodeCanonical(
                "[".repeat(60) + "0" + "]".repeat(60))
        }
        // Bare JSON digest cannot establish origin: successful decode is
        // merely self-consistent, and original CP-0008P reports are required
        // for independent projection-content-change verification.
        println("CP-0008U strict comparison decode: PASS assertions=$assertions")
    }
}
