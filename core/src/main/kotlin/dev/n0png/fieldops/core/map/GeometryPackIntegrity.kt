package dev.n0png.fieldops.core.map

import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Canonical integrity serializer shared by production tests/checks.
 *
 * The Python CP-0007C builder uses the same format when generating
 * per-feature and overall pack SHA-256 values.
 */
object GeometryPackIntegrity {
    fun featureCanonical(record: OfflineGeometryPackRecord): String {
        val state = record.targetValue.trim().uppercase()
        require(state.matches(Regex("""[A-Z]{2}"""))) {
            "Canonical state geometry requires a two-letter state code"
        }
        val geometry = record.geometry as? MultiPolygonGeometry
            ?: error("Production U.S. state geometry must be multi-polygon geometry")

        return buildString {
            append("STATE\t")
            append(state)
            append('\n')
            geometry.polygons.forEachIndexed { index, polygon ->
                append("POLYGON\t")
                append(index)
                append('\t')
                append("O:")
                append(ringCanonical(polygon.outer))
                polygon.holes.forEach { hole ->
                    append("|H:")
                    append(ringCanonical(hole))
                }
                append('\n')
            }
        }
    }

    fun featureSha256(record: OfflineGeometryPackRecord): String =
        textSha256(featureCanonical(record))

    fun packCanonical(records: Iterable<OfflineGeometryPackRecord>): String =
        records
            .sortedBy { it.targetValue.trim().uppercase() }
            .joinToString(separator = "") { featureCanonical(it) }

    fun packSha256(records: Iterable<OfflineGeometryPackRecord>): String =
        textSha256(packCanonical(records))

    fun textSha256(text: String): String =
        sha256(text)

    fun maxLongitudeJump(record: OfflineGeometryPackRecord): Double {
        val geometry = record.geometry as? MultiPolygonGeometry
            ?: error("Antimeridian inspection requires multi-polygon geometry")
        return geometry.polygons.maxOf { polygon ->
            sequenceOf(polygon.outer)
                .plus(polygon.holes.asSequence())
                .maxOf(::maxLongitudeJump)
        }
    }

    fun hasPositiveLongitude(record: OfflineGeometryPackRecord): Boolean =
        coordinates(record).any { it.longitude > 0.0 }

    fun hasNegativeLongitude(record: OfflineGeometryPackRecord): Boolean =
        coordinates(record).any { it.longitude < 0.0 }

    private fun coordinates(record: OfflineGeometryPackRecord): Sequence<GeoCoordinate> {
        val geometry = record.geometry as? MultiPolygonGeometry
            ?: error("Coordinate inspection requires multi-polygon geometry")
        return geometry.polygons.asSequence().flatMap { polygon ->
            sequenceOf(polygon.outer)
                .plus(polygon.holes.asSequence())
                .flatMap { it.points.asSequence() }
        }
    }

    private fun maxLongitudeJump(ring: GeoLinearRing): Double =
        ring.points
            .zipWithNext()
            .maxOfOrNull { (a, b) -> kotlin.math.abs(b.longitude - a.longitude) }
            ?: 0.0

    private fun ringCanonical(ring: GeoLinearRing): String =
        ring.points.joinToString(separator = ";") { point ->
            canonicalDouble(point.longitude) + "," + canonicalDouble(point.latitude)
        }

    private fun canonicalDouble(value: Double): String {
        require(value.isFinite()) { "Canonical geometry requires finite coordinates" }
        if (value == 0.0) return "0"
        return BigDecimal.valueOf(value)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}
