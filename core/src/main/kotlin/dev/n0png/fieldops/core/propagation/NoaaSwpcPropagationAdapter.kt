package dev.n0png.fieldops.core.propagation

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

enum class NoaaSwpcRecordStatus {
    OBSERVED,
    ESTIMATED,
    PREDICTED,
}

data class NoaaSwpcSolarGeomagneticRecord(
    val status: NoaaSwpcRecordStatus,
    val observation: SolarGeomagneticObservation,
    val noaaScale: String? = null,
    val stationCount: Int? = null,
    val runningA: Double? = null,
) {
    init {
        noaaScale?.let {
            require(it.matches(Regex("""G[1-5]"""))) {
                "NOAA geomagnetic scale must be G1 through G5 when present"
            }
        }
        stationCount?.let {
            require(it > 0) { "NOAA station count must be positive when present" }
        }
        runningA?.let {
            require(it >= 0.0 && it.isFinite()) {
                "NOAA running-a value must be finite and non-negative when present"
            }
        }
    }
}

object NoaaSwpcPropagationAdapter {
    const val PLANETARY_KP_URL =
        "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    const val PLANETARY_KP_FORECAST_URL =
        "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
    const val F107_SUMMARY_URL =
        "https://services.swpc.noaa.gov/products/summary/10cm-flux.json"
    const val SERVICE_CHANGE_NOTICE_URL =
        "https://www.weather.gov/media/notification/pdf_2026/scn26-21_Data_Format_Changes_Impacting_SWPC_Products.pdf"

    const val SOURCE_VERSION = "swpc-json-post-scn26-21-v1"

    fun parsePlanetaryKp(
        json: String,
        retrievedAtUtcMillis: Long,
    ): List<NoaaSwpcSolarGeomagneticRecord> {
        require(retrievedAtUtcMillis >= 0) { "NOAA retrieval UTC must be non-negative" }
        return FlatJson.parseArrayOfObjects(json).map { row ->
            val timeTag = row.requiredString("time_tag")
            val kp = row.requiredNumber("Kp")
            val runningA = row.requiredNumber("a_running")
            val stationCount = row.requiredInteger("station_count")
            require(row.keys == setOf("time_tag", "Kp", "a_running", "station_count")) {
                "Unexpected NOAA planetary-Kp fields: " + row.keys.sorted()
            }

            val timestamp = parseProviderUtc(timeTag)
            NoaaSwpcSolarGeomagneticRecord(
                status = NoaaSwpcRecordStatus.OBSERVED,
                observation = SolarGeomagneticObservation(
                    evidenceId = "NOAA_SWPC_KP_OBSERVED:" + timeTag,
                    source = sourceRef(
                        id = "NOAA_SWPC_PLANETARY_KP",
                        providerName = "NOAA SWPC Planetary K-index",
                        sourceClass = PropagationSourceClass.DERIVED_PRODUCT,
                        retrievedAtUtcMillis = retrievedAtUtcMillis,
                        sourceUrl = PLANETARY_KP_URL,
                    ),
                    observedAtUtcMillis = timestamp,
                    confidence = providerConfidence(
                        value = 0.90,
                        explanation = "NOAA SWPC provider-reported planetary Kp observation",
                    ),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    planetaryKp = kp,
                ),
                stationCount = stationCount,
                runningA = runningA,
            )
        }
    }

    fun parsePlanetaryKpForecast(
        json: String,
        retrievedAtUtcMillis: Long,
    ): List<NoaaSwpcSolarGeomagneticRecord> {
        require(retrievedAtUtcMillis >= 0) { "NOAA retrieval UTC must be non-negative" }
        return FlatJson.parseArrayOfObjects(json).map { row ->
            val timeTag = row.requiredString("time_tag")
            val kp = row.requiredNumber("kp")
            val rawStatus = row.requiredString("observed")
            val noaaScale = row.optionalString("noaa_scale")
            require(row.keys == setOf("time_tag", "kp", "observed", "noaa_scale")) {
                "Unexpected NOAA planetary-Kp forecast fields: " + row.keys.sorted()
            }

            val status = when (rawStatus.lowercase()) {
                "observed" -> NoaaSwpcRecordStatus.OBSERVED
                "estimated" -> NoaaSwpcRecordStatus.ESTIMATED
                "predicted" -> NoaaSwpcRecordStatus.PREDICTED
                else -> throw IllegalArgumentException(
                    "Unknown NOAA planetary-Kp record status: " + rawStatus
                )
            }
            val sourceClass = when (status) {
                NoaaSwpcRecordStatus.OBSERVED,
                NoaaSwpcRecordStatus.ESTIMATED,
                -> PropagationSourceClass.DERIVED_PRODUCT
                NoaaSwpcRecordStatus.PREDICTED -> PropagationSourceClass.FORECAST
            }
            val quality = when (status) {
                NoaaSwpcRecordStatus.OBSERVED ->
                    setOf(PropagationDataQuality.PROVISIONAL)
                NoaaSwpcRecordStatus.ESTIMATED ->
                    setOf(
                        PropagationDataQuality.ESTIMATED,
                        PropagationDataQuality.PROVISIONAL,
                    )
                NoaaSwpcRecordStatus.PREDICTED ->
                    setOf(PropagationDataQuality.ESTIMATED)
            }
            val confidence = when (status) {
                NoaaSwpcRecordStatus.OBSERVED -> 0.90
                NoaaSwpcRecordStatus.ESTIMATED -> 0.75
                NoaaSwpcRecordStatus.PREDICTED -> 0.65
            }
            val time = parseProviderUtc(timeTag)

            NoaaSwpcSolarGeomagneticRecord(
                status = status,
                observation = SolarGeomagneticObservation(
                    evidenceId = "NOAA_SWPC_KP_" + status.name + ":" + timeTag,
                    source = sourceRef(
                        id = "NOAA_SWPC_KP_FORECAST_" + status.name,
                        providerName = "NOAA SWPC Planetary K-index forecast",
                        sourceClass = sourceClass,
                        retrievedAtUtcMillis = retrievedAtUtcMillis,
                        sourceUrl = PLANETARY_KP_FORECAST_URL,
                    ),
                    observedAtUtcMillis = time,
                    confidence = providerConfidence(
                        value = confidence,
                        explanation =
                            "NOAA SWPC provider-reported planetary Kp " +
                                rawStatus.lowercase() + " record",
                    ),
                    quality = quality,
                    planetaryKp = kp,
                ),
                noaaScale = noaaScale,
            )
        }
    }

    fun parseF107Summary(
        json: String,
        retrievedAtUtcMillis: Long,
    ): List<NoaaSwpcSolarGeomagneticRecord> {
        require(retrievedAtUtcMillis >= 0) { "NOAA retrieval UTC must be non-negative" }
        return FlatJson.parseArrayOfObjects(json).map { row ->
            val flux = row.requiredNumber("flux")
            val timeTag = row.requiredString("time_tag")
            require(row.keys == setOf("flux", "time_tag")) {
                "Unexpected NOAA F10.7 summary fields: " + row.keys.sorted()
            }

            NoaaSwpcSolarGeomagneticRecord(
                status = NoaaSwpcRecordStatus.OBSERVED,
                observation = SolarGeomagneticObservation(
                    evidenceId = "NOAA_SWPC_F107_OBSERVED:" + timeTag,
                    source = sourceRef(
                        id = "NOAA_SWPC_F107_SUMMARY",
                        providerName = "NOAA SWPC 10.7 cm solar radio flux",
                        sourceClass = PropagationSourceClass.MEASUREMENT,
                        retrievedAtUtcMillis = retrievedAtUtcMillis,
                        sourceUrl = F107_SUMMARY_URL,
                    ),
                    observedAtUtcMillis = parseProviderUtc(timeTag),
                    confidence = providerConfidence(
                        value = 0.90,
                        explanation = "NOAA SWPC provider-reported 10.7 cm solar radio flux observation",
                    ),
                    quality = setOf(PropagationDataQuality.PROVISIONAL),
                    f107SolarFluxSfu = flux,
                ),
            )
        }
    }

    private fun sourceRef(
        id: String,
        providerName: String,
        sourceClass: PropagationSourceClass,
        retrievedAtUtcMillis: Long,
        sourceUrl: String,
    ) = PropagationSourceRef(
        sourceId = id,
        providerName = providerName,
        sourceClass = sourceClass,
        sourceVersion = SOURCE_VERSION,
        retrievedAtUtcMillis = retrievedAtUtcMillis,
        sourceUrl = sourceUrl,
    )

    private fun providerConfidence(
        value: Double,
        explanation: String,
    ) = PropagationConfidence(
        value = value,
        basis = PropagationConfidenceBasis.PROVIDER_REPORTED,
        explanation = explanation,
    )

    private fun parseProviderUtc(raw: String): Long {
        require(raw.isNotBlank()) { "NOAA provider timestamp must not be blank" }
        return try {
            if (raw.endsWith("Z")) {
                Instant.parse(raw).toEpochMilli()
            } else {
                LocalDateTime.parse(raw)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid NOAA provider UTC timestamp: " + raw, e)
        }
    }
}

private sealed interface FlatJsonValue {
    data class Text(val value: String) : FlatJsonValue
    data class NumberValue(val value: Double, val raw: String) : FlatJsonValue
    data class BooleanValue(val value: Boolean) : FlatJsonValue
    data object NullValue : FlatJsonValue
}

private object FlatJson {
    fun parseArrayOfObjects(json: String): List<Map<String, FlatJsonValue>> =
        Parser(json).parseArrayOfObjects()

    private class Parser(
        private val input: String,
    ) {
        private var index = 0

        fun parseArrayOfObjects(): List<Map<String, FlatJsonValue>> {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Map<String, FlatJsonValue>>()
            if (peek(']')) {
                index++
                finish()
                return result
            }

            while (true) {
                result += parseObject()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek(']') -> {
                        index++
                        finish()
                        return result
                    }
                    else -> fail("Expected comma or closing array bracket")
                }
            }
        }

        private fun parseObject(): Map<String, FlatJsonValue> {
            skipWhitespace()
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, FlatJsonValue>()
            if (peek('}')) {
                index++
                return fields
            }

            while (true) {
                val key = parseString()
                require(!fields.containsKey(key)) {
                    "Duplicate JSON key: " + key
                }
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = parseScalar()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek('}') -> {
                        index++
                        return fields
                    }
                    else -> fail("Expected comma or closing object brace")
                }
            }
        }

        private fun parseScalar(): FlatJsonValue = when {
            peek('"') -> FlatJsonValue.Text(parseString())
            matches("null") -> {
                index += 4
                FlatJsonValue.NullValue
            }
            matches("true") -> {
                index += 4
                FlatJsonValue.BooleanValue(true)
            }
            matches("false") -> {
                index += 5
                FlatJsonValue.BooleanValue(false)
            }
            else -> parseNumber()
        }

        private fun parseNumber(): FlatJsonValue.NumberValue {
            val start = index
            if (peek('-')) index++
            require(index < input.length && input[index].isDigit()) {
                "Expected JSON number at offset " + index
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
                ?: throw IllegalArgumentException("Invalid JSON number: " + raw)
            require(value.isFinite()) { "JSON number must be finite" }
            return FlatJsonValue.NumberValue(value, raw)
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
                                        "Invalid JSON unicode escape: " + hex
                                    )
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> fail("Unsupported JSON escape: " + escaped)
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

        private fun finish() {
            skipWhitespace()
            require(index == input.length) {
                "Trailing JSON content at offset " + index
            }
        }

        private fun expect(ch: Char) {
            require(index < input.length && input[index] == ch) {
                "Expected '" + ch + "' at offset " + index
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
            throw IllegalArgumentException(message + " at offset " + index)
    }
}

private fun Map<String, FlatJsonValue>.requiredString(key: String): String =
    when (val value = this[key]) {
        is FlatJsonValue.Text -> value.value
        null -> throw IllegalArgumentException("Missing required JSON field: " + key)
        else -> throw IllegalArgumentException("JSON field must be a string: " + key)
    }

private fun Map<String, FlatJsonValue>.optionalString(key: String): String? =
    when (val value = this[key]) {
        is FlatJsonValue.Text -> value.value
        FlatJsonValue.NullValue -> null
        null -> throw IllegalArgumentException("Missing required JSON field: " + key)
        else -> throw IllegalArgumentException(
            "JSON field must be a string or null: " + key
        )
    }

private fun Map<String, FlatJsonValue>.requiredNumber(key: String): Double =
    when (val value = this[key]) {
        is FlatJsonValue.NumberValue -> value.value
        null -> throw IllegalArgumentException("Missing required JSON field: " + key)
        else -> throw IllegalArgumentException("JSON field must be numeric: " + key)
    }

private fun Map<String, FlatJsonValue>.requiredInteger(key: String): Int =
    when (val value = this[key]) {
        is FlatJsonValue.NumberValue -> {
            require(!value.raw.contains('.') && !value.raw.contains('e', true)) {
                "JSON field must be an integer: " + key
            }
            value.raw.toIntOrNull()
                ?: throw IllegalArgumentException("JSON integer out of range: " + key)
        }
        null -> throw IllegalArgumentException("Missing required JSON field: " + key)
        else -> throw IllegalArgumentException("JSON field must be numeric: " + key)
    }
