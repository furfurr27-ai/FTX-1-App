package dev.n0png.fieldops.core.propagation

data class NoaaSwpcGlotecIndexSelection(
    val artifactFilename: String,
    val artifactUrl: String,
    val timestampToken: String,
)

object NoaaSwpcGlotecIndexSelector {
    private val canonicalFilename =
        Regex("""glotec_icao_(\d{8}T\d{6}Z)\.geojson""")

    fun selectLatest(indexJson: String): NoaaSwpcGlotecIndexSelection {
        require(indexJson.isNotBlank()) {
            "GloTEC index JSON must not be blank"
        }

        val values = JsonStringValueCollector.parse(indexJson)
        val candidateValues = values.filter {
            it.contains("glotec_icao_", ignoreCase = false)
        }
        require(candidateValues.isNotEmpty()) {
            "GloTEC index contains no canonical artifact candidates"
        }

        val candidates = candidateValues.map { raw ->
            val filename = when {
                canonicalFilename.matches(raw) -> raw
                raw.startsWith(NoaaSwpcGlotecAdapter.DIRECTORY_URL) -> {
                    val suffix = raw.removePrefix(NoaaSwpcGlotecAdapter.DIRECTORY_URL)
                    require(canonicalFilename.matches(suffix)) {
                        "GloTEC index contains a malformed official artifact URL: $raw"
                    }
                    suffix
                }
                else -> throw IllegalArgumentException(
                    "GloTEC index contains a non-official or malformed artifact reference: $raw"
                )
            }

            val match = requireNotNull(canonicalFilename.matchEntire(filename))
            NoaaSwpcGlotecIndexSelection(
                artifactFilename = filename,
                artifactUrl = NoaaSwpcGlotecAdapter.DIRECTORY_URL + filename,
                timestampToken = match.groupValues[1],
            )
        }.distinctBy { it.artifactUrl }

        return candidates.maxWithOrNull(
            compareBy<NoaaSwpcGlotecIndexSelection>(
                { it.timestampToken },
                { it.artifactFilename },
            )
        ) ?: throw IllegalArgumentException(
            "GloTEC index contains no usable canonical artifact"
        )
    }
}

private object JsonStringValueCollector {
    fun parse(input: String): List<String> =
        Parser(input).parse()

    private class Parser(
        private val input: String,
    ) {
        private var index = 0
        private val stringValues = mutableListOf<String>()

        fun parse(): List<String> {
            skipWhitespace()
            parseValue(collectString = true)
            skipWhitespace()
            require(index == input.length) {
                "Trailing JSON content at offset $index"
            }
            return stringValues.toList()
        }

        private fun parseValue(collectString: Boolean) {
            skipWhitespace()
            require(index < input.length) {
                "Unexpected end of JSON at offset $index"
            }
            when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> {
                    val value = parseString()
                    if (collectString) stringValues += value
                }
                't' -> consumeLiteral("true")
                'f' -> consumeLiteral("false")
                'n' -> consumeLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> fail("Unexpected JSON token")
            }
        }

        private fun parseObject() {
            expect('{')
            skipWhitespace()
            if (peek('}')) {
                index++
                return
            }

            val keys = mutableSetOf<String>()
            while (true) {
                skipWhitespace()
                require(peek('"')) {
                    "JSON object key must be a string at offset $index"
                }
                val key = parseString()
                require(keys.add(key)) {
                    "Duplicate JSON object key: $key"
                }
                skipWhitespace()
                expect(':')
                parseValue(collectString = true)
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek('}') -> {
                        index++
                        return
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun parseArray() {
            expect('[')
            skipWhitespace()
            if (peek(']')) {
                index++
                return
            }

            while (true) {
                parseValue(collectString = true)
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek(']') -> {
                        index++
                        return
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun parseNumber() {
            if (peek('-')) index++
            require(index < input.length) {
                "Incomplete JSON number at offset $index"
            }

            if (peek('0')) {
                index++
            } else {
                require(input[index] in '1'..'9') {
                    "Invalid JSON number at offset $index"
                }
                while (index < input.length && input[index].isDigit()) index++
            }

            if (peek('.')) {
                index++
                require(index < input.length && input[index].isDigit()) {
                    "Invalid JSON fraction at offset $index"
                }
                while (index < input.length && input[index].isDigit()) index++
            }

            if (index < input.length && (input[index] == 'e' || input[index] == 'E')) {
                index++
                if (index < input.length && (input[index] == '+' || input[index] == '-')) {
                    index++
                }
                require(index < input.length && input[index].isDigit()) {
                    "Invalid JSON exponent at offset $index"
                }
                while (index < input.length && input[index].isDigit()) index++
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < input.length) {
                val ch = input[index++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(index < input.length) {
                            "Incomplete JSON escape at offset $index"
                        }
                        when (val escaped = input[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= input.length) {
                                    "Incomplete JSON unicode escape at offset $index"
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
                        require(ch.code >= 0x20) {
                            "Control character in JSON string"
                        }
                        out.append(ch)
                    }
                }
            }
            fail("Unterminated JSON string")
        }

        private fun consumeLiteral(value: String) {
            require(input.regionMatches(index, value, 0, value.length)) {
                "Invalid JSON literal at offset $index"
            }
            index += value.length
        }

        private fun expect(ch: Char) {
            require(index < input.length && input[index] == ch) {
                "Expected '$ch' at offset $index"
            }
            index++
        }

        private fun peek(ch: Char): Boolean =
            index < input.length && input[index] == ch

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("$message at offset $index")
    }
}
