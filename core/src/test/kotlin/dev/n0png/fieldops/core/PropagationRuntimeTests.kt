package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object PropagationRuntimeTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) {
        assertions++
        check(expected == actual) {
            "$message expected=$expected actual=$actual"
        }
    }

    private fun expectFailure(message: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) {
            "Expected failure: $message"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        defaultConfigurationIsSafeAndExplicit()
        invalidConfigurationFailsClosed()
        factoryComposesFiveVerifiedSources()
        fullRefreshAndProjectionWorks()
        cadenceSelectivelyRefreshesSources()
        failedEligibleSourcePreservesLastGoodProjection()
        injectedStoresAndFiltersRemainEffective()
        noGeographyOrPlatformStateIsInvented()

        println("CP-0008J propagation runtime tests: PASS assertions=$assertions")
    }

    private fun defaultConfigurationIsSafeAndExplicit() {
        val config = PropagationRuntimeConfig(operatorCallsign = "n0png")

        eq("N0PNG", config.normalizedOperatorCallsign, "operator callsign normalized")
        eq("N0PNG", config.pskReporterQuery.callsign.uppercase(), "default PSK query uses explicit operator callsign")
        eq(
            15 * 60_000L,
            config.refreshPolicies.planetaryKp.cadenceMillis,
            "observed Kp default cadence",
        )
        eq(
            15 * 60_000L,
            config.refreshPolicies.planetaryKpForecast.cadenceMillis,
            "forecast Kp default cadence",
        )
        eq(
            60 * 60_000L,
            config.refreshPolicies.f107.cadenceMillis,
            "F10.7 default cadence",
        )
        eq(
            10 * 60_000L,
            config.refreshPolicies.glotec.cadenceMillis,
            "GloTEC default cadence matches product cadence",
        )
        eq(
            PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS,
            config.refreshPolicies.pskReporter.cadenceMillis,
            "PSK default cadence matches documented minimum",
        )
        listOf(
            config.refreshPolicies.planetaryKp,
            config.refreshPolicies.planetaryKpForecast,
            config.refreshPolicies.f107,
            config.refreshPolicies.glotec,
            config.refreshPolicies.pskReporter,
        ).forEach {
            eq(60_000L, it.initialRetryBackoffMillis, "default initial retry")
            eq(15 * 60_000L, it.maximumRetryBackoffMillis, "default maximum retry")
        }
        eq(10_000, config.httpsTransportConfig.connectTimeoutMillis, "runtime carries HTTPS connect timeout")
        eq(15_000, config.httpsTransportConfig.readTimeoutMillis, "runtime carries HTTPS read timeout")
    }

    private fun invalidConfigurationFailsClosed() {
        expectFailure("blank operator callsign") {
            PropagationRuntimeConfig(operatorCallsign = " ")
        }
        expectFailure("operator callsign unsupported character") {
            PropagationRuntimeConfig(operatorCallsign = "N0PNG@example")
        }
        expectFailure("GloTEC cadence below product minimum") {
            PropagationRuntimeRefreshPolicies(
                glotec = policy(9 * 60_000L),
            )
        }
        expectFailure("PSK cadence below retrieval minimum") {
            PropagationRuntimeRefreshPolicies(
                pskReporter = policy(
                    PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS - 1
                ),
            )
        }

        val config = PropagationRuntimeConfig(
            operatorCallsign = "N0PNG",
            pskReporterQuery = PskReporterPublicQuery(
                callsign = "K1ABC",
                selector = PskReporterQuerySelector.RECEIVER,
            ),
        )
        eq("N0PNG", config.normalizedOperatorCallsign, "operator identity remains explicit")
        eq("K1ABC", config.pskReporterQuery.callsign, "PSK target may be explicitly different")
        eq(
            PskReporterQuerySelector.RECEIVER,
            config.pskReporterQuery.selector,
            "PSK selector remains explicit",
        )
    }

    private fun factoryComposesFiveVerifiedSources() {
        val runtime = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig(operatorCallsign = "N0PNG"),
            transport = RecordingTransport(::providerResponse),
        )

        eq(5, runtime.sourceDefinitions.size, "runtime composes five sources")
        eq(
            listOf(
                "NOAA_SWPC_F107_SUMMARY",
                "NOAA_SWPC_GLOTEC_VTEC",
                "NOAA_SWPC_KP_FORECAST",
                "NOAA_SWPC_PLANETARY_KP",
                "PSK_REPORTER_PUBLIC_QUERY",
            ),
            runtime.sourceKeys,
            "runtime source keys deterministic",
        )
        eq(
            setOf(
                PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
                PropagationRefreshSourceRole.NOAA_KP_FORECAST,
                PropagationRefreshSourceRole.NOAA_F107,
                PropagationRefreshSourceRole.NOAA_GLOTEC,
                PropagationRefreshSourceRole.PSK_REPORTER,
            ),
            runtime.sourceDefinitions.map { it.role }.toSet(),
            "runtime source roles complete",
        )
        eq(5, runtime.sourceStates().size, "runtime initializes five source states")
        checkThat(
            runtime.sourceStates().all {
                it.lastAttemptUtcMillis == null &&
                    it.lastSuccessUtcMillis == null &&
                    it.consecutiveFailures == 0
            },
            "new runtime source state starts clean",
        )
    }

    private fun fullRefreshAndProjectionWorks() {
        val transport = RecordingTransport(::providerResponse)
        val runtime = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig(operatorCallsign = "N0PNG"),
            transport = transport,
        )
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()

        val result = runtime.refreshAndProject(now)

        eq(5, result.refresh.successfulAttemptCount, "all five sources refresh")
        eq(0, result.refresh.failedAttemptCount, "no source fails")
        val snapshot = requireNotNull(result.refresh.savedSnapshot)
        eq(snapshot.snapshotId, result.refresh.latestSnapshot?.snapshotId, "saved snapshot becomes latest")

        val projection = requireNotNull(result.projection)
        eq(snapshot.snapshotId, projection.status.snapshotId, "new snapshot projected")
        eq(6, projection.solarGeomagnetic.size, "canonical Kp plus F10.7 projected")
        eq(1, projection.ionosphericProducts.size, "GloTEC projected")
        eq(4, projection.heardPaths.size, "PSK heard paths projected")
        eq(0, projection.modeledPaths.size, "runtime invents no modeled paths")
        checkThat(
            projection.solarGeomagnetic.none {
                it.metadata.source.sourceId == "NOAA_SWPC_KP_FORECAST_OBSERVED"
            },
            "canonical Kp rule remains active",
        )
        checkThat(
            projection.solarGeomagnetic.any {
                it.metadata.source.sourceId == "NOAA_SWPC_PLANETARY_KP"
            },
            "dedicated observed Kp retained",
        )
        eq(6, transport.requests.size, "five sources require six requests due GloTEC index+artifact")
        eq(5, runtime.sourceStates().count { it.lastSuccessUtcMillis == now }, "all source success times tracked")
    }

    private fun cadenceSelectivelyRefreshesSources() {
        val transport = RecordingTransport(::providerResponse)
        val runtime = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig(operatorCallsign = "N0PNG"),
            transport = transport,
        )
        val start = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        runtime.refreshAndProject(start)
        transport.requests.clear()

        val plusFour = runtime.refreshAndProject(start + 4 * 60_000L)
        eq(0, plusFour.refresh.attempts.size, "no source due at four minutes")
        eq(5, plusFour.refresh.skippedSourceKeys.size, "all sources skipped at four minutes")
        eq(0, transport.requests.size, "four-minute cycle makes no network request")

        val plusFive = runtime.refreshAndProject(start + 5 * 60_000L)
        eq(1, plusFive.refresh.attempts.size, "only PSK due at five minutes")
        eq("PSK_REPORTER_PUBLIC_QUERY", plusFive.refresh.attempts.single().sourceKey, "PSK is five-minute source")
        eq(1, transport.requests.size, "five-minute cycle makes one request")
        checkThat(
            transport.requests.single().url.startsWith(PskReporterHeardPathAdapter.QUERY_URL),
            "five-minute request is PSK Reporter",
        )

        transport.requests.clear()
        val plusTen = runtime.refreshAndProject(start + 10 * 60_000L)
        eq(
            setOf("NOAA_SWPC_GLOTEC_VTEC", "PSK_REPORTER_PUBLIC_QUERY"),
            plusTen.refresh.attempts.map { it.sourceKey }.toSet(),
            "GloTEC and PSK due at ten minutes",
        )
        eq(3, transport.requests.size, "ten-minute cycle uses PSK plus GloTEC index+artifact")
    }

    private fun failedEligibleSourcePreservesLastGoodProjection() {
        var failPsk = false
        val transport = RecordingTransport { request ->
            if (
                failPsk &&
                request.url.startsWith(PskReporterHeardPathAdapter.QUERY_URL)
            ) {
                PublicPropagationResponse(
                    requestedUrl = request.url,
                    effectiveUrl = request.url,
                    statusCode = 503,
                    contentType = "text/plain",
                    body = "unavailable",
                )
            } else {
                providerResponse(request)
            }
        }
        val runtime = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig(operatorCallsign = "N0PNG"),
            transport = transport,
        )
        val start = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val first = runtime.refreshAndProject(start)
        val firstProjection = requireNotNull(first.projection)

        failPsk = true
        val second = runtime.refreshAndProject(start + 5 * 60_000L)

        eq(0, second.refresh.successfulAttemptCount, "only eligible source failed")
        eq(1, second.refresh.failedAttemptCount, "PSK failure recorded")
        eq(null, second.refresh.savedSnapshot, "all-attempt failure writes no snapshot")
        val secondProjection = requireNotNull(second.projection)
        eq(
            firstProjection.status.snapshotId,
            secondProjection.status.snapshotId,
            "failed runtime refresh projects last good snapshot",
        )
        val pskState = runtime.sourceStates().single {
            it.sourceKey == "PSK_REPORTER_PUBLIC_QUERY"
        }
        eq(1, pskState.consecutiveFailures, "runtime exposes PSK failure state")
        eq(true, pskState.lastFailureRetryable, "503 remains retryable")
        checkThat(
            pskState.lastFailureMessage!!.contains("HTTP 503"),
            "failure reason remains visible",
        )
    }

    private fun injectedStoresAndFiltersRemainEffective() {
        val snapshotStore = InMemoryPropagationSnapshotStore()
        val stateStore = InMemoryPropagationRefreshStateStore()
        val runtime = PropagationRuntimeFactory.create(
            config = PropagationRuntimeConfig(operatorCallsign = "N0PNG"),
            transport = RecordingTransport(::providerResponse),
            snapshotStore = snapshotStore,
            refreshStateStore = stateStore,
        )
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val result = runtime.refreshAndProject(
            nowUtcMillis = now,
            filter = PropagationProjectionFilter(
                modes = setOf("FT8"),
                sourceIds = setOf("PSK_REPORTER_PUBLIC_QUERY"),
            ),
        )

        eq(snapshotStore.latest()?.snapshotId, result.refresh.savedSnapshot?.snapshotId, "injected snapshot store used")
        eq(5, stateStore.all().size, "injected state store used")
        val projection = requireNotNull(result.projection)
        eq(4, projection.heardPaths.size, "filter retains PSK FT8 paths")
        eq(0, projection.solarGeomagnetic.size, "source filter excludes NOAA solar context")
        eq(0, projection.ionosphericProducts.size, "source filter excludes GloTEC")
    }

    private fun noGeographyOrPlatformStateIsInvented() {
        val fields = listOf(
            PropagationRuntimeConfig::class.java,
            PropagationRuntimeRefreshPolicies::class.java,
            PropagationRuntime::class.java,
            PropagationRuntimeFactory::class.java,
        ).flatMap { it.declaredFields.toList() }

        checkThat(
            fields.none {
                it.name.contains("latitude", ignoreCase = true) ||
                    it.name.contains("longitude", ignoreCase = true) ||
                    it.name.contains("maidenhead", ignoreCase = true) ||
                    it.name.contains("grid", ignoreCase = true)
            },
            "runtime config contains no inferred station geography",
        )
        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("WorkManager")
            },
            "runtime contains no Android/background scheduler field types",
        )
        checkThat(
            fields.none {
                it.name.contains("password", ignoreCase = true) ||
                    it.name.contains("credential", ignoreCase = true) ||
                    it.name.contains("apiKey", ignoreCase = true)
            },
            "runtime contains no credential fields",
        )
    }

    private fun providerResponse(
        request: PublicPropagationRequest,
    ): PublicPropagationResponse {
        val body: String
        val contentType: String
        when {
            request.url == NoaaSwpcPropagationAdapter.PLANETARY_KP_URL -> {
                body = fixture("noaa_swpc_planetary_kp_sample.json")
                contentType = "application/json"
            }
            request.url == NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL -> {
                body = fixture("noaa_swpc_planetary_kp_forecast_sample.json")
                contentType = "application/json"
            }
            request.url == NoaaSwpcPropagationAdapter.F107_SUMMARY_URL -> {
                body = fixture("noaa_swpc_f107_summary_sample.json")
                contentType = "application/json"
            }
            request.url == NoaaSwpcGlotecAdapter.INDEX_URL -> {
                body = """["glotec_icao_20260909T151500Z.geojson"]"""
                contentType = "application/json"
            }
            request.url ==
                NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                    "glotec_icao_20260909T151500Z.geojson" -> {
                body = fixture(
                    "noaa_swpc_glotec_20260909T151500Z_bounded.geojson"
                )
                contentType = "application/geo+json"
            }
            request.url.startsWith(PskReporterHeardPathAdapter.QUERY_URL) -> {
                body = fixture("psk_reporter_ag6k_20200903_bounded.xml")
                contentType = "text/xml"
            }
            else -> error("Unexpected runtime provider request: " + request.url)
        }

        return PublicPropagationResponse(
            requestedUrl = request.url,
            effectiveUrl = request.url,
            statusCode = 200,
            contentType = contentType,
            body = body,
        )
    }

    private fun fixture(name: String): String =
        Files.readString(
            Path.of("research", "propagation", "fixtures", name)
        )

    private fun policy(cadenceMillis: Long) =
        PropagationRefreshPolicy(
            cadenceMillis = cadenceMillis,
            initialRetryBackoffMillis = 60_000L,
            maximumRetryBackoffMillis = 15 * 60_000L,
        )

    private class RecordingTransport(
        private val responder:
            (PublicPropagationRequest) -> PublicPropagationResponse,
    ) : PublicPropagationTransport {
        val requests = mutableListOf<PublicPropagationRequest>()

        override fun get(
            request: PublicPropagationRequest,
        ): PublicPropagationResponse {
            requests += request
            return responder(request)
        }
    }
}
