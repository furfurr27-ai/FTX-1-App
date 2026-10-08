package dev.n0png.fieldops.core.propagation

data class PropagationRuntimeRefreshPolicies(
    val planetaryKp: PropagationRefreshPolicy = PropagationRefreshPolicy(
        cadenceMillis = 15 * 60_000L,
        initialRetryBackoffMillis = 60_000L,
        maximumRetryBackoffMillis = 15 * 60_000L,
    ),
    val planetaryKpForecast: PropagationRefreshPolicy = PropagationRefreshPolicy(
        cadenceMillis = 15 * 60_000L,
        initialRetryBackoffMillis = 60_000L,
        maximumRetryBackoffMillis = 15 * 60_000L,
    ),
    val f107: PropagationRefreshPolicy = PropagationRefreshPolicy(
        cadenceMillis = 60 * 60_000L,
        initialRetryBackoffMillis = 60_000L,
        maximumRetryBackoffMillis = 15 * 60_000L,
    ),
    val glotec: PropagationRefreshPolicy = PropagationRefreshPolicy(
        cadenceMillis =
            NoaaSwpcGlotecAdapter.EXPECTED_CADENCE_MINUTES * 60_000L,
        initialRetryBackoffMillis = 60_000L,
        maximumRetryBackoffMillis = 15 * 60_000L,
    ),
    val pskReporter: PropagationRefreshPolicy = PropagationRefreshPolicy(
        cadenceMillis =
            PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS,
        initialRetryBackoffMillis = 60_000L,
        maximumRetryBackoffMillis = 15 * 60_000L,
    ),
) {
    init {
        require(
            glotec.cadenceMillis >=
                NoaaSwpcGlotecAdapter.EXPECTED_CADENCE_MINUTES * 60_000L
        ) {
            "Runtime GloTEC cadence must respect the pinned 10-minute product cadence"
        }
        require(
            pskReporter.cadenceMillis >=
                PskReporterHeardPathAdapter.MIN_RETRIEVAL_INTERVAL_MILLIS
        ) {
            "Runtime PSK Reporter cadence must respect the documented five-minute minimum"
        }
    }
}

data class PropagationRuntimeConfig(
    val operatorCallsign: String,
    val pskReporterQuery: PskReporterPublicQuery =
        PskReporterPublicQuery(callsign = operatorCallsign),
    val refreshPolicies: PropagationRuntimeRefreshPolicies =
        PropagationRuntimeRefreshPolicies(),
    val httpsTransportConfig: HttpsPublicPropagationTransportConfig =
        HttpsPublicPropagationTransportConfig(),
) {
    init {
        require(operatorCallsign.isNotBlank()) {
            "Propagation runtime operator callsign must not be blank"
        }
        require(operatorCallsign.length <= 32) {
            "Propagation runtime operator callsign is unexpectedly long"
        }
        require(
            operatorCallsign.all {
                it.isLetterOrDigit() || it == '/' || it == '-'
            }
        ) {
            "Propagation runtime operator callsign contains unsupported characters"
        }
    }

    val normalizedOperatorCallsign: String
        get() = operatorCallsign.trim().uppercase()
}

class PropagationRuntime internal constructor(
    val config: PropagationRuntimeConfig,
    val sourceDefinitions: List<PropagationRefreshSourceDefinition>,
    val snapshotStore: PropagationSnapshotStore,
    val refreshStateStore: PropagationRefreshStateStore,
    private val workspaceService: PropagationRefreshWorkspaceService,
) {
    init {
        require(sourceDefinitions.size == 5) {
            "Propagation runtime requires the five verified public sources"
        }
        require(sourceDefinitions.map { it.sourceKey }.distinct().size == 5) {
            "Propagation runtime source keys must be unique"
        }
    }

    val sourceKeys: List<String>
        get() = sourceDefinitions.map { it.sourceKey }.sorted()

    fun sourceStates(): List<PropagationRefreshSourceState> =
        refreshStateStore.all()

    /** Read-only source readiness and cached-evidence freshness; explicit UTC. */
    fun sourceStatus(nowUtcMillis: Long): PropagationSourcesStatusProjection =
        PropagationSourceStatusService(refreshStateStore, snapshotStore)
            .project(nowUtcMillis)

    /** One captured cached snapshot for both lossless operating-picture views. */
    fun operatingPicture(query: PropagationProjectionQuery): PropagationOperatingPicture =
        PropagationOperatingPictureService(snapshotStore, refreshStateStore).read(query)

    fun operatingPicture(
        nowUtcMillis: Long,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
        selectedPath: PropagationAssessmentQuery? = null,
    ): PropagationOperatingPicture =
        operatingPicture(PropagationProjectionQuery(nowUtcMillis, filter, selectedPath))

    /** Read-only UTC/last-good provenance diagnostics from the same captured view. */
    fun operatingPictureWithDiagnostics(
        query: PropagationProjectionQuery,
    ): PropagationOperatingPictureWithDiagnostics =
        PropagationReadModelConsistencyService.withDiagnostics(operatingPicture(query))

    fun operatingPictureWithDiagnostics(
        nowUtcMillis: Long,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
        selectedPath: PropagationAssessmentQuery? = null,
    ): PropagationOperatingPictureWithDiagnostics =
        operatingPictureWithDiagnostics(
            PropagationProjectionQuery(nowUtcMillis, filter, selectedPath)
        )

    /** Complete offline report DTO with no additional stores or provider reads. */
    fun offlineDiagnosticReport(
        query: PropagationProjectionQuery,
    ): PropagationOfflineDiagnosticReport =
        PropagationOfflineDiagnosticReportService.build(
            operatingPictureWithDiagnostics(query)
        )

    fun offlineDiagnosticReport(
        nowUtcMillis: Long,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
        selectedPath: PropagationAssessmentQuery? = null,
    ): PropagationOfflineDiagnosticReport =
        offlineDiagnosticReport(
            PropagationProjectionQuery(nowUtcMillis, filter, selectedPath)
        )

    fun refreshAndProject(
        query: PropagationProjectionQuery,
    ): PropagationRefreshWorkspaceResult =
        workspaceService.refreshAndProject(query)

    fun refreshAndProject(
        nowUtcMillis: Long,
        filter: PropagationProjectionFilter = PropagationProjectionFilter(),
        selectedPath: PropagationAssessmentQuery? = null,
    ): PropagationRefreshWorkspaceResult =
        refreshAndProject(
            PropagationProjectionQuery(
                nowUtcMillis = nowUtcMillis,
                filter = filter,
                selectedPath = selectedPath,
            )
        )
}

object PropagationRuntimeFactory {
    fun create(
        config: PropagationRuntimeConfig,
        transport: PublicPropagationTransport =
            HttpsUrlConnectionPublicPropagationTransport(
                config.httpsTransportConfig
            ),
        snapshotStore: PropagationSnapshotStore =
            InMemoryPropagationSnapshotStore(),
        refreshStateStore: PropagationRefreshStateStore =
            InMemoryPropagationRefreshStateStore(),
        aggregator: PropagationSnapshotAggregator =
            PropagationSnapshotAggregator(),
        assessmentEngine: PropagationAssessmentEngine =
            PropagationAssessmentEngine(),
    ): PropagationRuntime {
        val policies = config.refreshPolicies
        val sources = listOf(
            PublicPropagationSourceAdapters.noaaPlanetaryKp(
                transport = transport,
                policy = policies.planetaryKp,
            ),
            PublicPropagationSourceAdapters.noaaPlanetaryKpForecast(
                transport = transport,
                policy = policies.planetaryKpForecast,
            ),
            PublicPropagationSourceAdapters.noaaF107(
                transport = transport,
                policy = policies.f107,
            ),
            PublicPropagationSourceAdapters.noaaGlotec(
                transport = transport,
                policy = policies.glotec,
            ),
            PublicPropagationSourceAdapters.pskReporter(
                transport = transport,
                policy = policies.pskReporter,
                query = config.pskReporterQuery,
            ),
        )

        val coordinator = PropagationSourceRefreshCoordinator(
            sources = sources,
            snapshotStore = snapshotStore,
            stateStore = refreshStateStore,
            aggregator = aggregator,
        )
        val projectionService = PropagationWorkspaceProjectionService(
            store = snapshotStore,
            assessmentEngine = assessmentEngine,
        )
        val workspaceService = PropagationRefreshWorkspaceService(
            refreshCoordinator = coordinator,
            projectionService = projectionService,
        )

        return PropagationRuntime(
            config = config,
            sourceDefinitions = sources,
            snapshotStore = snapshotStore,
            refreshStateStore = refreshStateStore,
            workspaceService = workspaceService,
        )
    }
}
