package dev.n0png.fieldops.core.propagation

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.IdentityHashMap

/**
 * Versioned canonical JSON export of the COMPLETE CP-0008O typed report.
 * One deterministic in-memory UTF-8 document, no filesystem, fetch or clock.
 * Domain fields are sorted by name, unordered maps/sets are sorted and
 * semantically ordered lists are retained. Wire schema changes require
 * a deliberate version bump and golden-fixture update.
 */
data class PropagationOfflineSerializedReport(
    val contentType: String,
    val wireVersion: Int,
    val json: String,
    val utf8ByteCount: Int,
    val sha256Hex: String,
)

object PropagationOfflineReportSerialization {
    const val WIRE_VERSION = 1
    const val CONTENT_TYPE = "application/vnd.fieldops.propagation-diagnostic+json;version=1"
    const val FORMAT = "fieldops.propagation.offline-diagnostic"
    const val MAX_UTF8_BYTES = 16 * 1024 * 1024
    private const val MAX_DEPTH = 48
    private const val MAX_COLLECTION_ITEMS = 100_000
    private const val PRODUCTION_PACKAGE = "dev.n0png.fieldops.core.propagation."

    /** Output is a snapshot of already captured values, never live provider health. */
    fun serialize(report: PropagationOfflineDiagnosticReport):
        PropagationOfflineSerializedReport {
        validate(report)
        val out = StringBuilder()
        val active = IdentityHashMap<Any, Boolean>()
        out.append("{\"format\":")
        string(FORMAT, out)
        out.append(",\"payload\":")
        jsonValue(report, out, active, 0)
        out.append(",\"wireVersion\":").append(WIRE_VERSION).append('}')
        val json = out.toString()
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_UTF8_BYTES) {
            "Serialized report exceeds maximum UTF-8 byte count"
        }
        return PropagationOfflineSerializedReport(
            contentType = CONTENT_TYPE,
            wireVersion = WIRE_VERSION,
            json = json,
            utf8ByteCount = bytes.size,
            sha256Hex = sha256(bytes),
        )
    }

    /** Checks the full byte-for-byte canonical output, not a digital signature. */
    fun verify(report: PropagationOfflineDiagnosticReport,
        encoded: PropagationOfflineSerializedReport): Boolean =
        serialize(report) == encoded

    private fun validate(report: PropagationOfflineDiagnosticReport) {
        require(report.schemaVersion == 1) { "Expected report schemaVersion=1" }
        require(!report.summary.crossStoreAtomicityVerified) {
            "Cross-store atomicity is unverified"
        }
        require(report.sources.map { it.sourceKey } ==
            report.sources.map { it.sourceKey }.sorted()) {
            "Source rows must be stable and key-sorted"
        }
        require(report.summary.readySourceCount == report.sources.count {
            it.status.readiness == PropagationSourceRefreshReadiness.READY
        }) { "Ready source count drift" }
        require(report.summary.failedSourceCount == report.sources.count {
            it.status.lastAttempt == PropagationSourceLastAttempt.FAILED
        }) { "Failed source count drift" }
        require(report.summary.sourcesWithCachedEvidenceCount ==
            report.sources.count { it.status.hasCachedEvidence }) {
            "Cached source count drift"
        }
        require(report.summary.attributedCachedEvidenceCount ==
            report.sources.sumOf { it.status.evidenceCount }) {
            "Cached attributed evidence count drift"
        }
        require(report.summary.successesNewerThanSnapshotCount ==
            report.sources.count {
                it.timing.lastSuccessToSnapshotRelation ==
                    PropagationReadTimestampRelation.AFTER
            }) { "Snapshot/source timing count drift" }
        require(report.summary.retrievalsNewerThanSuccessCount ==
            report.sources.count {
                it.timing.newestRetrievalToLastSuccessRelation ==
                    PropagationReadTimestampRelation.AFTER
            }) { "Retrieval/source timing count drift" }
        require(report.summary.futureAttemptSourceCount ==
            report.sources.count { it.timing.lastAttemptIsFutureDated }) {
            "Future attempt count drift"
        }
        require(report.summary.futureRetrievalSourceCount ==
            report.sources.count { it.timing.newestRetrievalIsFutureDated }) {
            "Future retrieval count drift"
        }
        require(report.snapshot.isFutureDated ==
            (report.snapshot.capturedAtUtcMillis?.let {
                it > report.queriedAtUtcMillis
            } ?: false)) { "Snapshot future marker drift" }
        require(report.snapshot.snapshotAgeMillis ==
            report.snapshot.capturedAtUtcMillis?.takeIf {
                it <= report.queriedAtUtcMillis
            }?.let { report.queriedAtUtcMillis - it }) {
            "Snapshot age provenance drift"
        }
        val index = buildList {
            report.workspace?.heardPaths?.forEach {
                add(indexItem(PropagationOfflineEvidenceKind.HEARD_PATH, it.metadata))
            }
            report.workspace?.ionosphericProducts?.forEach {
                add(indexItem(PropagationOfflineEvidenceKind.IONOSPHERIC_MAP, it.metadata))
            }
            report.workspace?.solarGeomagnetic?.forEach {
                add(indexItem(PropagationOfflineEvidenceKind.SOLAR_GEOMAGNETIC, it.metadata))
            }
            report.workspace?.modeledPaths?.forEach {
                add(indexItem(PropagationOfflineEvidenceKind.MODELED_PATH, it.metadata))
            }
        }
        require(report.visibleEvidenceIndex == index) {
            "Evidence index must match selected workspace provenance exactly"
        }
    }

    private fun indexItem(
        kind: PropagationOfflineEvidenceKind, meta: PropagationProjectionMetadata,
    ) = PropagationOfflineEvidenceIndexItem(
        kind, meta.evidenceId, meta.source.sourceId, meta.observedAtUtcMillis,
        meta.source.retrievedAtUtcMillis, meta.freshness,
        meta.retrievalIsFutureDated,
    )

    private fun jsonValue(
        value: Any?, out: StringBuilder, active: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ) {
        require(depth <= MAX_DEPTH) { "Maximum report nesting depth exceeded" }
        when (value) {
            null -> out.append("null")
            is String -> string(value, out)
            is Char -> string(value.toString(), out)
            is Boolean -> out.append(value)
            is Byte, is Short, is Int, is Long -> out.append(value)
            is Float -> {
                require(value.isFinite()) { "Non-finite floating-point evidence" }
                out.append(value)
            }
            is Double -> {
                require(value.isFinite()) { "Non-finite floating-point evidence" }
                out.append(value)
            }
            is Enum<*> -> string(value.name, out)
            is Map<*, *> -> tracking(value, active) {
                require(value.size <= MAX_COLLECTION_ITEMS) { "Map too large" }
                val entries = value.entries.map { (key, item) ->
                    val k = when (key) {
                        is String -> key
                        is Enum<*> -> key.name
                        else -> throw IllegalArgumentException("Unsupported map key type")
                    }
                    k to item
                }.sortedBy { it.first }
                require(entries.map { it.first }.distinct().size == entries.size) {
                    "Serialized map key collision"
                }
                out.append('{')
                entries.forEachIndexed { i, (key, item) ->
                    if (i > 0) out.append(',')
                    string(key, out)
                    out.append(':')
                    jsonValue(item, out, active, depth + 1)
                }
                out.append('}')
            }
            is Collection<*> -> tracking(value, active) {
                require(value.size <= MAX_COLLECTION_ITEMS) { "Collection too large" }
                out.append('[')
                if (value is Set<*>) {
                    val canonical = value.map { item ->
                        val nested = StringBuilder()
                        jsonValue(item, nested, active, depth + 1)
                        nested.toString()
                    }.sorted()
                    canonical.forEachIndexed { i, item ->
                        if (i > 0) out.append(',')
                        out.append(item)
                    }
                } else {
                    value.forEachIndexed { i, item ->
                        if (i > 0) out.append(',')
                        jsonValue(item, out, active, depth + 1)
                    }
                }
                out.append(']')
            }
            else -> tracking(value, active) {
                val type = value.javaClass
                require(type.name.startsWith(PRODUCTION_PACKAGE) &&
                    !type.isAnonymousClass && !type.isSynthetic) {
                    "Unsupported report field type: " + type.name
                }
                val fields = type.declaredFields.filter {
                    !Modifier.isStatic(it.modifiers) && !it.isSynthetic
                }.sortedBy { it.name }
                require(fields.isNotEmpty() && fields.size <= 128) {
                    "Unsupported/oversized report object: " + type.name
                }
                out.append('{')
                fields.forEachIndexed { i, field ->
                    if (i > 0) out.append(',')
                    field.isAccessible = true
                    string(field.name, out)
                    out.append(':')
                    jsonValue(field.get(value), out, active, depth + 1)
                }
                out.append('}')
            }
        }
    }

    private inline fun tracking(
        value: Any, active: IdentityHashMap<Any, Boolean>, block: () -> Unit,
    ) {
        require(active.put(value, true) == null) { "Cycle in offline report graph" }
        try { block() } finally { active.remove(value) }
    }

    private fun string(raw: String, out: StringBuilder) {
        out.append('"')
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> when {
                    c.code < 0x20 -> {
                        out.append("\\u")
                        out.append(c.code.toString(16).padStart(4, '0'))
                    }
                    c.isHighSurrogate() -> {
                        require(i + 1 < raw.length && raw[i + 1].isLowSurrogate()) {
                            "Unpaired UTF-16 high surrogate"
                        }
                        out.append(c).append(raw[++i])
                    }
                    c.isLowSurrogate() -> error("Unpaired UTF-16 low surrogate")
                    else -> out.append(c)
                }
            }
            i++
        }
        out.append('"')
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
}
