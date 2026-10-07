package dev.n0png.fieldops.core.propagation

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate
import java.time.Instant
import java.time.format.DateTimeParseException

data class NoaaSwpcGlotecRecord(
    val product: IonosphericMapProduct,
    val providerTimeTag: String,
    val cadenceMinutes: Int,
    val tecUnits: String,
    val featureCount: Int,
) {
    init {
        require(providerTimeTag.isNotBlank()) { "GloTEC provider time_tag must not be blank" }
        require(cadenceMinutes > 0) { "GloTEC cadence must be positive" }
        require(tecUnits.isNotBlank()) { "GloTEC TEC units must not be blank" }
        require(featureCount > 0) { "GloTEC feature count must be positive" }
    }
}

object NoaaSwpcGlotecAdapter {
    const val DIRECTORY_URL =
        "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/"
    const val INDEX_URL =
        "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt.json"
    const val PRODUCT_INFO_URL =
        "https://www.spaceweather.gov/index.php/products/glotec"
    const val SOURCE_VERSION = "glotec-operational-geojson-v1"
    const val EXPECTED_CADENCE_MINUTES = 10
    const val TEC_UNITS = "TECU"

    private val artifactName =
        Regex("""glotec_icao_(\d{8}T\d{6}Z)\.geojson""")

    fun parseGeoJson(
        json: String,
        retrievedAtUtcMillis: Long,
        sourceUrl: String,
    ): NoaaSwpcGlotecRecord {
        require(retrievedAtUtcMillis >= 0) { "GloTEC retrieval UTC must be non-negative" }
        val artifactTimestamp = validateArtifactUrl(sourceUrl)
        val root = GlotecJson.parse(json).requiredObject("root")

        root.requireExactKeys(
            setOf("type", "time_tag", "cadence", "metadata", "features"),
            "GloTEC root",
        )
        require(root.requiredString("type") == "FeatureCollection") {
            "GloTEC root type must be FeatureCollection"
        }

        val timeTag = root.requiredString("time_tag")
        val observedAt = parseProviderUtc(timeTag)
        require(observedAt == artifactTimestamp) {
            "GloTEC source filename timestamp must match provider time_tag"
        }

        val cadence = root.requiredInteger("cadence")
        require(cadence == EXPECTED_CADENCE_MINUTES) {
            "GloTEC cadence drift: expected $EXPECTED_CADENCE_MINUTES minutes"
        }

        val metadata = root.requiredObject("metadata")
        metadata.requireExactKeys(setOf("variables"), "GloTEC metadata")
        val variables = metadata.requiredObject("variables")
        require("tec" in variables.fields) { "GloTEC metadata must define TEC" }

        val tecMetadata = variables.requiredObject("tec")
        tecMetadata.requireExactKeys(setOf("units", "min", "max"), "GloTEC TEC metadata")
        val units = tecMetadata.requiredString("units")
        require(units == TEC_UNITS) { "GloTEC TEC units must be TECU" }
        val metadataMin = tecMetadata.requiredNumber("min")
        val metadataMax = tecMetadata.requiredNumber("max")
        require(metadataMin >= 0.0 && metadataMin.isFinite()) {
            "GloTEC TEC metadata minimum must be finite and non-negative"
        }
        require(metadataMax > metadataMin && metadataMax.isFinite()) {
            "GloTEC TEC metadata maximum must exceed minimum"
        }

        val features = root.requiredArray("features").values
        require(features.isNotEmpty()) { "GloTEC GeoJSON must contain features" }

        val samples = features.mapIndexed { index, featureValue ->
            parseFeature(
                value = featureValue,
                index = index,
                sourceReference = "NOAA_SWPC_GLOTEC:$timeTag",
                metadataMin = metadataMin,
                metadataMax = metadataMax,
            )
        }

        require(samples.map { it.position.stableKey }.distinct().size == samples.size) {
            "GloTEC GeoJSON must not contain duplicate grid coordinates"
        }

        val coordinates = samples.map { requireNotNull(it.position.coordinate) }
        val west = coordinates.minOf { it.longitude }
        val east = coordinates.maxOf { it.longitude }
        val south = coordinates.minOf { it.latitude }
        val north = coordinates.maxOf { it.latitude }
        require(west < east && south < north) {
            "GloTEC GeoJSON requires at least two distinct longitude and latitude values"
        }

        val product = IonosphericMapProduct(
            evidenceId = "NOAA_SWPC_GLOTEC_VTEC:$timeTag",
            source = PropagationSourceRef(
                sourceId = "NOAA_SWPC_GLOTEC_VTEC",
                providerName = "NOAA SWPC GloTEC",
                sourceClass = PropagationSourceClass.DERIVED_PRODUCT,
                sourceVersion = SOURCE_VERSION,
                retrievedAtUtcMillis = retrievedAtUtcMillis,
                sourceUrl = sourceUrl,
            ),
            observedAtUtcMillis = observedAt,
            confidence = PropagationConfidence(
                value = 0.70,
                basis = PropagationConfidenceBasis.DERIVED,
                explanation =
                    "FieldOps confidence policy for NOAA GloTEC assimilative VTEC; " +
                        "per-cell provider quality_flag is preserved separately.",
            ),
            quality = setOf(PropagationDataQuality.ESTIMATED),
            coverage = BoundsPropagationCoverage(
                GeoBounds(
                    west = west,
                    south = south,
                    east = east,
                    north = north,
                )
            ),
            metric = IonosphericMetric.VTEC_TECU,
            samples = samples,
            referenceDistanceKm = null,
            generatedAtUtcMillis = null,
        )

        return NoaaSwpcGlotecRecord(
            product = product,
            providerTimeTag = timeTag,
            cadenceMinutes = cadence,
            tecUnits = units,
            featureCount = samples.size,
        )
    }

    private fun parseFeature(
        value: GlotecJsonValue,
        index: Int,
        sourceReference: String,
        metadataMin: Double,
        metadataMax: Double,
    ): IonosphericSample {
        val feature = value.requiredObject("GloTEC feature[$index]")
        feature.requireExactKeys(
            setOf("type", "geometry", "properties"),
            "GloTEC feature[$index]",
        )
        require(feature.requiredString("type") == "Feature") {
            "GloTEC feature[$index] type must be Feature"
        }

        val geometry = feature.requiredObject("geometry")
        geometry.requireExactKeys(
            setOf("type", "coordinates"),
            "GloTEC feature[$index] geometry",
        )
        require(geometry.requiredString("type") == "Point") {
            "GloTEC feature[$index] geometry must be Point"
        }
        val coordinates = geometry.requiredArray("coordinates").values
        require(coordinates.size == 2) {
            "GloTEC feature[$index] coordinates must contain longitude and latitude"
        }
        val longitude = coordinates[0].requiredNumber("GloTEC feature[$index] longitude")
        val latitude = coordinates[1].requiredNumber("GloTEC feature[$index] latitude")

        val properties = feature.requiredObject("properties")
        properties.requireExactKeys(
            setOf("tec", "anomaly", "hmF2", "NmF2", "quality_flag"),
            "GloTEC feature[$index] properties",
        )
        val tec = properties.requiredNumber("tec")
        require(tec > 0.0 && tec.isFinite()) {
            "GloTEC feature[$index] TEC must be finite and positive"
        }
        require(tec in metadataMin..metadataMax) {
            "GloTEC feature[$index] TEC must fall inside provider metadata bounds"
        }

        properties.requiredNumber("anomaly")
        properties.requiredNumber("hmF2")
        properties.requiredNumber("NmF2")

        val qualityFlag = properties.requiredInteger("quality_flag")
        require(qualityFlag in 0..5) {
            "GloTEC feature[$index] quality_flag must be between 0 and 5"
        }

        return IonosphericSample(
            position = PropagationPosition(
                coordinate = GeoCoordinate(
                    longitude = longitude,
                    latitude = latitude,
                ),
                method = PropagationLocationMethod.PROVIDER_COORDINATE,
                sourceReference = sourceReference,
            ),
            value = tec,
            confidence = null,
            providerQualityCode = qualityFlag,
            providerQualityExplanation =
                "NOAA GloTEC quality_flag preserves the provider's rounded/capped " +
                    "mean F-region observation count for the vertical profile.",
        )
    }

    private fun validateArtifactUrl(url: String): Long {
        require(url.startsWith(DIRECTORY_URL)) {
            "GloTEC artifact URL must use the pinned NOAA SWPC directory"
        }
        val name = url.removePrefix(DIRECTORY_URL)
        require('/' !in name && '?' !in name && '#' !in name) {
            "GloTEC artifact URL must identify one canonical GeoJSON file"
        }
        val match = artifactName.matchEntire(name)
            ?: throw IllegalArgumentException("GloTEC artifact filename is not canonical")
        return parseArtifactUtc(match.groupValues[1])
    }

    private fun parseArtifactUtc(raw: String): Long {
        val iso =
            raw.substring(0, 4) + "-" +
                raw.substring(4, 6) + "-" +
                raw.substring(6, 8) + "T" +
                raw.substring(9, 11) + ":" +
                raw.substring(11, 13) + ":" +
                raw.substring(13, 15) + "Z"
        return parseProviderUtc(iso)
    }

    private fun parseProviderUtc(raw: String): Long {
        require(raw.isNotBlank()) { "GloTEC provider time_tag must not be blank" }
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(
                "Invalid GloTEC provider UTC timestamp: $raw",
                e,
            )
        }
    }
}

private sealed interface GlotecJsonValue {
    data class ObjectValue(
        val fields: Map<String, GlotecJsonValue>,
    ) : GlotecJsonValue

    data class ArrayValue(
        val values: List<GlotecJsonValue>,
    ) : GlotecJsonValue

    data class StringValue(
        val value: String,
    ) : GlotecJsonValue

    data class NumberValue(
        val value: Double,
        val raw: String,
    ) : GlotecJsonValue

    data class BooleanValue(
        val value: Boolean,
    ) : GlotecJsonValue

    data object NullValue : GlotecJsonValue
}

private object GlotecJson {
    fun parse(json: String): GlotecJsonValue = Parser(json).parse()

    private class Parser(
        private val input: String,
    ) {
        private var index = 0

        fun parse(): GlotecJsonValue {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(index == input.length) {
                "Trailing JSON content at offset $index"
            }
            return value
        }

        private fun parseValue(): GlotecJsonValue = when {
            peek('{') -> parseObject()
            peek('[') -> parseArray()
            peek('"') -> GlotecJsonValue.StringValue(parseString())
            matches("true") -> {
                index += 4
                GlotecJsonValue.BooleanValue(true)
            }
            matches("false") -> {
                index += 5
                GlotecJsonValue.BooleanValue(false)
            }
            matches("null") -> {
                index += 4
                GlotecJsonValue.NullValue
            }
            else -> parseNumber()
        }

        private fun parseObject(): GlotecJsonValue.ObjectValue {
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, GlotecJsonValue>()
            if (peek('}')) {
                index++
                return GlotecJsonValue.ObjectValue(fields)
            }

            while (true) {
                require(peek('"')) { "Expected JSON object key at offset $index" }
                val key = parseString()
                require(key !in fields) { "Duplicate JSON key: $key" }
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek('}') -> {
                        index++
                        return GlotecJsonValue.ObjectValue(fields)
                    }
                    else -> fail("Expected comma or closing object brace")
                }
            }
        }

        private fun parseArray(): GlotecJsonValue.ArrayValue {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<GlotecJsonValue>()
            if (peek(']')) {
                index++
                return GlotecJsonValue.ArrayValue(values)
            }

            while (true) {
                values += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek(']') -> {
                        index++
                        return GlotecJsonValue.ArrayValue(values)
                    }
                    else -> fail("Expected comma or closing array bracket")
                }
            }
        }

        private fun parseNumber(): GlotecJsonValue.NumberValue {
            val start = index
            if (peek('-')) index++
            require(index < input.length && input[index].isDigit()) {
                "Expected JSON number at offset $index"
            }
            if (peek('0')) {
                index++
            } else {
                while (index < input.length && input[index].isDigit()) index++
            }
            if (peek('.')) {
                index++
                val fractionStart = index
                while (index < input.length && input[index].isDigit()) index++
                require(index > fractionStart) { "Malformed JSON fraction" }
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                val exponentStart = index
                while (index < input.length && input[index].isDigit()) index++
                require(index > exponentStart) { "Malformed JSON exponent" }
            }

            val raw = input.substring(start, index)
            val value = raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid JSON number: $raw")
            require(value.isFinite()) { "JSON number must be finite" }
            return GlotecJsonValue.NumberValue(value, raw)
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < input.length) {
                val ch = input[index++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(index < input.length) { "Unterminated JSON escape" }
                        when (val escaped = input[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= input.length) {
                                    "Incomplete JSON unicode escape"
                                }
                                val hex = input.substring(index, index + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException(
                                        "Invalid JSON unicode escape: $hex"
                                    )
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> fail("Unsupported JSON escape: $escaped")
                        }
                    }
                    else -> {
                        require(ch.code >= 0x20) { "Control character in JSON string" }
                        out.append(ch)
                    }
                }
            }
            fail("Unterminated JSON string")
        }

        private fun expect(ch: Char) {
            require(index < input.length && input[index] == ch) {
                "Expected '$ch' at offset $index"
            }
            index++
        }

        private fun peek(ch: Char): Boolean =
            index < input.length && input[index] == ch

        private fun matches(value: String): Boolean =
            input.regionMatches(index, value, 0, value.length)

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("$message at offset $index")
    }
}

private fun GlotecJsonValue.requiredObject(label: String): GlotecJsonValue.ObjectValue =
    this as? GlotecJsonValue.ObjectValue
        ?: throw IllegalArgumentException("$label must be a JSON object")

private fun GlotecJsonValue.requiredNumber(label: String): Double =
    when (this) {
        is GlotecJsonValue.NumberValue -> value
        else -> throw IllegalArgumentException("$label must be numeric")
    }

private fun GlotecJsonValue.ObjectValue.requireExactKeys(
    expected: Set<String>,
    label: String,
) {
    require(fields.keys == expected) {
        "$label schema drift fields=" + fields.keys.sorted()
    }
}

private fun GlotecJsonValue.ObjectValue.requiredString(key: String): String =
    when (val value = fields[key]) {
        is GlotecJsonValue.StringValue -> value.value
        null -> throw IllegalArgumentException("Missing required JSON field: $key")
        else -> throw IllegalArgumentException("JSON field must be a string: $key")
    }

private fun GlotecJsonValue.ObjectValue.requiredObject(key: String): GlotecJsonValue.ObjectValue =
    when (val value = fields[key]) {
        is GlotecJsonValue.ObjectValue -> value
        null -> throw IllegalArgumentException("Missing required JSON field: $key")
        else -> throw IllegalArgumentException("JSON field must be an object: $key")
    }

private fun GlotecJsonValue.ObjectValue.requiredArray(key: String): GlotecJsonValue.ArrayValue =
    when (val value = fields[key]) {
        is GlotecJsonValue.ArrayValue -> value
        null -> throw IllegalArgumentException("Missing required JSON field: $key")
        else -> throw IllegalArgumentException("JSON field must be an array: $key")
    }

private fun GlotecJsonValue.ObjectValue.requiredNumber(key: String): Double =
    when (val value = fields[key]) {
        is GlotecJsonValue.NumberValue -> value.value
        null -> throw IllegalArgumentException("Missing required JSON field: $key")
        else -> throw IllegalArgumentException("JSON field must be numeric: $key")
    }

private fun GlotecJsonValue.ObjectValue.requiredInteger(key: String): Int =
    when (val value = fields[key]) {
        is GlotecJsonValue.NumberValue -> {
            require(!value.raw.contains('.') && !value.raw.contains('e', ignoreCase = true)) {
                "JSON field must be an integer: $key"
            }
            value.raw.toIntOrNull()
                ?: throw IllegalArgumentException("JSON integer out of range: $key")
        }
        null -> throw IllegalArgumentException("Missing required JSON field: $key")
        else -> throw IllegalArgumentException("JSON field must be numeric: $key")
    }
