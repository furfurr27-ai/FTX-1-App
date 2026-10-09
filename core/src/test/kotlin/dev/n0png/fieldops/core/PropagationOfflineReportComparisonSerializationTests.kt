package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic host-only CP-0008T canonical comparison-export tests. */
object PropagationOfflineReportComparisonSerializationTests {
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

    @JvmStatic fun main(args: Array<String>) {
        val none = artifact(report())
        val a = artifact(report(
            PropagationSnapshot("snapshot-a", NOW - 20,
                solarGeomagnetic = listOf(observation("solar-é🌍", 2.0))),
            listOf(state("offline-solar"))
        ))
        val b = artifact(report(
            PropagationSnapshot("snapshot-b", NOW,
                solarGeomagnetic = listOf(observation("solar-é🌍", 4.0),
                    observation("second", 3.0))),
            listOf(state("offline-solar"), state("other-source")),
            time = NOW + 120
        ))
        val compare = PropagationOfflineReportComparisonService.compare(a, b)
        val serialized = PropagationOfflineReportComparisonSerialization.serialize(compare)
        val directly = PropagationOfflineReportComparisonSerialization.serialize(a, b)
        eq(serialized, directly, "comparison DTO vs original V1 artifacts")
        eq(serialized, PropagationOfflineReportComparisonSerialization.serialize(compare),
            "stable canonical export across calls")
        yes(PropagationOfflineReportComparisonSerialization.verify(compare, serialized),
            "byte/metadata equality for exported comparison")
        eq(1, serialized.wireVersion, "separate comparison wire V1")
        eq("application/vnd.fieldops.propagation-comparison+json;version=1",
            serialized.contentType, "distinct comparison media type")
        yes(serialized.json.startsWith(
            "{\"format\":\"fieldops.propagation.offline-comparison\",\"payload\":{"),
            "separate canonical comparison envelope")
        yes(serialized.json.endsWith(",\"wireVersion\":1}"), "canonical root version")
        yes(serialized.json.contains("\"sourceKey\":\"offline-solar\""),
            "source provenance retained")
        yes(serialized.json.contains("\"evidenceId\":\"solar-é🌍\""),
            "UTF-8 evidence ID retained")
        yes(serialized.json.contains("\"projectionContentChanged\":true"),
            "measurement-only changes represented")
        yes(serialized.json.contains("\"trust\":\"UNAUTHENTICATED_SELF_CONSISTENT\""),
            "integrity is not authenticated provenance")
        yes(serialized.json.contains("\"afterQueryTimeRelativeToBefore\":\"LATER\""),
            "original report UTC ordering retained")
        eq(a.sha256Hex, compare.beforeReceipt.sha256Hex,
            "first full report digest linked")
        eq(b.sha256Hex, compare.afterReceipt.sha256Hex,
            "second full report digest linked")
        eq(serialized.json.toByteArray(StandardCharsets.UTF_8).size,
            serialized.utf8ByteCount, "actual UTF-8 count")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(serialized.json.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        eq(hash, serialized.sha256Hex, "actual canonical payload digest")
        yes(!PropagationOfflineReportComparisonSerialization.verify(compare,
            serialized.copy(sha256Hex = "0".repeat(64))),
            "modified export metadata fails byte identity")

        val reverse = PropagationOfflineReportComparisonSerialization.serialize(b, a)
        yes(reverse.sha256Hex != serialized.sha256Hex,
            "comparison direction changes payload bytes")
        val identical = PropagationOfflineReportComparisonSerialization.serialize(a, a)
        yes(identical.json.contains("\"canonicalArtifactChanged\":false"),
            "identical report change flag")
        yes(identical.json.contains("\"evidenceChanges\""),
            "unchanged selected evidence still inspectable")
        val absent = PropagationOfflineReportComparisonSerialization.serialize(none, a)
        yes(absent.json.contains("\"change\":\"ADDED_TO_VIEW\""),
            "missing selected view distinguishes added evidence")
        eq(none.sha256Hex, PropagationOfflineReportSerialization.serialize(report()).sha256Hex,
            "CP-0008P canonical report serialization unchanged")

        denied("source row status/receipt count mismatch") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = emptyList()))
        }
        denied("evidence status/receipt count mismatch") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                evidenceChanges = emptyList()))
        }
        denied("provenance-authenticated top-level claim") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                originAuthenticated = true))
        }
        denied("cross-store-atomicity claim") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                crossStoreAtomicityVerified = true))
        }
        denied("forged authenticated import receipt") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                beforeReceipt = compare.beforeReceipt.copy(originAuthenticated = true)))
        }
        denied("forged trust on receipt") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                afterReceipt = compare.afterReceipt.copy(
                    trust = PropagationOfflineImportTrust.UNAUTHENTICATED_SELF_CONSISTENT,
                    crossStoreAtomicityVerified = true)))
        }
        denied("incorrect original time order") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                afterQueryTimeRelativeToBefore = PropagationOfflineComparisonTimeOrder.EARLIER))
        }
        denied("incorrect artifact drift marker") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                canonicalArtifactChanged = false))
        }
        denied("unsorted source comparison") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = compare.sourceChanges.reversed()))
        }
        denied("invalid source change classification") {
            val first = compare.sourceChanges.first()
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = listOf(first.copy(change =
                    PropagationOfflineComparisonChange.REMOVED_FROM_VIEW)) +
                    compare.sourceChanges.drop(1)))
        }
        denied("invalid source key mismatch") {
            val first = compare.sourceChanges.first()
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = listOf(first.copy(sourceKey = "mismatch")) +
                    compare.sourceChanges.drop(1)))
        }
        denied("invalid evidence index flag") {
            val first = compare.evidenceChanges.first()
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                evidenceChanges = listOf(first.copy(
                    selectedIndexChanged = !first.selectedIndexChanged)) +
                    compare.evidenceChanges.drop(1)))
        }
        denied("invalid evidence classification") {
            val first = compare.evidenceChanges.first()
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                evidenceChanges = listOf(first.copy(
                    change = PropagationOfflineComparisonChange.REMOVED_FROM_VIEW)) +
                    compare.evidenceChanges.drop(1)))
        }
        denied("oversized receipt digest") {
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                beforeReceipt = compare.beforeReceipt.copy(sha256Hex = "bad")))
        }
        denied("noncanonical invalid Unicode") {
            val row = compare.sourceChanges.first()
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = listOf(row.copy(sourceKey = "hi\uD800")) +
                    compare.sourceChanges.drop(1)))
        }
        denied("large UTF-8 limit") {
            val row = compare.sourceChanges.first()
            val huge = row.copy(sourceKey = "x".repeat(
                PropagationOfflineReportComparisonSerialization.MAX_UTF8_BYTES))
            PropagationOfflineReportComparisonSerialization.serialize(compare.copy(
                sourceChanges = listOf(huge) + compare.sourceChanges.drop(1)))
        }

        println("CP-0008T canonical comparison export: PASS assertions=$assertions")
    }
}
