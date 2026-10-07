package dev.n0png.fieldops.core.propagation

data class PropagationProjectionFilter(
    val bands: Set<String> = emptySet(),
    val minimumFrequencyHz: Long? = null,
    val maximumFrequencyHz: Long? = null,
    val modes: Set<String> = emptySet(),
    val sourceIds: Set<String> = emptySet(),
    val freshness: Set<PropagationFreshness> = PropagationFreshness.entries.toSet(),
) {
    init {
        require(bands.none { it.isBlank() }) { "Band filters must not be blank" }
        require(modes.none { it.isBlank() }) { "Mode filters must not be blank" }
        require(sourceIds.none { it.isBlank() }) { "Source filters must not be blank" }
        minimumFrequencyHz?.let { require(it > 0) { "Minimum frequency must be positive" } }
        maximumFrequencyHz?.let { require(it > 0) { "Maximum frequency must be positive" } }
        if (minimumFrequencyHz != null && maximumFrequencyHz != null) {
            require(minimumFrequencyHz <= maximumFrequencyHz) {
                "Minimum frequency must not exceed maximum frequency"
            }
        }
        require(freshness.isNotEmpty()) { "Freshness filter must not be empty" }
    }

    val normalizedBands: Set<String>
        get() = bands.mapTo(linkedSetOf()) { it.trim().lowercase() }

    val normalizedModes: Set<String>
        get() = modes.mapTo(linkedSetOf()) { it.trim().uppercase() }
}

data class PropagationProjectionQuery(
    val nowUtcMillis: Long,
    val filter: PropagationProjectionFilter = PropagationProjectionFilter(),
    val selectedPath: PropagationAssessmentQuery? = null,
) {
    init {
        require(nowUtcMillis >= 0) { "Projection UTC must be non-negative" }
        selectedPath?.let {
            require(it.nowUtcMillis == nowUtcMillis) {
                "Selected-path assessment UTC must match projection UTC"
            }
        }
    }
}

data class PropagationProjectionMetadata(
    val evidenceId: String,
    val source: PropagationSourceRef,
    val observedAtUtcMillis: Long,
    val freshness: PropagationFreshness,
    val retrievalAgeMillis: Long?,
    val retrievalIsFutureDated: Boolean,
    val confidence: PropagationConfidence,
    val quality: Set<PropagationDataQuality>,
)

data class HeardPathProjection(
    val metadata: PropagationProjectionMetadata,
    val transmitter: PropagationEndpoint,
    val receiver: PropagationEndpoint,
    val frequencyHz: Long,
    val band: String,
    val mode: String,
    val snrDb: Double?,
    val reportCount: Int,
)

data class IonosphericProjection(
    val metadata: PropagationProjectionMetadata,
    val coverage: PropagationCoverage,
    val metric: IonosphericMetric,
    val samples: List<IonosphericSample>,
    val referenceDistanceKm: Double?,
    val generatedAtUtcMillis: Long?,
)

data class SolarGeomagneticProjection(
    val metadata: PropagationProjectionMetadata,
    val f107SolarFluxSfu: Double?,
    val planetaryKp: Double?,
    val planetaryAp: Double?,
    val sunspotNumber: Double?,
    val xrayFluxWattsPerSquareMeter: Double?,
)

data class ModeledPathProjection(
    val metadata: PropagationProjectionMetadata,
    val origin: PropagationPosition,
    val destination: PropagationPosition,
    val maximumUsableFrequencyHz: Long?,
    val lowestUsableFrequencyHz: Long?,
    val modelInputsSummary: String,
)

data class PropagationProjectionStatus(
    val snapshotId: String,
    val capturedAtUtcMillis: Long,
    val snapshotAgeMillis: Long?,
    val snapshotIsFutureDated: Boolean,
    val offlineCacheAvailable: Boolean,
    val sourceCount: Int,
    val oldestSourceRetrievalAtUtcMillis: Long?,
    val newestSourceRetrievalAtUtcMillis: Long?,
    val containsStaleEvidence: Boolean,
    val containsFutureDatedEvidence: Boolean,
)

data class PropagationWorkspaceProjection(
    val status: PropagationProjectionStatus,
    val heardPaths: List<HeardPathProjection>,
    val ionosphericProducts: List<IonosphericProjection>,
    val solarGeomagnetic: List<SolarGeomagneticProjection>,
    val modeledPaths: List<ModeledPathProjection>,
    val selectedPathAssessment: PropagationPathAssessment?,
) {
    val projectedEvidenceCount: Int
        get() = heardPaths.size + ionosphericProducts.size + solarGeomagnetic.size + modeledPaths.size
}

interface OfflinePropagationSnapshotStore : PropagationSnapshotStore
