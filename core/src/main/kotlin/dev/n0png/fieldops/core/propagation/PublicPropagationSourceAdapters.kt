package dev.n0png.fieldops.core.propagation

import java.nio.charset.StandardCharsets

enum class PskReporterQuerySelector(
    val parameterName: String,
) {
    SENDER("senderCallsign"),
    RECEIVER("receiverCallsign"),
    EITHER("callsign"),
}

data class PskReporterPublicQuery(
    val callsign: String,
    val selector: PskReporterQuerySelector = PskReporterQuerySelector.EITHER,
    val lookbackSeconds: Int = 60 * 60,
    val reportLimit: Int = 500,
    val mode: String? = null,
    val minimumFrequencyHz: Long? = null,
    val maximumFrequencyHz: Long? = null,
) {
    init {
        require(callsign.isNotBlank()) {
            "PSK Reporter callsign must not be blank"
        }
        require(callsign.length <= 32) {
            "PSK Reporter callsign is unexpectedly long"
        }
        require(callsign.all { it.isLetterOrDigit() || it == '/' || it == '-' }) {
            "PSK Reporter callsign contains unsupported query characters"
        }
        require(lookbackSeconds in 60..86_400) {
            "PSK Reporter lookback must be between 60 seconds and 24 hours"
        }
        require(reportLimit in 1..5_000) {
            "PSK Reporter report limit must be in 1..5000"
        }
        mode?.let {
            require(it.isNotBlank()) {
                "PSK Reporter mode must not be blank when supplied"
            }
            require(it.length <= 32) {
                "PSK Reporter mode is unexpectedly long"
            }
        }
        require(
            (minimumFrequencyHz == null) == (maximumFrequencyHz == null)
        ) {
            "PSK Reporter frequency range requires both minimum and maximum"
        }
        if (minimumFrequencyHz != null && maximumFrequencyHz != null) {
            require(minimumFrequencyHz > 0) {
                "PSK Reporter minimum frequency must be positive"
            }
            require(maximumFrequencyHz >= minimumFrequencyHz) {
                "PSK Reporter maximum frequency must not be below minimum"
            }
        }
    }

    fun url(): String {
        val parameters = mutableListOf<Pair<String, String>>()
        parameters += selector.parameterName to callsign.trim().uppercase()
        parameters += "flowStartSeconds" to (-lookbackSeconds).toString()
        parameters += "rptlimit" to reportLimit.toString()
        parameters += "rronly" to "1"
        parameters += "noactive" to "1"
        mode?.let {
            parameters += "mode" to it.trim().uppercase()
        }
        if (minimumFrequencyHz != null && maximumFrequencyHz != null) {
            parameters += "frange" to "$minimumFrequencyHz-$maximumFrequencyHz"
        }

        return PskReporterHeardPathAdapter.QUERY_URL +
            "?" +
            parameters.joinToString("&") { (name, value) ->
                percentEncode(name) + "=" + percentEncode(value)
            }
    }

    private fun percentEncode(value: String): String =
        buildString {
            value.toByteArray(StandardCharsets.UTF_8).forEach { raw ->
                val byte = raw.toInt() and 0xFF
                val ch = byte.toChar()
                if (
                    ch in 'A'..'Z' ||
                    ch in 'a'..'z' ||
                    ch in '0'..'9' ||
                    ch == '-' ||
                    ch == '_' ||
                    ch == '.' ||
                    ch == '~'
                ) {
                    append(ch)
                } else {
                    append('%')
                    append(HEX[byte ushr 4])
                    append(HEX[byte and 0x0F])
                }
            }
        }

    private companion object {
        const val HEX = "0123456789ABCDEF"
    }
}

object PublicPropagationSourceAdapters {
    const val NOAA_JSON_MAX_BYTES = 2 * 1024 * 1024
    const val GLOTEC_INDEX_MAX_BYTES = 2 * 1024 * 1024
    const val GLOTEC_ARTIFACT_MAX_BYTES = 8 * 1024 * 1024
    const val PSK_REPORTER_XML_MAX_BYTES = 4 * 1024 * 1024

    private val JSON_TYPES =
        setOf("application/json", "text/json", "text/plain")
    private val GEOJSON_TYPES =
        setOf(
            "application/geo+json",
            "application/json",
            "text/json",
            "text/plain",
        )
    private val XML_TYPES =
        setOf("application/xml", "text/xml")

    fun noaaPlanetaryKp(
        transport: PublicPropagationTransport,
        policy: PropagationRefreshPolicy,
    ): PropagationRefreshSourceDefinition =
        PropagationRefreshSourceDefinition(
            sourceKey = "NOAA_SWPC_PLANETARY_KP",
            role = PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
            policy = policy,
            fetcher = PropagationSourceFetcher { retrievedAtUtcMillis ->
                fetchAndParse(
                    transport = transport,
                    request = request(
                        NoaaSwpcPropagationAdapter.PLANETARY_KP_URL,
                        NOAA_JSON_MAX_BYTES,
                        JSON_TYPES,
                    ),
                ) { body, _ ->
                    PropagationAggregationInput(
                        solarGeomagnetic =
                            NoaaSwpcPropagationAdapter
                                .parsePlanetaryKp(body, retrievedAtUtcMillis)
                                .map { it.observation },
                    )
                }
            },
        )

    fun noaaPlanetaryKpForecast(
        transport: PublicPropagationTransport,
        policy: PropagationRefreshPolicy,
    ): PropagationRefreshSourceDefinition =
        PropagationRefreshSourceDefinition(
            sourceKey = "NOAA_SWPC_KP_FORECAST",
            role = PropagationRefreshSourceRole.NOAA_KP_FORECAST,
            policy = policy,
            fetcher = PropagationSourceFetcher { retrievedAtUtcMillis ->
                fetchAndParse(
                    transport = transport,
                    request = request(
                        NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL,
                        NOAA_JSON_MAX_BYTES,
                        JSON_TYPES,
                    ),
                ) { body, _ ->
                    PropagationAggregationInput(
                        solarGeomagnetic =
                            NoaaSwpcPropagationAdapter
                                .parsePlanetaryKpForecast(body, retrievedAtUtcMillis)
                                .map { it.observation },
                    )
                }
            },
        )

    fun noaaF107(
        transport: PublicPropagationTransport,
        policy: PropagationRefreshPolicy,
    ): PropagationRefreshSourceDefinition =
        PropagationRefreshSourceDefinition(
            sourceKey = "NOAA_SWPC_F107_SUMMARY",
            role = PropagationRefreshSourceRole.NOAA_F107,
            policy = policy,
            fetcher = PropagationSourceFetcher { retrievedAtUtcMillis ->
                fetchAndParse(
                    transport = transport,
                    request = request(
                        NoaaSwpcPropagationAdapter.F107_SUMMARY_URL,
                        NOAA_JSON_MAX_BYTES,
                        JSON_TYPES,
                    ),
                ) { body, _ ->
                    PropagationAggregationInput(
                        solarGeomagnetic =
                            NoaaSwpcPropagationAdapter
                                .parseF107Summary(body, retrievedAtUtcMillis)
                                .map { it.observation },
                    )
                }
            },
        )

    fun noaaGlotec(
        transport: PublicPropagationTransport,
        policy: PropagationRefreshPolicy,
    ): PropagationRefreshSourceDefinition {
        require(
            policy.cadenceMillis >=
                NoaaSwpcGlotecAdapter.EXPECTED_CADENCE_MINUTES * 60_000L
        ) {
            "GloTEC refresh cadence must not poll faster than the pinned 10-minute product cadence"
        }

        return PropagationRefreshSourceDefinition(
            sourceKey = "NOAA_SWPC_GLOTEC_VTEC",
            role = PropagationRefreshSourceRole.NOAA_GLOTEC,
            policy = policy,
            fetcher = PropagationSourceFetcher { retrievedAtUtcMillis ->
                when (
                    val index = fetchBody(
                        transport = transport,
                        request = request(
                            NoaaSwpcGlotecAdapter.INDEX_URL,
                            GLOTEC_INDEX_MAX_BYTES,
                            JSON_TYPES,
                        ),
                    )
                ) {
                    is ValidatedPublicBody.Failure ->
                        index.toSourceFailure()

                    is ValidatedPublicBody.Success -> {
                        val selection = try {
                            NoaaSwpcGlotecIndexSelector.selectLatest(index.body)
                        } catch (e: Exception) {
                            return@PropagationSourceFetcher parserFailure(
                                "GloTEC index rejected",
                                e,
                            )
                        }

                        when (
                            val artifact = fetchBody(
                                transport = transport,
                                request = request(
                                    selection.artifactUrl,
                                    GLOTEC_ARTIFACT_MAX_BYTES,
                                    GEOJSON_TYPES,
                                ),
                            )
                        ) {
                            is ValidatedPublicBody.Failure ->
                                artifact.toSourceFailure()

                            is ValidatedPublicBody.Success -> {
                                try {
                                    val record =
                                        NoaaSwpcGlotecAdapter.parseGeoJson(
                                            json = artifact.body,
                                            retrievedAtUtcMillis = retrievedAtUtcMillis,
                                            sourceUrl = selection.artifactUrl,
                                        )
                                    PropagationSourceFetchResult.Success(
                                        PropagationAggregationInput(
                                            ionosphericProducts =
                                                listOf(record.product),
                                        )
                                    )
                                } catch (e: Exception) {
                                    parserFailure(
                                        "GloTEC artifact rejected",
                                        e,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    fun pskReporter(
        transport: PublicPropagationTransport,
        policy: PropagationRefreshPolicy,
        query: PskReporterPublicQuery,
    ): PropagationRefreshSourceDefinition {
        require(
            policy.cadenceMillis >=
                PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS
        ) {
            "PSK Reporter refresh cadence must respect the documented five-minute minimum retrieval interval"
        }
        val url = query.url()
        require(!url.contains("appcontact=", ignoreCase = true)) {
            "PSK Reporter source URL must not persist appcontact identifiers"
        }
        require(!url.contains("callback=", ignoreCase = true)) {
            "PSK Reporter XML source URL must not request callback/JSONP"
        }

        return PropagationRefreshSourceDefinition(
            sourceKey = "PSK_REPORTER_PUBLIC_QUERY",
            role = PropagationRefreshSourceRole.PSK_REPORTER,
            policy = policy,
            fetcher = PropagationSourceFetcher { retrievedAtUtcMillis ->
                fetchAndParse(
                    transport = transport,
                    request = request(
                        url,
                        PSK_REPORTER_XML_MAX_BYTES,
                        XML_TYPES,
                    ),
                ) { body, effectiveUrl ->
                    val parsed =
                        PskReporterHeardPathAdapter.parseXml(
                            xml = body,
                            retrievedAtUtcMillis = retrievedAtUtcMillis,
                            sourceUrl = effectiveUrl,
                        )
                    PropagationAggregationInput(
                        heardPaths = parsed.observations,
                    )
                }
            },
        )
    }

    private fun request(
        url: String,
        maxBytes: Int,
        contentTypes: Set<String>,
    ) = PublicPropagationRequest(
        url = url,
        maxResponseBytes = maxBytes,
        acceptedContentTypes = contentTypes,
    )

    private fun fetchAndParse(
        transport: PublicPropagationTransport,
        request: PublicPropagationRequest,
        parser: (String, String) -> PropagationAggregationInput,
    ): PropagationSourceFetchResult =
        when (val fetched = fetchBody(transport, request)) {
            is ValidatedPublicBody.Failure ->
                fetched.toSourceFailure()

            is ValidatedPublicBody.Success ->
                try {
                    PropagationSourceFetchResult.Success(
                        parser(fetched.body, fetched.effectiveUrl)
                    )
                } catch (e: Exception) {
                    parserFailure("Provider payload rejected", e)
                }
        }

    private fun fetchBody(
        transport: PublicPropagationTransport,
        request: PublicPropagationRequest,
    ): ValidatedPublicBody {
        val response = try {
            transport.get(request)
        } catch (e: Exception) {
            return ValidatedPublicBody.Failure(
                message = boundedMessage(
                    "Transport failure: " +
                        e::class.java.simpleName +
                        ": " +
                        (e.message ?: "no message")
                ),
                retryable = true,
            )
        }
        return PublicPropagationResponseValidator.validate(
            request = request,
            response = response,
        )
    }

    private fun ValidatedPublicBody.Failure.toSourceFailure() =
        PropagationSourceFetchResult.Failure(
            message = boundedMessage(message),
            retryable = retryable,
        )

    private fun parserFailure(
        prefix: String,
        exception: Exception,
    ) = PropagationSourceFetchResult.Failure(
        message = boundedMessage(
            "$prefix: " +
                exception::class.java.simpleName +
                ": " +
                (exception.message ?: "no message")
        ),
        retryable = false,
    )

    private fun boundedMessage(message: String): String =
        message.trim()
            .replace(Regex("""\s+"""), " ")
            .take(240)
            .ifBlank { "unspecified public propagation source failure" }
}
