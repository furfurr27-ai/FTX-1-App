package dev.n0png.fieldops.core.propagation

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Strict, bounded and platform-neutral decoder for CP-0008P canonical JSON V1.
 * Integrity SHA-256 is NOT a signature and cannot authenticate a modified report.
 */
object PropagationOfflineReportDecoder {
    private const val MAX_DEPTH = 48
    private const val MAX_ITEMS = 100_000
    private const val DOMAIN = "dev.n0png.fieldops.core.propagation."

    fun decode(encoded: PropagationOfflineSerializedReport): PropagationOfflineDiagnosticReport {
        require(encoded.contentType == PropagationOfflineReportSerialization.CONTENT_TYPE)
        require(encoded.wireVersion == PropagationOfflineReportSerialization.WIRE_VERSION)
        val bytes = checkedBytes(encoded.json)
        require(encoded.utf8ByteCount == bytes.size) { "Length mismatch" }
        require(encoded.sha256Hex.matches(Regex("[0-9a-f]{64}"))) { "Invalid digest format" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(MessageDigest.isEqual(
            digest.toByteArray(StandardCharsets.US_ASCII),
            encoded.sha256Hex.toByteArray(StandardCharsets.US_ASCII)
        )) { "SHA-256 mismatch" }
        return decodeCanonical(encoded.json)
    }

    /** Decode raw canonical JSON when artifact metadata is not available. */
    fun decodeCanonical(json: String): PropagationOfflineDiagnosticReport {
        checkedBytes(json)
        val envelope = obj(Reader(json).read())
        require(envelope.keys == setOf("format", "payload", "wireVersion")) {
            "Incompatible envelope"
        }
        require(envelope["format"] == PropagationOfflineReportSerialization.FORMAT)
        require(envelope["wireVersion"] == Num("1")) { "Incompatible wire version" }
        val report = typed(envelope["payload"],
            PropagationOfflineDiagnosticReport::class.java, 0)
            as PropagationOfflineDiagnosticReport
        correlations(report)
        require(PropagationOfflineReportSerialization.serialize(report).json == json) {
            "Not canonical wire V1"
        }
        return report
    }

    private fun checkedBytes(json: String): ByteArray {
        require(json.length <= PropagationOfflineReportSerialization.MAX_UTF8_BYTES) {
            "Character size limit exceeded"
        }
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (c.isHighSurrogate()) {
                require(i + 1 < json.length && json[i + 1].isLowSurrogate()) {
                    "Unpaired high surrogate"
                }
                i++
            } else require(!c.isLowSurrogate()) { "Unpaired low surrogate" }
            i++
        }
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= PropagationOfflineReportSerialization.MAX_UTF8_BYTES) {
            "UTF-8 size limit exceeded"
        }
        return bytes
    }

    private data class Num(val text: String)

    private fun obj(v: Any?): Map<String, Any?> {
        require(v is Map<*, *>) { "Expected object" }
        @Suppress("UNCHECKED_CAST")
        return v as Map<String, Any?>
    }

    private fun typed(v: Any?, type: Type, depth: Int): Any? {
        require(depth <= MAX_DEPTH) { "Nesting limit" }
        if (v == null) {
            if (type is Class<*>) require(!type.isPrimitive) { "Null primitive" }
            return null
        }
        if (type is WildcardType) {
            require(type.lowerBounds.isEmpty() && type.upperBounds.size == 1) {
                "Unsupported wildcard report type"
            }
            return typed(v, type.upperBounds.single(), depth)
        }
        if (type is ParameterizedType) {
            val raw = type.rawType as? Class<*>
                ?: throw IllegalArgumentException("Invalid generic field")
            val args = type.actualTypeArguments
            if (List::class.java.isAssignableFrom(raw) ||
                Set::class.java.isAssignableFrom(raw)) {
                require(v is List<*> && v.size <= MAX_ITEMS) { "Invalid/oversized array" }
                val values = v.map { typed(it, args.single(), depth + 1) }
                return if (Set::class.java.isAssignableFrom(raw)) {
                    val unique = LinkedHashSet(values)
                    require(unique.size == values.size) { "Duplicate set value" }
                    unique
                } else values
            }
            if (Map::class.java.isAssignableFrom(raw)) {
                val entries = obj(v)
                require(entries.size <= MAX_ITEMS) { "Oversized map" }
                return linkedMapOf<Any, Any?>().apply {
                    entries.forEach { (key, value) ->
                        val converted = when (val k = args[0]) {
                            String::class.java -> key
                            is Class<*> -> {
                                require(k.isEnum) { "Invalid map key type" }
                                k.enumConstants.singleOrNull { (it as Enum<*>).name == key }
                                    ?: throw IllegalArgumentException("Unknown map enum")
                            }
                            else -> throw IllegalArgumentException("Invalid key type")
                        }
                        require(!containsKey(converted)) { "Duplicate typed key" }
                        put(converted, typed(value, args[1], depth + 1))
                    }
                }
            }
            throw IllegalArgumentException("Unsupported generic report type")
        }
        require(type is Class<*>) { "Unsupported report type" }
        return when {
            type == String::class.java -> { require(v is String); v }
            type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> {
                require(v is Num && v.text.matches(Regex("-?(0|[1-9][0-9]*)")))
                v.text.toIntOrNull() ?: throw IllegalArgumentException("Int overflow")
            }
            type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> {
                require(v is Num && v.text.matches(Regex("-?(0|[1-9][0-9]*)")))
                v.text.toLongOrNull() ?: throw IllegalArgumentException("Long overflow")
            }
            type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> {
                require(v is Num)
                v.text.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Non-finite double")
            }
            type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> {
                require(v is Num)
                v.text.toFloatOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Non-finite float")
            }
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> {
                require(v is Boolean); v
            }
            type.isEnum -> {
                require(v is String)
                type.enumConstants.singleOrNull { (it as Enum<*>).name == v }
                    ?: throw IllegalArgumentException("Unknown enum")
            }
            type == PropagationCoverage::class.java -> {
                when (obj(v).keys) {
                    emptySet<String>() -> GlobalPropagationCoverage
                    setOf("position") -> typed(v, PointPropagationCoverage::class.java, depth + 1)
                    setOf("bounds") -> typed(v, BoundsPropagationCoverage::class.java, depth + 1)
                    setOf("origin", "destination") ->
                        typed(v, PathPropagationCoverage::class.java, depth + 1)
                    else -> throw IllegalArgumentException("Unknown coverage shape")
                }
            }
            type == GlobalPropagationCoverage::class.java -> {
                require(obj(v).isEmpty())
                GlobalPropagationCoverage
            }
            else -> construct(v, type, depth)
        }
    }

    private fun construct(v: Any, type: Class<*>, depth: Int): Any {
        require(type.name.startsWith(DOMAIN) ||
            type == GeoCoordinate::class.java || type == GeoBounds::class.java) {
            "Unapproved report DTO"
        }
        require(!type.isInterface && !Modifier.isAbstract(type.modifiers))
        val data = obj(v)
        val fields = type.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && !it.isSynthetic
        }
        require(fields.isNotEmpty() && fields.size <= 128 &&
            fields.size == data.size && fields.map { it.name }.toSet() == data.keys) {
            "Incompatible DTO fields: " + type.simpleName
        }
        // Kotlin V1 primary-constructor backing fields retain source declaration order.
        // Verify exact JVM parameter types; never select a synthetic/default constructor.
        val ctor = type.declaredConstructors.singleOrNull {
            !it.isSynthetic && it.parameterCount == fields.size &&
                it.parameterTypes.toList() == fields.map { field -> field.type }
        } ?: throw IllegalArgumentException("Incompatible constructor: " + type.simpleName)
        val values = fields.map {
            typed(data[it.name], it.genericType, depth + 1)
        }.toTypedArray()
        return try {
            ctor.isAccessible = true
            ctor.newInstance(*values)
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException("Invalid report data: " + type.simpleName, e)
        }
    }

    private fun rel(a: Long?, b: Long?) = when {
        a == null || b == null -> PropagationReadTimestampRelation.UNKNOWN
        a < b -> PropagationReadTimestampRelation.BEFORE
        a > b -> PropagationReadTimestampRelation.AFTER
        else -> PropagationReadTimestampRelation.EQUAL
    }
    private fun diff(a: Long?, b: Long?): Long? =
        if (a == null || b == null) null else Math.subtractExact(a, b)

    private fun correlations(report: PropagationOfflineDiagnosticReport) {
        val now = report.queriedAtUtcMillis
        report.sources.forEach { source ->
            val s = source.status
            val d = source.timing
            require(s.evidenceCount >= 0 &&
                s.evidenceFreshnessCounts.keys == PropagationFreshness.entries.toSet() &&
                s.evidenceFreshnessCounts.values.all { it >= 0 } &&
                s.evidenceFreshnessCounts.values.sumOf { it.toLong() } == s.evidenceCount.toLong()) {
                "Source freshness sum mismatch"
            }
            require(d.freshEvidenceCount ==
                    (s.evidenceFreshnessCounts[PropagationFreshness.FRESH] ?: 0) &&
                d.agingEvidenceCount ==
                    (s.evidenceFreshnessCounts[PropagationFreshness.AGING] ?: 0) &&
                d.staleEvidenceCount ==
                    (s.evidenceFreshnessCounts[PropagationFreshness.STALE] ?: 0) &&
                d.futureDatedEvidenceCount ==
                    (s.evidenceFreshnessCounts[PropagationFreshness.FUTURE_DATED] ?: 0)) {
                "Source diagnostic freshness mismatch"
            }
            require(s.lastSuccessIsFutureDated ==
                (s.lastSuccessUtcMillis?.let { it > now } ?: false) &&
                s.lastSuccessAgeMillis ==
                s.lastSuccessUtcMillis?.takeIf { it <= now }?.let { Math.subtractExact(now, it) } &&
                d.lastAttemptIsFutureDated ==
                (s.lastAttemptUtcMillis?.let { it > now } ?: false) &&
                d.newestRetrievalIsFutureDated ==
                (s.newestEvidenceRetrievedUtcMillis?.let { it > now } ?: false)) {
                "Source clock marker mismatch"
            }
            require(d.lastSuccessToSnapshotRelation ==
                rel(s.lastSuccessUtcMillis, report.snapshot.capturedAtUtcMillis) &&
                d.newestRetrievalToLastSuccessRelation ==
                rel(s.newestEvidenceRetrievedUtcMillis, s.lastSuccessUtcMillis) &&
                d.lastSuccessMinusSnapshotCaptureMillis ==
                diff(s.lastSuccessUtcMillis, report.snapshot.capturedAtUtcMillis) &&
                d.newestRetrievalMinusLastSuccessMillis ==
                diff(s.newestEvidenceRetrievedUtcMillis, s.lastSuccessUtcMillis)) {
                "Source diagnostic timestamp mismatch"
            }
            val attempt = when {
                s.consecutiveFailures > 0 -> PropagationSourceLastAttempt.FAILED
                s.lastAttemptUtcMillis != null -> PropagationSourceLastAttempt.SUCCEEDED
                else -> PropagationSourceLastAttempt.NEVER_ATTEMPTED
            }
            require(s.lastAttempt == attempt &&
                s.nextEligibleRefreshUtcMillis >= 0 &&
                (s.lastAttemptUtcMillis == null || s.lastAttemptUtcMillis >= 0) &&
                (s.lastSuccessUtcMillis == null || s.lastSuccessUtcMillis >= 0) &&
                (s.lastSuccessUtcMillis == null || (s.lastAttemptUtcMillis != null &&
                    s.lastSuccessUtcMillis <= s.lastAttemptUtcMillis)) &&
                (s.consecutiveFailures != 0 ||
                    (s.lastFailureMessage == null && s.lastFailureRetryable == null)) &&
                (s.consecutiveFailures == 0 ||
                    (!s.lastFailureMessage.isNullOrBlank() && s.lastFailureRetryable != null))) {
                "Source attempt status provenance mismatch"
            }
            require(s.consecutiveFailures >= 0 && s.remainingWaitMillis >= 0 &&
                s.remainingWaitMillis == (if (s.nextEligibleRefreshUtcMillis > now)
                    Math.subtractExact(s.nextEligibleRefreshUtcMillis, now) else 0L)) {
                "Source wait mismatch"
            }
            val readiness = when {
                s.remainingWaitMillis == 0L -> PropagationSourceRefreshReadiness.READY
                s.consecutiveFailures > 0 && s.lastFailureRetryable == true ->
                    PropagationSourceRefreshReadiness.RETRY_BACKOFF
                s.consecutiveFailures > 0 -> PropagationSourceRefreshReadiness.FAILURE_COOLDOWN
                else -> PropagationSourceRefreshReadiness.CADENCE_WAIT
            }
            require(s.readiness == readiness) { "Source readiness mismatch" }
        }
        report.workspace?.let { workspace ->
            val metas = workspace.heardPaths.map { it.metadata } +
                workspace.ionosphericProducts.map { it.metadata } +
                workspace.solarGeomagnetic.map { it.metadata } +
                workspace.modeledPaths.map { it.metadata }
            metas.forEach { meta ->
                require(meta.observedAtUtcMillis >= 0L &&
                    meta.freshness == PropagationFreshnessClassifier.classify(
                        meta.observedAtUtcMillis, now,
                        PropagationSourceFreshnessDefaults.policyForSourceId(meta.source.sourceId)
                    )) { "Evidence freshness provenance mismatch" }
                require(meta.retrievalIsFutureDated == (meta.source.retrievedAtUtcMillis > now) &&
                    meta.retrievalAgeMillis == meta.source.retrievedAtUtcMillis
                        .takeIf { it <= now }?.let { Math.subtractExact(now, it) }) {
                    "Evidence retrieval marker mismatch"
                }
            }
            require(report.snapshot.oldestRetrievalUtcMillis == null ||
                report.snapshot.newestRetrievalUtcMillis == null ||
                report.snapshot.oldestRetrievalUtcMillis <= report.snapshot.newestRetrievalUtcMillis) {
                "Reversed retrieval bounds"
            }
        }
    }

    /** Independent bounded JSON scanner; final canonical serializer rejects non-V1 spellings. */
    private class Reader(private val input: String) {
        private var p = 0
        private fun fail(): Nothing = throw IllegalArgumentException("Invalid JSON at " + p)
        private fun next(): Char? = input.getOrNull(p)
        private fun eat(ch: Char) { if (next() != ch) fail(); p++ }
        private fun ws() { while (next() in listOf(' ', '\n', '\r', '\t')) p++ }
        fun read(): Any? {
            val v = value(0)
            ws()
            if (p != input.length) fail()
            return v
        }
        private fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail()
            ws()
            return when (next()) {
                '{' -> map(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> { word("true"); true }
                'f' -> { word("false"); false }
                'n' -> { word("null"); null }
                else -> if (next() == '-' || next()?.isDigit() == true) number() else fail()
            }
        }
        private fun word(w: String) {
            if (!input.startsWith(w, p)) fail()
            p += w.length
        }
        private fun map(depth: Int): Map<String, Any?> {
            eat('{'); ws()
            val result = linkedMapOf<String, Any?>()
            if (next() == '}') { p++; return result }
            while (true) {
                if (result.size >= MAX_ITEMS || next() != '"') fail()
                val key = string()
                if (result.containsKey(key)) fail()
                ws(); eat(':')
                result[key] = value(depth + 1)
                ws()
                if (next() == '}') { p++; return result }
                eat(','); ws()
            }
        }
        private fun array(depth: Int): List<Any?> {
            eat('['); ws()
            val result = ArrayList<Any?>()
            if (next() == ']') { p++; return result }
            while (true) {
                if (result.size >= MAX_ITEMS) fail()
                result.add(value(depth + 1))
                ws()
                if (next() == ']') { p++; return result }
                eat(','); ws()
            }
        }
        private fun string(): String {
            eat('"')
            val result = StringBuilder()
            while (true) {
                val ch = next() ?: fail()
                p++
                if (ch == '"') return result.toString()
                if (ch == '\\') {
                    val escaped = next() ?: fail()
                    p++
                    when (escaped) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            val ch1 = hex().toChar()
                            if (ch1.isHighSurrogate()) {
                                eat('\\'); eat('u')
                                val ch2 = hex().toChar()
                                if (!ch2.isLowSurrogate()) fail()
                                result.append(ch1).append(ch2)
                            } else {
                                if (ch1.isLowSurrogate()) fail()
                                result.append(ch1)
                            }
                        }
                        else -> fail()
                    }
                } else {
                    if (ch.code < 0x20) fail()
                    if (ch.isHighSurrogate()) {
                        val low = next() ?: fail()
                        if (!low.isLowSurrogate()) fail()
                        result.append(ch).append(low)
                        p++
                    } else {
                        if (ch.isLowSurrogate()) fail()
                        result.append(ch)
                    }
                }
                if (result.length > PropagationOfflineReportSerialization.MAX_UTF8_BYTES) fail()
            }
        }
        private fun hex(): Int {
            if (p + 4 > input.length) fail()
            val n = input.substring(p, p + 4).toIntOrNull(16) ?: fail()
            p += 4
            return n
        }
        private fun number(): Num {
            val start = p
            if (next() == '-') p++
            if (next() == '0') p++ else {
                if (next() !in '1'..'9') fail()
                while (next()?.isDigit() == true) p++
            }
            if (next() == '.') {
                p++
                if (next()?.isDigit() != true) fail()
                while (next()?.isDigit() == true) p++
            }
            if (next() == 'e' || next() == 'E') {
                p++
                if (next() == '+' || next() == '-') p++
                if (next()?.isDigit() != true) fail()
                while (next()?.isDigit() == true) p++
            }
            return Num(input.substring(start, p))
        }
    }
}
