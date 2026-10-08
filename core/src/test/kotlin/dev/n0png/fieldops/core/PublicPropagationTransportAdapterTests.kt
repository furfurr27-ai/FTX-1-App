package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object PublicPropagationTransportAdapterTests {
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
        requestContract()
        noaaSourceAdapters()
        glotecIndexSelection()
        glotecTransportAdapter()
        pskReporterQueryAndAdapter()
        responseAndFailureHandling()
        refreshCoordinatorIntegration()
        platformBoundary()

        println(
            "CP-0008H public propagation transport adapter tests: " +
                "PASS assertions=$assertions"
        )
    }

    private fun requestContract() {
        val request = PublicPropagationRequest(
            url = "https://example.invalid/data",
            maxResponseBytes = 10,
            acceptedContentTypes = setOf(" Application/JSON "),
        )
        eq(
            setOf("application/json"),
            request.normalizedAcceptedContentTypes,
            "content types normalize deterministically",
        )

        expectFailure("HTTPS required") {
            PublicPropagationRequest(
                "http://example.invalid",
                10,
                setOf("application/json"),
            )
        }
        expectFailure("fragment rejected") {
            PublicPropagationRequest(
                "https://example.invalid/data#fragment",
                10,
                setOf("application/json"),
            )
        }
        expectFailure("newline rejected") {
            PublicPropagationRequest(
                "https://example.invalid/data\nX-Test: bad",
                10,
                setOf("application/json"),
            )
        }
        expectFailure("max size positive") {
            PublicPropagationRequest(
                "https://example.invalid/data",
                0,
                setOf("application/json"),
            )
        }
        expectFailure("accepted type required") {
            PublicPropagationRequest(
                "https://example.invalid/data",
                10,
                emptySet(),
            )
        }
    }

    private fun noaaSourceAdapters() {
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val bodies = mapOf(
            NoaaSwpcPropagationAdapter.PLANETARY_KP_URL to
                fixture("noaa_swpc_planetary_kp_sample.json"),
            NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL to
                fixture("noaa_swpc_planetary_kp_forecast_sample.json"),
            NoaaSwpcPropagationAdapter.F107_SUMMARY_URL to
                fixture("noaa_swpc_f107_summary_sample.json"),
        )
        val transport = RecordingTransport { request ->
            ok(
                request = request,
                body = requireNotNull(bodies[request.url]),
                contentType = "application/json; charset=utf-8",
            )
        }
        val policy = policy(15 * 60_000L)

        val observed = PublicPropagationSourceAdapters.noaaPlanetaryKp(
            transport,
            policy,
        )
        val forecast = PublicPropagationSourceAdapters.noaaPlanetaryKpForecast(
            transport,
            policy,
        )
        val f107 = PublicPropagationSourceAdapters.noaaF107(
            transport,
            policy,
        )

        eq(
            PropagationRefreshSourceRole.NOAA_KP_OBSERVED,
            observed.role,
            "observed Kp role",
        )
        eq(
            "NOAA_SWPC_PLANETARY_KP",
            observed.sourceKey,
            "observed Kp source key",
        )
        eq(
            PropagationRefreshSourceRole.NOAA_KP_FORECAST,
            forecast.role,
            "forecast Kp role",
        )
        eq(
            "NOAA_SWPC_KP_FORECAST",
            forecast.sourceKey,
            "forecast source key",
        )
        eq(
            PropagationRefreshSourceRole.NOAA_F107,
            f107.role,
            "F10.7 role",
        )

        val observedResult =
            observed.fetcher.fetch(now) as PropagationSourceFetchResult.Success
        val forecastResult =
            forecast.fetcher.fetch(now) as PropagationSourceFetchResult.Success
        val f107Result =
            f107.fetcher.fetch(now) as PropagationSourceFetchResult.Success

        eq(3, observedResult.input.solarGeomagnetic.size, "observed rows parsed")
        eq(3, forecastResult.input.solarGeomagnetic.size, "forecast rows parsed")
        eq(1, f107Result.input.solarGeomagnetic.size, "F10.7 row parsed")
        checkThat(
            observedResult.input.solarGeomagnetic.all {
                it.source.sourceId == "NOAA_SWPC_PLANETARY_KP"
            },
            "observed Kp provenance retained",
        )
        checkThat(
            forecastResult.input.solarGeomagnetic.map { it.source.sourceId }.toSet() ==
                setOf(
                    "NOAA_SWPC_KP_FORECAST_OBSERVED",
                    "NOAA_SWPC_KP_FORECAST_ESTIMATED",
                    "NOAA_SWPC_KP_FORECAST_PREDICTED",
                ),
            "forecast status provenance retained",
        )
        eq(
            113.0,
            f107Result.input.solarGeomagnetic.single().f107SolarFluxSfu,
            "F10.7 value reaches normalized evidence",
        )
        checkThat(
            transport.requests.map { it.url } ==
                listOf(
                    NoaaSwpcPropagationAdapter.PLANETARY_KP_URL,
                    NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL,
                    NoaaSwpcPropagationAdapter.F107_SUMMARY_URL,
                ),
            "NOAA requests use exact pinned endpoints",
        )
        transport.requests.forEach {
            eq(
                PublicPropagationSourceAdapters.NOAA_JSON_MAX_BYTES,
                it.maxResponseBytes,
                "NOAA request uses bounded response size",
            )
            checkThat(
                "application/json" in it.normalizedAcceptedContentTypes,
                "NOAA accepts JSON content type",
            )
        }
    }

    private fun glotecIndexSelection() {
        val selection = NoaaSwpcGlotecIndexSelector.selectLatest(
            """
            {
              "files": [
                "glotec_icao_20260909T145500Z.geojson",
                "glotec_icao_20260909T151500Z.geojson",
                "glotec_icao_20260909T150500Z.geojson"
              ],
              "count": 3
            }
            """.trimIndent()
        )
        eq(
            "glotec_icao_20260909T151500Z.geojson",
            selection.artifactFilename,
            "latest filename selected by canonical timestamp",
        )
        eq(
            NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                "glotec_icao_20260909T151500Z.geojson",
            selection.artifactUrl,
            "filename resolves only under official directory",
        )
        eq(
            "20260909T151500Z",
            selection.timestampToken,
            "timestamp token preserved",
        )

        val fullUrl = NoaaSwpcGlotecIndexSelector.selectLatest(
            """
            [
              "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/glotec_icao_20260909T150500Z.geojson",
              "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/glotec_icao_20260909T151500Z.geojson"
            ]
            """.trimIndent()
        )
        eq(selection.artifactUrl, fullUrl.artifactUrl, "official full URLs accepted")

        val duplicate = NoaaSwpcGlotecIndexSelector.selectLatest(
            """
            [
              "glotec_icao_20260909T151500Z.geojson",
              "glotec_icao_20260909T151500Z.geojson"
            ]
            """.trimIndent()
        )
        eq(selection.artifactUrl, duplicate.artifactUrl, "duplicate candidates deduplicate")

        expectFailure("empty index has no artifact") {
            NoaaSwpcGlotecIndexSelector.selectLatest("[]")
        }
        expectFailure("malformed JSON rejected") {
            NoaaSwpcGlotecIndexSelector.selectLatest(
                """["glotec_icao_20260909T151500Z.geojson",]"""
            )
        }
        expectFailure("duplicate JSON keys rejected") {
            NoaaSwpcGlotecIndexSelector.selectLatest(
                """
                {
                  "files": ["glotec_icao_20260909T151500Z.geojson"],
                  "files": []
                }
                """.trimIndent()
            )
        }
        expectFailure("non-official URL rejected") {
            NoaaSwpcGlotecIndexSelector.selectLatest(
                """
                [
                  "https://evil.invalid/glotec_icao_20260909T151500Z.geojson"
                ]
                """.trimIndent()
            )
        }
        expectFailure("malformed candidate rejected") {
            NoaaSwpcGlotecIndexSelector.selectLatest(
                """["glotec_icao_latest.geojson"]"""
            )
        }
        expectFailure("official URL with query rejected") {
            NoaaSwpcGlotecIndexSelector.selectLatest(
                """
                [
                  "https://services.swpc.noaa.gov/products/glotec/geojson_2d_urt/glotec_icao_20260909T151500Z.geojson?x=1"
                ]
                """.trimIndent()
            )
        }
    }

    private fun glotecTransportAdapter() {
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val artifact =
            NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                "glotec_icao_20260909T151500Z.geojson"
        val indexBody =
            """
            {
              "items": [
                "glotec_icao_20260909T150500Z.geojson",
                "glotec_icao_20260909T151500Z.geojson"
              ]
            }
            """.trimIndent()

        val transport = RecordingTransport { request ->
            when (request.url) {
                NoaaSwpcGlotecAdapter.INDEX_URL ->
                    ok(request, indexBody, "application/json")
                artifact ->
                    ok(
                        request,
                        fixture(
                            "noaa_swpc_glotec_20260909T151500Z_bounded.geojson"
                        ),
                        "application/geo+json",
                    )
                else -> error("Unexpected GloTEC request " + request.url)
            }
        }

        val source = PublicPropagationSourceAdapters.noaaGlotec(
            transport = transport,
            policy = policy(10 * 60_000L),
        )
        eq(
            PropagationRefreshSourceRole.NOAA_GLOTEC,
            source.role,
            "GloTEC refresh role",
        )
        val result =
            source.fetcher.fetch(now) as PropagationSourceFetchResult.Success
        eq(
            1,
            result.input.ionosphericProducts.size,
            "GloTEC artifact normalizes one map product",
        )
        val product = result.input.ionosphericProducts.single()
        eq(
            "NOAA_SWPC_GLOTEC_VTEC",
            product.source.sourceId,
            "GloTEC source id preserved",
        )
        eq(
            artifact,
            product.source.sourceUrl,
            "selected exact artifact URL retained as provenance",
        )
        eq(4, product.samples.size, "bounded GloTEC fixture samples parsed")
        eq(
            listOf(NoaaSwpcGlotecAdapter.INDEX_URL, artifact),
            transport.requests.map { it.url },
            "GloTEC transport fetches index then selected artifact",
        )
        eq(
            PublicPropagationSourceAdapters.GLOTEC_INDEX_MAX_BYTES,
            transport.requests[0].maxResponseBytes,
            "GloTEC index response bounded",
        )
        eq(
            PublicPropagationSourceAdapters.GLOTEC_ARTIFACT_MAX_BYTES,
            transport.requests[1].maxResponseBytes,
            "GloTEC artifact response bounded",
        )
        expectFailure("GloTEC polling cannot exceed pinned cadence") {
            PublicPropagationSourceAdapters.noaaGlotec(
                transport,
                policy(9 * 60_000L),
            )
        }
    }

    private fun pskReporterQueryAndAdapter() {
        val query = PskReporterPublicQuery(callsign = "n0png")
        eq(
            "https://retrieve.pskreporter.info/query?" +
                "callsign=N0PNG&flowStartSeconds=-3600&rptlimit=500&" +
                "rronly=1&noactive=1",
            query.url(),
            "default PSK query is deterministic and minimal",
        )
        checkThat(
            !query.url().contains("appcontact=", ignoreCase = true),
            "query excludes appcontact",
        )
        checkThat(
            !query.url().contains("callback=", ignoreCase = true),
            "query excludes callback",
        )

        val portable = PskReporterPublicQuery(
            callsign = "N0PNG/P",
            selector = PskReporterQuerySelector.SENDER,
            lookbackSeconds = 3_600,
            reportLimit = 250,
            mode = "ft8",
            minimumFrequencyHz = 14_000_000,
            maximumFrequencyHz = 14_350_000,
        )
        eq(
            "https://retrieve.pskreporter.info/query?" +
                "senderCallsign=N0PNG%2FP&flowStartSeconds=-3600&" +
                "rptlimit=250&rronly=1&noactive=1&mode=FT8&" +
                "frange=14000000-14350000",
            portable.url(),
            "portable callsign and optional filters encode deterministically",
        )

        val receiver = PskReporterPublicQuery(
            callsign = "K1ABC",
            selector = PskReporterQuerySelector.RECEIVER,
        )
        checkThat(
            receiver.url().contains("receiverCallsign=K1ABC"),
            "receiver selector uses official parameter",
        )

        expectFailure("blank callsign") { PskReporterPublicQuery(" ") }
        expectFailure("unsupported callsign character") {
            PskReporterPublicQuery("N0PNG@example")
        }
        expectFailure("lookback longer than 24 hours") {
            PskReporterPublicQuery("N0PNG", lookbackSeconds = 86_401)
        }
        expectFailure("zero report limit") {
            PskReporterPublicQuery("N0PNG", reportLimit = 0)
        }
        expectFailure("one-sided frequency range") {
            PskReporterPublicQuery(
                "N0PNG",
                minimumFrequencyHz = 14_000_000,
            )
        }
        expectFailure("frequency range order") {
            PskReporterPublicQuery(
                "N0PNG",
                minimumFrequencyHz = 14_350_000,
                maximumFrequencyHz = 14_000_000,
            )
        }

        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val transport = RecordingTransport { request ->
            ok(
                request,
                fixture("psk_reporter_ag6k_20200903_bounded.xml"),
                "text/xml; charset=UTF-8",
            )
        }
        val source = PublicPropagationSourceAdapters.pskReporter(
            transport = transport,
            policy = policy(
                PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS
            ),
            query = query,
        )
        eq(
            PropagationRefreshSourceRole.PSK_REPORTER,
            source.role,
            "PSK Reporter refresh role",
        )
        val result =
            source.fetcher.fetch(now) as PropagationSourceFetchResult.Success
        eq(4, result.input.heardPaths.size, "bounded PSK fixture parsed")
        checkThat(
            result.input.heardPaths.all {
                it.source.sourceUrl == query.url()
            },
            "PSK source provenance uses exact public query URL",
        )
        checkThat(
            result.input.heardPaths.all {
                it.source.retrievedAtUtcMillis == now
            },
            "PSK retrieval UTC is caller supplied refresh time",
        )
        eq(1, transport.requests.size, "PSK transport makes one request")
        eq(query.url(), transport.requests.single().url, "PSK exact URL requested")
        eq(
            PublicPropagationSourceAdapters.PSK_REPORTER_XML_MAX_BYTES,
            transport.requests.single().maxResponseBytes,
            "PSK response bounded",
        )
        checkThat(
            "text/xml" in transport.requests.single().normalizedAcceptedContentTypes,
            "PSK accepts documented XML service content type",
        )

        expectFailure("PSK policy must respect five-minute retrieval guidance") {
            PublicPropagationSourceAdapters.pskReporter(
                transport,
                policy(
                    PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS - 1
                ),
                query,
            )
        }
    }

    private fun responseAndFailureHandling() {
        val policy = policy(15 * 60_000L)
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val goodBody = fixture("noaa_swpc_f107_summary_sample.json")

        fun result(
            responder: (PublicPropagationRequest) -> PublicPropagationResponse,
        ): PropagationSourceFetchResult {
            val source = PublicPropagationSourceAdapters.noaaF107(
                RecordingTransport(responder),
                policy,
            )
            return source.fetcher.fetch(now)
        }

        val rateLimited = result { request ->
            PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = request.url,
                statusCode = 429,
                contentType = "application/json",
                body = "rate limited",
            )
        } as PropagationSourceFetchResult.Failure
        eq(true, rateLimited.retryable, "HTTP 429 is retryable")
        checkThat(rateLimited.message.contains("HTTP 429"), "HTTP 429 reason retained")

        val unavailable = result { request ->
            PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = request.url,
                statusCode = 503,
                contentType = "application/json",
                body = "unavailable",
            )
        } as PropagationSourceFetchResult.Failure
        eq(true, unavailable.retryable, "HTTP 503 is retryable")

        val notFound = result { request ->
            PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = request.url,
                statusCode = 404,
                contentType = "application/json",
                body = "missing",
            )
        } as PropagationSourceFetchResult.Failure
        eq(false, notFound.retryable, "HTTP 404 is not rapid-retryable")

        val redirect = result { request ->
            PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = "https://evil.invalid/data",
                statusCode = 200,
                contentType = "application/json",
                body = goodBody,
            )
        } as PropagationSourceFetchResult.Failure
        eq(false, redirect.retryable, "unexpected effective URL fails closed")
        checkThat(
            redirect.message.contains("effective URL"),
            "redirect failure is explicit",
        )

        val requestMismatch = result { request ->
            PublicPropagationResponse(
                requestedUrl = "https://other.invalid",
                effectiveUrl = request.url,
                statusCode = 200,
                contentType = "application/json",
                body = goodBody,
            )
        } as PropagationSourceFetchResult.Failure
        eq(false, requestMismatch.retryable, "request provenance mismatch fails closed")

        val contentType = result { request ->
            ok(request, goodBody, "text/html")
        } as PropagationSourceFetchResult.Failure
        eq(false, contentType.retryable, "wrong content type fails closed")
        checkThat(
            contentType.message.contains("content type"),
            "wrong content type reason retained",
        )

        val blank = result { request ->
            ok(request, "   ", "application/json")
        } as PropagationSourceFetchResult.Failure
        eq(true, blank.retryable, "blank 200 response can be retried")

        val oversized = result { request ->
            ok(
                request,
                "x".repeat(
                    PublicPropagationSourceAdapters.NOAA_JSON_MAX_BYTES + 1
                ),
                "application/json",
            )
        } as PropagationSourceFetchResult.Failure
        eq(false, oversized.retryable, "oversized body fails closed")
        checkThat(
            oversized.message.contains("exceeds"),
            "oversized reason retained",
        )

        val malformed = result { request ->
            ok(request, """[{"flux":"113","time_tag":"bad"}]""", "application/json")
        } as PropagationSourceFetchResult.Failure
        eq(false, malformed.retryable, "provider schema drift is non-retryable")
        checkThat(
            malformed.message.contains("Provider payload rejected"),
            "parser rejection is explicit",
        )

        val noContentType = result { request ->
            ok(request, goodBody, null)
        }
        checkThat(
            noContentType is PropagationSourceFetchResult.Success,
            "missing content-type is tolerated when exact endpoint and parser both validate",
        )

        val throwingTransport = object : PublicPropagationTransport {
            override fun get(request: PublicPropagationRequest): PublicPropagationResponse {
                throw IllegalStateException("simulated network exception")
            }
        }
        val thrown = PublicPropagationSourceAdapters
            .noaaF107(throwingTransport, policy)
            .fetcher
            .fetch(now) as PropagationSourceFetchResult.Failure
        eq(true, thrown.retryable, "transport exception is retryable")
        checkThat(
            thrown.message.contains("IllegalStateException"),
            "transport exception class retained",
        )
    }

    private fun refreshCoordinatorIntegration() {
        val now = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val pskQuery = PskReporterPublicQuery(
            callsign = "N0PNG",
            lookbackSeconds = 60 * 60,
            reportLimit = 500,
        )
        val artifact =
            NoaaSwpcGlotecAdapter.DIRECTORY_URL +
                "glotec_icao_20260909T151500Z.geojson"
        val transport = RecordingTransport { request ->
            when (request.url) {
                NoaaSwpcPropagationAdapter.PLANETARY_KP_URL ->
                    ok(
                        request,
                        fixture("noaa_swpc_planetary_kp_sample.json"),
                        "application/json",
                    )
                NoaaSwpcPropagationAdapter.PLANETARY_KP_FORECAST_URL ->
                    ok(
                        request,
                        fixture(
                            "noaa_swpc_planetary_kp_forecast_sample.json"
                        ),
                        "application/json",
                    )
                NoaaSwpcPropagationAdapter.F107_SUMMARY_URL ->
                    ok(
                        request,
                        fixture("noaa_swpc_f107_summary_sample.json"),
                        "application/json",
                    )
                NoaaSwpcGlotecAdapter.INDEX_URL ->
                    ok(
                        request,
                        """["glotec_icao_20260909T151500Z.geojson"]""",
                        "application/json",
                    )
                artifact ->
                    ok(
                        request,
                        fixture(
                            "noaa_swpc_glotec_20260909T151500Z_bounded.geojson"
                        ),
                        "application/geo+json",
                    )
                pskQuery.url() ->
                    ok(
                        request,
                        fixture("psk_reporter_ag6k_20200903_bounded.xml"),
                        "text/xml",
                    )
                else -> error("Unexpected integration request " + request.url)
            }
        }

        val sources = listOf(
            PublicPropagationSourceAdapters.noaaPlanetaryKp(
                transport,
                policy(15 * 60_000L),
            ),
            PublicPropagationSourceAdapters.noaaPlanetaryKpForecast(
                transport,
                policy(15 * 60_000L),
            ),
            PublicPropagationSourceAdapters.noaaF107(
                transport,
                policy(60 * 60_000L),
            ),
            PublicPropagationSourceAdapters.noaaGlotec(
                transport,
                policy(10 * 60_000L),
            ),
            PublicPropagationSourceAdapters.pskReporter(
                transport,
                policy(
                    PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS
                ),
                pskQuery,
            ),
        )

        val store = InMemoryPropagationSnapshotStore()
        val coordinator = PropagationSourceRefreshCoordinator(
            sources = sources,
            snapshotStore = store,
        )
        val cycle = coordinator.refresh(now)
        eq(5, cycle.successfulAttemptCount, "all five public source definitions refresh")
        eq(0, cycle.failedAttemptCount, "integration cycle has no source failures")
        val snapshot = requireNotNull(cycle.savedSnapshot)
        eq(
            6,
            snapshot.solarGeomagnetic.size,
            "canonical Kp plus F10.7 evidence aggregated",
        )
        eq(1, snapshot.ionosphericProducts.size, "GloTEC evidence aggregated")
        eq(4, snapshot.heardPaths.size, "PSK heard paths aggregated")
        checkThat(
            snapshot.solarGeomagnetic.none {
                it.source.sourceId == "NOAA_SWPC_KP_FORECAST_OBSERVED"
            },
            "CP-0008G canonical observed-Kp rule remains active through transport layer",
        )
        checkThat(
            snapshot.solarGeomagnetic.any {
                it.source.sourceId == "NOAA_SWPC_PLANETARY_KP"
            },
            "dedicated observed Kp remains canonical",
        )
        checkThat(
            snapshot.solarGeomagnetic.any {
                it.source.sourceId == "NOAA_SWPC_KP_FORECAST_ESTIMATED"
            },
            "estimated Kp forecast retained",
        )
        checkThat(
            snapshot.solarGeomagnetic.any {
                it.source.sourceId == "NOAA_SWPC_KP_FORECAST_PREDICTED"
            },
            "predicted Kp forecast retained",
        )
        eq(6, transport.requests.size, "five sources issue six HTTP requests because GloTEC uses index plus artifact")
        eq(snapshot.snapshotId, store.latest()?.snapshotId, "public refresh snapshot persisted")

        val beforeEarly = transport.requests.size
        val early = coordinator.refresh(now + 4 * 60_000L)
        eq(0, early.attempts.size, "all public sources respect cadence before five minutes")
        eq(5, early.skippedSourceKeys.size, "all five sources skipped before eligibility")
        eq(beforeEarly, transport.requests.size, "cadence skip performs no transport calls")
        eq(
            snapshot.snapshotId,
            early.latestSnapshot?.snapshotId,
            "early skipped refresh keeps last good snapshot",
        )
    }

    private fun platformBoundary() {
        val classes = listOf(
            PublicPropagationRequest::class.java,
            PublicPropagationResponse::class.java,
            PublicPropagationSourceAdapters::class.java,
            NoaaSwpcGlotecIndexSelector::class.java,
            PskReporterPublicQuery::class.java,
        )
        val fields = classes.flatMap { it.declaredFields.toList() }

        checkThat(
            fields.none {
                it.type.name.startsWith("android.") ||
                    it.type.name.startsWith("androidx.") ||
                    it.type.name.contains("WorkManager") ||
                    it.type.name.contains("OkHttp") ||
                    it.type.name.contains("Retrofit") ||
                    it.type.name.contains("HttpURLConnection")
            },
            "CP-0008H production contracts expose no Android or concrete HTTP client types",
        )
        checkThat(
            fields.none {
                it.name.contains("password", ignoreCase = true) ||
                    it.name.contains("credential", ignoreCase = true) ||
                    it.name.contains("apiKey", ignoreCase = true) ||
                    it.name.contains("appcontact", ignoreCase = true)
            },
            "CP-0008H production state exposes no account/contact-secret fields",
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

    private fun ok(
        request: PublicPropagationRequest,
        body: String,
        contentType: String?,
    ) = PublicPropagationResponse(
        requestedUrl = request.url,
        effectiveUrl = request.url,
        statusCode = 200,
        contentType = contentType,
        body = body,
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
