package dev.n0png.fieldops.core.propagation

class PropagationWorkspaceProjectionService(
    private val store: PropagationSnapshotStore,
    private val assessmentEngine: PropagationAssessmentEngine = PropagationAssessmentEngine(),
) {
    fun latest(query: PropagationProjectionQuery): PropagationWorkspaceProjection? =
        store.latest()?.let { project(it, query) }

    fun snapshot(
        snapshotId: String,
        query: PropagationProjectionQuery,
    ): PropagationWorkspaceProjection? {
        require(snapshotId.isNotBlank()) { "Snapshot id must not be blank" }
        return store.snapshot(snapshotId)?.let { project(it, query) }
    }

    fun latestAtOrBefore(
        utcMillis: Long,
        query: PropagationProjectionQuery,
    ): PropagationWorkspaceProjection? {
        require(utcMillis >= 0) { "As-of UTC must be non-negative" }
        return store.latestAtOrBefore(utcMillis)?.let { project(it, query) }
    }

    fun project(
        snapshot: PropagationSnapshot,
        query: PropagationProjectionQuery,
    ): PropagationWorkspaceProjection {
        val filter = query.filter
        val bands = filter.normalizedBands
        val modes = filter.normalizedModes

        val heard = snapshot.heardPaths.asSequence()
            .filter { included(it, query) }
            .filter { bands.isEmpty() || it.normalizedBand in bands }
            .filter { modes.isEmpty() || it.normalizedMode in modes }
            .filter { filter.minimumFrequencyHz == null || it.frequencyHz >= filter.minimumFrequencyHz }
            .filter { filter.maximumFrequencyHz == null || it.frequencyHz <= filter.maximumFrequencyHz }
            .map { heard(it, query.nowUtcMillis) }
            .sortedWith(
                compareByDescending<HeardPathProjection> { it.metadata.observedAtUtcMillis }
                    .thenBy { it.metadata.evidenceId }
            )
            .toList()

        val iono = snapshot.ionosphericProducts.asSequence()
            .filter { included(it, query) }
            .map { iono(it, query.nowUtcMillis) }
            .sortedWith(
                compareByDescending<IonosphericProjection> { it.metadata.observedAtUtcMillis }
                    .thenBy { it.metadata.evidenceId }
            )
            .toList()

        val solar = snapshot.solarGeomagnetic.asSequence()
            .filter { included(it, query) }
            .map { solar(it, query.nowUtcMillis) }
            .sortedWith(
                compareByDescending<SolarGeomagneticProjection> { it.metadata.observedAtUtcMillis }
                    .thenBy { it.metadata.evidenceId }
            )
            .toList()

        val modeled = snapshot.modeledPaths.asSequence()
            .filter { included(it, query) }
            .map { modeled(it, query.nowUtcMillis) }
            .sortedWith(
                compareByDescending<ModeledPathProjection> { it.metadata.observedAtUtcMillis }
                    .thenBy { it.metadata.evidenceId }
            )
            .toList()

        val allEvidence = snapshot.allEvidence()
        val freshness = allEvidence.map {
            PropagationSourceFreshnessDefaults.classify(it, query.nowUtcMillis)
        }
        val retrievals = allEvidence.map { it.source.retrievedAtUtcMillis }
        val futureSnapshot = snapshot.capturedAtUtcMillis > query.nowUtcMillis

        return PropagationWorkspaceProjection(
            status = PropagationProjectionStatus(
                snapshotId = snapshot.snapshotId,
                capturedAtUtcMillis = snapshot.capturedAtUtcMillis,
                snapshotAgeMillis =
                    if (futureSnapshot) null else query.nowUtcMillis - snapshot.capturedAtUtcMillis,
                snapshotIsFutureDated = futureSnapshot,
                offlineCacheAvailable = store is OfflinePropagationSnapshotStore,
                sourceCount = snapshot.sourceIds.size,
                oldestSourceRetrievalAtUtcMillis = retrievals.minOrNull(),
                newestSourceRetrievalAtUtcMillis = retrievals.maxOrNull(),
                containsStaleEvidence = PropagationFreshness.STALE in freshness,
                containsFutureDatedEvidence = PropagationFreshness.FUTURE_DATED in freshness,
            ),
            heardPaths = heard,
            ionosphericProducts = iono,
            solarGeomagnetic = solar,
            modeledPaths = modeled,
            selectedPathAssessment = query.selectedPath?.let {
                assessmentEngine.assess(snapshot, it)
            },
        )
    }

    private fun included(
        evidence: PropagationEvidence,
        query: PropagationProjectionQuery,
    ): Boolean {
        val filter = query.filter
        if (filter.sourceIds.isNotEmpty() && evidence.source.sourceId !in filter.sourceIds) {
            return false
        }
        return PropagationSourceFreshnessDefaults.classify(
            evidence,
            query.nowUtcMillis,
        ) in filter.freshness
    }

    private fun metadata(
        evidence: PropagationEvidence,
        nowUtcMillis: Long,
    ): PropagationProjectionMetadata {
        val futureRetrieval = evidence.source.retrievedAtUtcMillis > nowUtcMillis
        return PropagationProjectionMetadata(
            evidenceId = evidence.evidenceId,
            source = evidence.source,
            observedAtUtcMillis = evidence.observedAtUtcMillis,
            freshness = PropagationSourceFreshnessDefaults.classify(evidence, nowUtcMillis),
            retrievalAgeMillis =
                if (futureRetrieval) null else nowUtcMillis - evidence.source.retrievedAtUtcMillis,
            retrievalIsFutureDated = futureRetrieval,
            confidence = evidence.confidence,
            quality = evidence.quality,
        )
    }

    private fun heard(evidence: HeardPathObservation, now: Long) =
        HeardPathProjection(
            metadata = metadata(evidence, now),
            transmitter = evidence.transmitter,
            receiver = evidence.receiver,
            frequencyHz = evidence.frequencyHz,
            band = evidence.band,
            mode = evidence.mode,
            snrDb = evidence.snrDb,
            reportCount = evidence.reportCount,
        )

    private fun iono(evidence: IonosphericMapProduct, now: Long) =
        IonosphericProjection(
            metadata = metadata(evidence, now),
            coverage = evidence.coverage,
            metric = evidence.metric,
            samples = evidence.samples,
            referenceDistanceKm = evidence.referenceDistanceKm,
            generatedAtUtcMillis = evidence.generatedAtUtcMillis,
        )

    private fun solar(evidence: SolarGeomagneticObservation, now: Long) =
        SolarGeomagneticProjection(
            metadata = metadata(evidence, now),
            f107SolarFluxSfu = evidence.f107SolarFluxSfu,
            planetaryKp = evidence.planetaryKp,
            planetaryAp = evidence.planetaryAp,
            sunspotNumber = evidence.sunspotNumber,
            xrayFluxWattsPerSquareMeter = evidence.xrayFluxWattsPerSquareMeter,
        )

    private fun modeled(evidence: ModeledPathEstimate, now: Long) =
        ModeledPathProjection(
            metadata = metadata(evidence, now),
            origin = evidence.origin,
            destination = evidence.destination,
            maximumUsableFrequencyHz = evidence.maximumUsableFrequencyHz,
            lowestUsableFrequencyHz = evidence.lowestUsableFrequencyHz,
            modelInputsSummary = evidence.modelInputsSummary,
        )
}
