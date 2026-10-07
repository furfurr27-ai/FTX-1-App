package dev.n0png.fieldops.core.propagation

import dev.n0png.fieldops.core.map.GeoBounds
import dev.n0png.fieldops.core.map.GeoCoordinate

enum class PropagationSourceClass {
    MEASUREMENT,
    DERIVED_PRODUCT,
    MODEL,
    FORECAST,
    SYNTHETIC_FIXTURE,
}

enum class PropagationDataQuality {
    VERIFIED,
    PROVISIONAL,
    ESTIMATED,
    INTERPOLATED,
    DEGRADED,
    SYNTHETIC,
}

enum class PropagationConfidenceBasis {
    DIRECT_MEASUREMENT,
    PROVIDER_REPORTED,
    MODEL_OUTPUT,
    DERIVED,
    SYNTHETIC,
}

data class PropagationSourceRef(
    val sourceId: String,
    val providerName: String,
    val sourceClass: PropagationSourceClass,
    val sourceVersion: String,
    val retrievedAtUtcMillis: Long,
    val sourceUrl: String? = null,
    val termsUrl: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Propagation source id must not be blank" }
        require(providerName.isNotBlank()) { "Propagation provider name must not be blank" }
        require(sourceVersion.isNotBlank()) { "Propagation source version must not be blank" }
        require(retrievedAtUtcMillis >= 0) { "Propagation retrieval UTC must be non-negative" }
        sourceUrl?.let {
            require(it.startsWith("https://")) { "Propagation source URL must use HTTPS" }
        }
        termsUrl?.let {
            require(it.startsWith("https://")) { "Propagation terms URL must use HTTPS" }
        }
        if (sourceClass == PropagationSourceClass.SYNTHETIC_FIXTURE) {
            require(sourceUrl == null) {
                "Synthetic propagation fixtures must not masquerade as live provider URLs"
            }
        }
    }
}

data class PropagationConfidence(
    val value: Double,
    val basis: PropagationConfidenceBasis,
    val explanation: String,
) {
    init {
        require(value in 0.0..1.0) { "Propagation confidence must be between 0 and 1" }
        require(explanation.isNotBlank()) { "Propagation confidence explanation must not be blank" }
    }
}

enum class PropagationLocationMethod {
    EXPLICIT_COORDINATE,
    EXPLICIT_GRID,
    PROVIDER_COORDINATE,
    STATION_PROFILE,
    SYNTHETIC_FIXTURE,
}

data class PropagationPosition(
    val coordinate: GeoCoordinate? = null,
    val maidenheadGrid: String? = null,
    val method: PropagationLocationMethod,
    val sourceReference: String? = null,
) {
    init {
        require(coordinate != null || !maidenheadGrid.isNullOrBlank()) {
            "Propagation position requires explicit coordinates or a Maidenhead grid"
        }
        maidenheadGrid?.let { raw ->
            val normalized = raw.trim().uppercase()
            require(normalized.matches(Regex("""[A-R]{2}[0-9]{2}([A-X]{2}([0-9]{2})?)?"""))) {
                "Propagation Maidenhead grid must be a valid 4, 6, or 8 character locator"
            }
        }
        sourceReference?.let {
            require(it.isNotBlank()) { "Propagation location source reference must not be blank" }
        }
        if (method == PropagationLocationMethod.EXPLICIT_COORDINATE) {
            require(coordinate != null) { "Explicit-coordinate position requires coordinates" }
        }
        if (method == PropagationLocationMethod.EXPLICIT_GRID) {
            require(!maidenheadGrid.isNullOrBlank()) { "Explicit-grid position requires a grid" }
        }
    }

    val normalizedGrid: String?
        get() = maidenheadGrid?.trim()?.uppercase()

    val stableKey: String
        get() = coordinate?.let {
            "COORD:${canonicalDouble(it.latitude)},${canonicalDouble(it.longitude)}"
        } ?: "GRID:${requireNotNull(normalizedGrid)}"

    private fun canonicalDouble(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

data class PropagationEndpoint(
    val location: PropagationPosition,
    val callsign: String? = null,
    val label: String? = null,
) {
    init {
        callsign?.let {
            require(it.isNotBlank()) { "Propagation endpoint callsign must not be blank" }
        }
        label?.let {
            require(it.isNotBlank()) { "Propagation endpoint label must not be blank" }
        }
    }

    val normalizedCallsign: String?
        get() = callsign?.trim()?.uppercase()
}

sealed interface PropagationCoverage

data object GlobalPropagationCoverage : PropagationCoverage

data class PointPropagationCoverage(
    val position: PropagationPosition,
) : PropagationCoverage

data class BoundsPropagationCoverage(
    val bounds: GeoBounds,
) : PropagationCoverage

data class PathPropagationCoverage(
    val origin: PropagationPosition,
    val destination: PropagationPosition,
) : PropagationCoverage {
    init {
        require(origin.stableKey != destination.stableKey) {
            "Propagation path coverage endpoints must be distinct"
        }
    }
}

data class PropagationFreshnessPolicy(
    val freshForMillis: Long,
    val usableForMillis: Long,
) {
    init {
        require(freshForMillis >= 0) { "Fresh propagation window must be non-negative" }
        require(usableForMillis >= freshForMillis) {
            "Usable propagation window must not be shorter than fresh window"
        }
    }

    companion object {
        val OPERATIONAL = PropagationFreshnessPolicy(
            freshForMillis = 15L * 60L * 1000L,
            usableForMillis = 60L * 60L * 1000L,
        )
    }
}

enum class PropagationFreshness {
    FRESH,
    AGING,
    STALE,
    FUTURE_DATED,
}

object PropagationFreshnessClassifier {
    fun classify(
        evidenceUtcMillis: Long,
        nowUtcMillis: Long,
        policy: PropagationFreshnessPolicy = PropagationFreshnessPolicy.OPERATIONAL,
    ): PropagationFreshness {
        require(evidenceUtcMillis >= 0) { "Propagation evidence UTC must be non-negative" }
        require(nowUtcMillis >= 0) { "Propagation current UTC must be non-negative" }
        if (evidenceUtcMillis > nowUtcMillis) return PropagationFreshness.FUTURE_DATED
        val age = nowUtcMillis - evidenceUtcMillis
        return when {
            age <= policy.freshForMillis -> PropagationFreshness.FRESH
            age <= policy.usableForMillis -> PropagationFreshness.AGING
            else -> PropagationFreshness.STALE
        }
    }
}

interface PropagationEvidence {
    val evidenceId: String
    val source: PropagationSourceRef
    val observedAtUtcMillis: Long
    val confidence: PropagationConfidence
    val quality: Set<PropagationDataQuality>
    val coverage: PropagationCoverage
}

data class SolarGeomagneticObservation(
    override val evidenceId: String,
    override val source: PropagationSourceRef,
    override val observedAtUtcMillis: Long,
    override val confidence: PropagationConfidence,
    override val quality: Set<PropagationDataQuality>,
    val f107SolarFluxSfu: Double? = null,
    val planetaryKp: Double? = null,
    val planetaryAp: Double? = null,
    val sunspotNumber: Double? = null,
    val xrayFluxWattsPerSquareMeter: Double? = null,
) : PropagationEvidence {
    override val coverage: PropagationCoverage = GlobalPropagationCoverage

    init {
        require(evidenceId.isNotBlank()) { "Solar/geomagnetic evidence id must not be blank" }
        require(observedAtUtcMillis >= 0) { "Solar/geomagnetic UTC must be non-negative" }
        require(quality.isNotEmpty()) { "Solar/geomagnetic observation requires quality metadata" }
        require(
            listOf(
                f107SolarFluxSfu,
                planetaryKp,
                planetaryAp,
                sunspotNumber,
                xrayFluxWattsPerSquareMeter,
            ).any { it != null }
        ) { "Solar/geomagnetic observation requires at least one measurement" }
        f107SolarFluxSfu?.let { require(it > 0.0) { "F10.7 flux must be positive" } }
        planetaryKp?.let { require(it in 0.0..9.0) { "Planetary Kp must be between 0 and 9" } }
        planetaryAp?.let { require(it >= 0.0) { "Planetary Ap must be non-negative" } }
        sunspotNumber?.let { require(it >= 0.0) { "Sunspot number must be non-negative" } }
        xrayFluxWattsPerSquareMeter?.let {
            require(it > 0.0) { "X-ray flux must be positive" }
        }
    }
}

enum class IonosphericMetric {
    FOF2_MHZ,
    MUF_MHZ,
    HMF2_KM,
}

data class IonosphericSample(
    val position: PropagationPosition,
    val value: Double,
    val confidence: PropagationConfidence? = null,
) {
    init {
        require(value > 0.0 && value.isFinite()) {
            "Ionospheric sample value must be finite and positive"
        }
    }
}

data class IonosphericMapProduct(
    override val evidenceId: String,
    override val source: PropagationSourceRef,
    override val observedAtUtcMillis: Long,
    override val confidence: PropagationConfidence,
    override val quality: Set<PropagationDataQuality>,
    override val coverage: PropagationCoverage,
    val metric: IonosphericMetric,
    val samples: List<IonosphericSample>,
    val referenceDistanceKm: Double? = null,
    val generatedAtUtcMillis: Long? = null,
) : PropagationEvidence {
    init {
        require(evidenceId.isNotBlank()) { "Ionospheric product evidence id must not be blank" }
        require(observedAtUtcMillis >= 0) { "Ionospheric product UTC must be non-negative" }
        generatedAtUtcMillis?.let {
            require(it >= 0) { "Ionospheric product generation UTC must be non-negative" }
            require(it <= source.retrievedAtUtcMillis) {
                "Ionospheric product generation time must not follow retrieval time"
            }
        }
        require(samples.isNotEmpty()) { "Ionospheric product requires samples" }
        require(quality.isNotEmpty()) { "Ionospheric product requires quality metadata" }
        if (metric == IonosphericMetric.MUF_MHZ) {
            require(referenceDistanceKm != null && referenceDistanceKm > 0.0) {
                "MUF product requires an explicit positive reference distance"
            }
        } else {
            require(referenceDistanceKm == null) {
                "Reference distance is only valid for MUF products"
            }
        }
    }
}

data class HeardPathObservation(
    override val evidenceId: String,
    override val source: PropagationSourceRef,
    override val observedAtUtcMillis: Long,
    override val confidence: PropagationConfidence,
    override val quality: Set<PropagationDataQuality>,
    val transmitter: PropagationEndpoint,
    val receiver: PropagationEndpoint,
    val frequencyHz: Long,
    val band: String,
    val mode: String,
    val snrDb: Double? = null,
    val reportCount: Int = 1,
) : PropagationEvidence {
    override val coverage: PropagationCoverage =
        PathPropagationCoverage(transmitter.location, receiver.location)

    init {
        require(evidenceId.isNotBlank()) { "Heard-path evidence id must not be blank" }
        require(observedAtUtcMillis >= 0) { "Heard-path UTC must be non-negative" }
        require(frequencyHz > 0) { "Heard-path frequency must be positive" }
        require(band.isNotBlank()) { "Heard-path band must not be blank" }
        require(mode.isNotBlank()) { "Heard-path mode must not be blank" }
        require(reportCount > 0) { "Heard-path report count must be positive" }
        require(quality.isNotEmpty()) { "Heard-path observation requires quality metadata" }
        snrDb?.let { require(it.isFinite()) { "Heard-path SNR must be finite" } }
    }

    val normalizedBand: String
        get() = band.trim().lowercase()

    val normalizedMode: String
        get() = mode.trim().uppercase()
}

data class ModeledPathEstimate(
    override val evidenceId: String,
    override val source: PropagationSourceRef,
    override val observedAtUtcMillis: Long,
    override val confidence: PropagationConfidence,
    override val quality: Set<PropagationDataQuality>,
    val origin: PropagationPosition,
    val destination: PropagationPosition,
    val maximumUsableFrequencyHz: Long? = null,
    val lowestUsableFrequencyHz: Long? = null,
    val modelInputsSummary: String,
) : PropagationEvidence {
    override val coverage: PropagationCoverage = PathPropagationCoverage(origin, destination)

    init {
        require(evidenceId.isNotBlank()) { "Modeled-path evidence id must not be blank" }
        require(observedAtUtcMillis >= 0) { "Modeled-path UTC must be non-negative" }
        require(source.sourceClass in setOf(PropagationSourceClass.MODEL, PropagationSourceClass.FORECAST, PropagationSourceClass.SYNTHETIC_FIXTURE)) {
            "Modeled-path estimate requires model/forecast/synthetic source provenance"
        }
        require(quality.isNotEmpty()) { "Modeled-path estimate requires quality metadata" }
        maximumUsableFrequencyHz?.let { require(it > 0) { "Modeled MUF must be positive" } }
        lowestUsableFrequencyHz?.let { require(it > 0) { "Modeled LUF must be positive" } }
        if (maximumUsableFrequencyHz != null && lowestUsableFrequencyHz != null) {
            require(lowestUsableFrequencyHz <= maximumUsableFrequencyHz) {
                "Modeled LUF must not exceed MUF"
            }
        }
        require(maximumUsableFrequencyHz != null || lowestUsableFrequencyHz != null) {
            "Modeled path estimate requires at least MUF or LUF"
        }
        require(modelInputsSummary.isNotBlank()) {
            "Modeled-path estimate must retain a human-readable input summary"
        }
    }
}

data class PropagationSnapshot(
    val snapshotId: String,
    val capturedAtUtcMillis: Long,
    val solarGeomagnetic: List<SolarGeomagneticObservation> = emptyList(),
    val ionosphericProducts: List<IonosphericMapProduct> = emptyList(),
    val heardPaths: List<HeardPathObservation> = emptyList(),
    val modeledPaths: List<ModeledPathEstimate> = emptyList(),
) {
    init {
        require(snapshotId.isNotBlank()) { "Propagation snapshot id must not be blank" }
        require(capturedAtUtcMillis >= 0) { "Propagation snapshot UTC must be non-negative" }

        val allEvidence = allEvidence()
        require(allEvidence.map { it.evidenceId }.distinct().size == allEvidence.size) {
            "Propagation evidence ids must be unique within a snapshot"
        }
        allEvidence.forEach { evidence ->
            require(evidence.source.retrievedAtUtcMillis <= capturedAtUtcMillis) {
                "Propagation snapshot cannot precede evidence retrieval"
            }
        }
    }

    fun allEvidence(): List<PropagationEvidence> =
        solarGeomagnetic + ionosphericProducts + heardPaths + modeledPaths

    val sourceIds: Set<String>
        get() = allEvidence().mapTo(linkedSetOf()) { it.source.sourceId }

    val observationCount: Int
        get() = solarGeomagnetic.size + ionosphericProducts.size + heardPaths.size

    val modeledCount: Int
        get() = modeledPaths.size
}

interface PropagationSnapshotStore {
    fun save(snapshot: PropagationSnapshot)
    fun latest(): PropagationSnapshot?
    fun snapshot(snapshotId: String): PropagationSnapshot?
    fun latestAtOrBefore(utcMillis: Long): PropagationSnapshot?
    fun history(limit: Int): List<PropagationSnapshot>
}

class InMemoryPropagationSnapshotStore : PropagationSnapshotStore {
    private val snapshots = linkedMapOf<String, PropagationSnapshot>()

    override fun save(snapshot: PropagationSnapshot) {
        val existing = snapshots[snapshot.snapshotId]
        require(existing == null || existing == snapshot) {
            "Propagation snapshot id ${snapshot.snapshotId} already exists with different content"
        }
        snapshots[snapshot.snapshotId] = snapshot
    }

    override fun latest(): PropagationSnapshot? =
        snapshots.values.maxWithOrNull(snapshotComparator)

    override fun snapshot(snapshotId: String): PropagationSnapshot? =
        snapshots[snapshotId]

    override fun latestAtOrBefore(utcMillis: Long): PropagationSnapshot? {
        require(utcMillis >= 0) { "Propagation lookup UTC must be non-negative" }
        return snapshots.values
            .asSequence()
            .filter { it.capturedAtUtcMillis <= utcMillis }
            .maxWithOrNull(snapshotComparator)
    }

    override fun history(limit: Int): List<PropagationSnapshot> {
        require(limit > 0) { "Propagation history limit must be positive" }
        return snapshots.values
            .sortedWith(snapshotComparator.reversed())
            .take(limit)
    }

    private companion object {
        val snapshotComparator =
            compareBy<PropagationSnapshot>({ it.capturedAtUtcMillis }, { it.snapshotId })
    }
}

data class PropagationAssessmentQuery(
    val band: String,
    val frequencyHz: Long,
    val origin: PropagationPosition,
    val destination: PropagationPosition? = null,
    val nowUtcMillis: Long,
    val freshnessPolicy: PropagationFreshnessPolicy = PropagationFreshnessPolicy.OPERATIONAL,
) {
    init {
        require(band.isNotBlank()) { "Propagation assessment band must not be blank" }
        require(frequencyHz > 0) { "Propagation assessment frequency must be positive" }
        require(nowUtcMillis >= 0) { "Propagation assessment UTC must be non-negative" }
        destination?.let {
            require(it.stableKey != origin.stableKey) {
                "Propagation assessment origin and destination must be distinct"
            }
        }
    }

    val normalizedBand: String
        get() = band.trim().lowercase()
}

enum class PropagationUsability {
    GOOD,
    MARGINAL,
    POOR,
    UNKNOWN,
}

enum class PropagationAssessmentReasonCode {
    RECENT_OBSERVED_PATH,
    AGING_OBSERVED_PATH,
    MODEL_FREQUENCY_WITHIN_LIMITS,
    FREQUENCY_ABOVE_MODELED_MUF,
    FREQUENCY_BELOW_MODELED_LUF,
    MODEL_ONLY_NO_OBSERVED_PATH,
    GEOMAGNETIC_STORM_CAUTION,
    SOLAR_CONTEXT_AVAILABLE,
    IONOSPHERIC_MAP_CONTEXT_AVAILABLE,
    STALE_EVIDENCE_IGNORED,
    FUTURE_DATED_EVIDENCE_IGNORED,
    INSUFFICIENT_PATH_EVIDENCE,
}

data class PropagationAssessmentReason(
    val code: PropagationAssessmentReasonCode,
    val explanation: String,
    val evidenceIds: List<String> = emptyList(),
) {
    init {
        require(explanation.isNotBlank()) { "Propagation assessment explanation must not be blank" }
        require(evidenceIds.distinct().size == evidenceIds.size) {
            "Propagation assessment evidence ids must be distinct"
        }
    }
}

data class PropagationPathAssessment(
    val usability: PropagationUsability,
    val confidence: PropagationConfidence?,
    val reasons: List<PropagationAssessmentReason>,
    val evidenceIds: List<String>,
) {
    init {
        require(reasons.isNotEmpty()) { "Propagation assessment requires at least one explanation" }
        require(evidenceIds.distinct().size == evidenceIds.size) {
            "Propagation assessment evidence ids must be distinct"
        }
    }
}

class PropagationAssessmentEngine {
    fun assess(
        snapshot: PropagationSnapshot,
        query: PropagationAssessmentQuery,
    ): PropagationPathAssessment {
        val reasons = mutableListOf<PropagationAssessmentReason>()
        val usedEvidenceIds = linkedSetOf<String>()

        val heardCandidates = snapshot.heardPaths
            .filter { it.normalizedBand == query.normalizedBand }
            .filter { matchesQueryPath(it.transmitter.location, it.receiver.location, query) }

        val freshHeard = heardCandidates.filter {
            freshness(it.observedAtUtcMillis, query) == PropagationFreshness.FRESH
        }
        val agingHeard = heardCandidates.filter {
            freshness(it.observedAtUtcMillis, query) == PropagationFreshness.AGING
        }

        if (freshHeard.isNotEmpty()) {
            val ids = freshHeard.map { it.evidenceId }.sorted()
            usedEvidenceIds += ids
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.RECENT_OBSERVED_PATH,
                explanation = "Recent directly observed/heard RF path evidence exists on the selected band.",
                evidenceIds = ids,
            )
        } else if (agingHeard.isNotEmpty()) {
            val ids = agingHeard.map { it.evidenceId }.sorted()
            usedEvidenceIds += ids
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.AGING_OBSERVED_PATH,
                explanation = "Observed/heard path evidence exists but is outside the fresh window.",
                evidenceIds = ids,
            )
        }

        val modelCandidates = snapshot.modeledPaths
            .filter { matchesQueryPath(it.origin, it.destination, query) }
            .filter {
                freshness(it.observedAtUtcMillis, query) in setOf(
                    PropagationFreshness.FRESH,
                    PropagationFreshness.AGING,
                )
            }

        val relevantModel = modelCandidates
            .sortedWith(
                compareByDescending<ModeledPathEstimate> { it.observedAtUtcMillis }
                    .thenByDescending { it.confidence.value }
                    .thenBy { it.evidenceId }
            )
            .firstOrNull()

        var modelWithin = false
        var modelOutside = false
        relevantModel?.let { model ->
            usedEvidenceIds += model.evidenceId
            val aboveMuf = model.maximumUsableFrequencyHz
                ?.let { query.frequencyHz > it }
                ?: false
            val belowLuf = model.lowestUsableFrequencyHz
                ?.let { query.frequencyHz < it }
                ?: false
            when {
                aboveMuf -> {
                    modelOutside = true
                    reasons += PropagationAssessmentReason(
                        code = PropagationAssessmentReasonCode.FREQUENCY_ABOVE_MODELED_MUF,
                        explanation = "Selected frequency is above the model-provided maximum usable frequency for this path.",
                        evidenceIds = listOf(model.evidenceId),
                    )
                }

                belowLuf -> {
                    modelOutside = true
                    reasons += PropagationAssessmentReason(
                        code = PropagationAssessmentReasonCode.FREQUENCY_BELOW_MODELED_LUF,
                        explanation = "Selected frequency is below the model-provided lowest usable frequency for this path.",
                        evidenceIds = listOf(model.evidenceId),
                    )
                }

                else -> {
                    modelWithin = true
                    reasons += PropagationAssessmentReason(
                        code = PropagationAssessmentReasonCode.MODEL_FREQUENCY_WITHIN_LIMITS,
                        explanation = "Selected frequency falls within the explicit model-provided path frequency limits.",
                        evidenceIds = listOf(model.evidenceId),
                    )
                }
            }
            if (freshHeard.isEmpty() && agingHeard.isEmpty()) {
                reasons += PropagationAssessmentReason(
                    code = PropagationAssessmentReasonCode.MODEL_ONLY_NO_OBSERVED_PATH,
                    explanation = "Path assessment is model-supported but has no matching observed/heard RF path in this snapshot.",
                    evidenceIds = listOf(model.evidenceId),
                )
            }
        }

        val freshSolar = snapshot.solarGeomagnetic
            .filter {
                freshness(it.observedAtUtcMillis, query) in setOf(
                    PropagationFreshness.FRESH,
                    PropagationFreshness.AGING,
                )
            }
            .sortedByDescending { it.observedAtUtcMillis }
            .firstOrNull()

        freshSolar?.let { solar ->
            usedEvidenceIds += solar.evidenceId
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.SOLAR_CONTEXT_AVAILABLE,
                explanation = "Current solar/geomagnetic context is available as supporting context, not as an observed RF path.",
                evidenceIds = listOf(solar.evidenceId),
            )
            if (solar.planetaryKp?.let { it >= 5.0 } == true) {
                reasons += PropagationAssessmentReason(
                    code = PropagationAssessmentReasonCode.GEOMAGNETIC_STORM_CAUTION,
                    explanation = "Planetary Kp is at or above 5; disturbed geomagnetic conditions are flagged as cautionary context.",
                    evidenceIds = listOf(solar.evidenceId),
                )
            }
        }

        val usableIonospheric = snapshot.ionosphericProducts.filter {
            freshness(it.observedAtUtcMillis, query) in setOf(
                PropagationFreshness.FRESH,
                PropagationFreshness.AGING,
            )
        }
        if (usableIonospheric.isNotEmpty()) {
            val ids = usableIonospheric.map { it.evidenceId }.sorted()
            usedEvidenceIds += ids
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.IONOSPHERIC_MAP_CONTEXT_AVAILABLE,
                explanation = "Fresh or aging ionospheric map context is available; this foundation does not interpolate it into a path prediction.",
                evidenceIds = ids,
            )
        }

        val stale = snapshot.allEvidence().filter {
            freshness(it.observedAtUtcMillis, query) == PropagationFreshness.STALE
        }
        if (stale.isNotEmpty()) {
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.STALE_EVIDENCE_IGNORED,
                explanation = "Stale propagation evidence was retained for inspection but ignored for the current usability decision.",
                evidenceIds = stale.map { it.evidenceId }.sorted(),
            )
        }

        val future = snapshot.allEvidence().filter {
            freshness(it.observedAtUtcMillis, query) == PropagationFreshness.FUTURE_DATED
        }
        if (future.isNotEmpty()) {
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.FUTURE_DATED_EVIDENCE_IGNORED,
                explanation = "Future-dated evidence was ignored rather than treated as current propagation state.",
                evidenceIds = future.map { it.evidenceId }.sorted(),
            )
        }

        val usability = when {
            freshHeard.isNotEmpty() -> PropagationUsability.GOOD
            agingHeard.isNotEmpty() -> PropagationUsability.MARGINAL
            modelOutside -> PropagationUsability.POOR
            modelWithin -> PropagationUsability.MARGINAL
            else -> PropagationUsability.UNKNOWN
        }

        if (
            freshHeard.isEmpty() &&
            agingHeard.isEmpty() &&
            relevantModel == null
        ) {
            reasons += PropagationAssessmentReason(
                code = PropagationAssessmentReasonCode.INSUFFICIENT_PATH_EVIDENCE,
                explanation = "No fresh/aging matching heard path or explicit modeled path limits are available for this query.",
            )
        }

        val selectedConfidence = when {
            freshHeard.isNotEmpty() -> freshHeard.maxBy { it.confidence.value }.confidence
            agingHeard.isNotEmpty() -> agingHeard.maxBy { it.confidence.value }.confidence
            relevantModel != null -> relevantModel.confidence
            else -> null
        }

        return PropagationPathAssessment(
            usability = usability,
            confidence = selectedConfidence,
            reasons = reasons.distinctBy { it.code to it.evidenceIds },
            evidenceIds = usedEvidenceIds.toList().sorted(),
        )
    }

    private fun freshness(
        evidenceUtcMillis: Long,
        query: PropagationAssessmentQuery,
    ): PropagationFreshness =
        PropagationFreshnessClassifier.classify(
            evidenceUtcMillis = evidenceUtcMillis,
            nowUtcMillis = query.nowUtcMillis,
            policy = query.freshnessPolicy,
        )

    private fun matchesQueryPath(
        origin: PropagationPosition,
        destination: PropagationPosition,
        query: PropagationAssessmentQuery,
    ): Boolean {
        val requestedDestination = query.destination
        if (requestedDestination == null) {
            return origin.stableKey == query.origin.stableKey ||
                destination.stableKey == query.origin.stableKey
        }

        val forward =
            origin.stableKey == query.origin.stableKey &&
                destination.stableKey == requestedDestination.stableKey
        val reverse =
            destination.stableKey == query.origin.stableKey &&
                origin.stableKey == requestedDestination.stableKey
        return forward || reverse
    }
}
