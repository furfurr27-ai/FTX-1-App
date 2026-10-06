package dev.n0png.fieldops.core.map

import dev.n0png.fieldops.core.awards.AwardConfirmationEvidence
import dev.n0png.fieldops.core.awards.AwardEvidenceSnapshot
import dev.n0png.fieldops.core.awards.AwardModeGroup
import dev.n0png.fieldops.core.awards.AwardProgressState
import dev.n0png.fieldops.core.awards.AwardTargetEvidenceIndex
import dev.n0png.fieldops.core.awards.AwardTargetProvenance
import dev.n0png.fieldops.core.awards.AwardThresholdBasis
import dev.n0png.fieldops.core.awards.AwardsCenterQuery
import dev.n0png.fieldops.core.awards.OfficialAwardCatalog
import dev.n0png.fieldops.core.awards.OfficialAwardCatalogEntry
import dev.n0png.fieldops.core.awards.OfficialAwardProgress
import dev.n0png.fieldops.core.awards.OfficialAwardProgressEngine
import dev.n0png.fieldops.core.awards.OfficialAwardRuleShape
import dev.n0png.fieldops.core.awards.OfficialAwardTargetKind
import dev.n0png.fieldops.core.logbook.QsoRecord

enum class AwardAreaMapState {
    NEEDED,
    WORKED_UNCONFIRMED,
    CONFIRMED,
    LOCAL_THRESHOLD_MET,
}

data class AwardAreaGeometryIdentity(
    val targetKind: OfficialAwardTargetKind,
    val targetValue: String,
) {
    init {
        require(targetValue.isNotBlank()) { "Award map geometry target value must not be blank" }
    }

    val stableKey: String = "${targetKind.name}:${targetValue.trim().uppercase()}"
}

data class AwardAreaGeometrySource(
    val sourceId: String,
    val sourceVersion: String,
    val sourceUrl: String? = null,
    val licenseLabel: String? = null,
    val retrievedOn: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Award map geometry source id must not be blank" }
        require(sourceVersion.isNotBlank()) { "Award map geometry source version must not be blank" }
        require(sourceUrl == null || sourceUrl.startsWith("https://")) {
            "Award map geometry source URL must use HTTPS"
        }
        require(licenseLabel == null || licenseLabel.isNotBlank()) {
            "Award map geometry license label must not be blank"
        }
        retrievedOn?.let {
            require(it.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                "Award map geometry retrieval date must be YYYY-MM-DD"
            }
        }
    }
}

/**
 * Metadata-only binding to a separately managed geometry asset.
 *
 * CP-0007A deliberately does not carry polygons, coordinates, GeoJSON, or
 * platform map objects in the award projection. A future licensed/versioned
 * geometry pack can resolve geometryAssetId independently of award rules.
 */
data class AwardAreaGeometryBinding(
    val identity: AwardAreaGeometryIdentity,
    val source: AwardAreaGeometrySource,
    val geometryAssetId: String,
) {
    init {
        require(geometryAssetId.isNotBlank()) {
            "Award map geometry asset id must not be blank"
        }
    }
}

fun interface AwardAreaGeometryCatalog {
    fun binding(identity: AwardAreaGeometryIdentity): AwardAreaGeometryBinding?

    companion object {
        val EMPTY = AwardAreaGeometryCatalog { null }
    }
}

data class AwardAreaMapTarget(
    val awardId: String,
    val identity: AwardAreaGeometryIdentity,
    val states: List<AwardAreaMapState>,
    val qsoIds: List<Long>,
    val confirmedQsoIds: List<Long>,
    val bands: List<String>,
    val modeGroups: List<AwardModeGroup>,
    val exactModes: List<String>,
    val targetProvenance: List<AwardTargetProvenance>,
    val confirmationSources: List<String>,
    val geometry: AwardAreaGeometryBinding?,
) {
    init {
        require(awardId.isNotBlank()) { "Award map target requires an award id" }
        require(states.count { it in primaryStates } == 1) {
            "Award map target requires exactly one primary target state"
        }
        require(qsoIds.distinct().size == qsoIds.size) {
            "Award map target QSO ids must be distinct"
        }
        require(confirmedQsoIds.all { it in qsoIds }) {
            "Confirmed map-target QSO ids must also be worked QSO ids"
        }
        geometry?.let {
            require(it.identity == identity) {
                "Geometry binding identity must match award map target identity"
            }
        }
    }

    val primaryState: AwardAreaMapState
        get() = states.single { it in primaryStates }

    val localThresholdMet: Boolean
        get() = AwardAreaMapState.LOCAL_THRESHOLD_MET in states

    val workedQsoCount: Int
        get() = qsoIds.size

    val confirmedQsoCount: Int
        get() = confirmedQsoIds.size

    companion object {
        private val primaryStates = setOf(
            AwardAreaMapState.NEEDED,
            AwardAreaMapState.WORKED_UNCONFIRMED,
            AwardAreaMapState.CONFIRMED,
        )
    }
}

data class AwardAreaMapLayer(
    val awardId: String,
    val displayName: String,
    val targetKind: OfficialAwardTargetKind,
    val query: AwardsCenterQuery,
    val thresholdBasis: AwardThresholdBasis,
    val requiredDistinctTargets: Int,
    val requiredBands: Set<String>,
    val localThresholdMet: Boolean,
    val targetUniverseComplete: Boolean,
    val remainingToThresholdCount: Int,
    val targets: List<AwardAreaMapTarget>,
    val warnings: List<String>,
) {
    init {
        require(awardId.isNotBlank()) { "Award map layer requires an award id" }
        require(displayName.isNotBlank()) { "Award map layer requires a display name" }
        require(requiredDistinctTargets > 0) { "Award map threshold must be positive" }
        require(remainingToThresholdCount >= 0) {
            "Award map remaining-to-threshold count must be non-negative"
        }
        require(targets.all { it.awardId == awardId }) {
            "Award map layer may contain targets only for its award"
        }
        require(targets.map { it.identity.stableKey }.distinct().size == targets.size) {
            "Award map layer target identities must be unique"
        }
    }

    val neededCount: Int
        get() = targets.count { it.primaryState == AwardAreaMapState.NEEDED }

    val workedUnconfirmedCount: Int
        get() = targets.count { it.primaryState == AwardAreaMapState.WORKED_UNCONFIRMED }

    val confirmedCount: Int
        get() = targets.count { it.primaryState == AwardAreaMapState.CONFIRMED }

    val geometryBoundCount: Int
        get() = targets.count { it.geometry != null }

    val geometryUnboundCount: Int
        get() = targets.size - geometryBoundCount
}

class AwardAreaMapProjectionService(
    private val progressEngine: OfficialAwardProgressEngine = OfficialAwardProgressEngine(),
) {
    fun project(
        qsos: Iterable<QsoRecord>,
        evidence: AwardEvidenceSnapshot,
        query: AwardsCenterQuery = AwardsCenterQuery(),
        geometryCatalog: AwardAreaGeometryCatalog = AwardAreaGeometryCatalog.EMPTY,
    ): List<AwardAreaMapLayer> {
        val filteredQsos = qsos
            .filter { qso -> query.dateRange?.contains(qso.qsoDate) ?: true }
            .toList()
        val targetIndex = AwardTargetEvidenceIndex(evidence.targets)

        return OfficialAwardCatalog.entries
            .asSequence()
            .filter(::supportsMapProjection)
            .map { entry ->
                val progress = progressEngine.evaluate(
                    entry = entry,
                    qsos = filteredQsos,
                    targets = targetIndex,
                    confirmations = evidence.confirmations,
                    filter = query.evaluationFilter(),
                )
                layerFor(entry, progress, query, geometryCatalog)
            }
            .sortedBy { it.awardId }
            .toList()
    }

    private fun supportsMapProjection(entry: OfficialAwardCatalogEntry): Boolean =
        entry.requirement.ruleShape == OfficialAwardRuleShape.DISTINCT_TARGET_COUNT &&
            entry.requirement.targetKind in supportedTargetKinds

    private fun layerFor(
        entry: OfficialAwardCatalogEntry,
        progress: OfficialAwardProgress,
        query: AwardsCenterQuery,
        geometryCatalog: AwardAreaGeometryCatalog,
    ): AwardAreaMapLayer {
        val required = requireNotNull(progress.requiredDistinctTargets)
        val contributionByTarget = progress.contributions.groupBy { it.targetValue }

        val targetValues = linkedSetOf<String>().apply {
            entry.requirement.targetUniverse
                ?.map { it.trim().uppercase() }
                ?.sorted()
                ?.let(::addAll)
            addAll(progress.workedTargets.sorted())
            addAll(progress.confirmedTargets.sorted())
        }

        val mapTargets = targetValues
            .map { value ->
                val contributions = contributionByTarget[value].orEmpty()
                val identity = AwardAreaGeometryIdentity(
                    targetKind = entry.requirement.targetKind,
                    targetValue = value,
                )
                val geometry = geometryCatalog.binding(identity)?.also {
                    require(it.identity == identity) {
                        "Geometry catalog returned mismatched identity for ${identity.stableKey}"
                    }
                }

                AwardAreaMapTarget(
                    awardId = entry.id,
                    identity = identity,
                    states = statesFor(value, progress),
                    qsoIds = contributions.map { it.qsoId }.distinct().sorted(),
                    confirmedQsoIds = contributions
                        .filter { it.confirmed }
                        .map { it.qsoId }
                        .distinct()
                        .sorted(),
                    bands = contributions.map { it.band }.distinct().sorted(),
                    modeGroups = contributions.map { it.modeGroup }.distinct().sortedBy { it.name },
                    exactModes = contributions
                        .map { contribution ->
                            contribution.exactSubmode
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "${contribution.exactMode}/$it" }
                                ?: contribution.exactMode
                        }
                        .distinct()
                        .sorted(),
                    targetProvenance = contributions
                        .flatMap { it.targetProvenance }
                        .distinct()
                        .sortedWith(
                            compareBy<AwardTargetProvenance>(
                                { it.sourceId },
                                { it.sourceVersion },
                                { it.reference ?: "" },
                                { it.sourceUrl ?: "" },
                            )
                        ),
                    confirmationSources = contributions
                        .flatMap { it.acceptedConfirmationEvidence }
                        .map(AwardConfirmationEvidence::source)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted(),
                    geometry = geometry,
                )
            }
            .sortedBy { it.identity.stableKey }

        val counted = progress.thresholdCount.coerceAtMost(required)
        val universeKnown = entry.requirement.targetUniverse != null

        return AwardAreaMapLayer(
            awardId = entry.id,
            displayName = entry.displayName,
            targetKind = entry.requirement.targetKind,
            query = query,
            thresholdBasis = progress.thresholdBasis,
            requiredDistinctTargets = required,
            requiredBands = entry.requirement.requiredBands
                .mapTo(linkedSetOf()) { it.trim().lowercase() },
            localThresholdMet = progress.thresholdMet,
            targetUniverseComplete = universeKnown,
            remainingToThresholdCount = (required - counted).coerceAtLeast(0),
            targets = mapTargets,
            warnings = buildList {
                add("LOCAL_THRESHOLD_MET is local evaluator state, not sponsor claimability or award credit.")
                add("Geometry bindings are metadata references only; this projection never synthesizes coordinates or boundaries.")
                if (!universeKnown) {
                    add(
                        "This award has no finite target universe in the catalog; needed target identities are not fabricated. " +
                            "Use remainingToThresholdCount only as a numeric threshold remainder."
                    )
                }
            },
        )
    }

    private fun statesFor(
        targetValue: String,
        progress: OfficialAwardProgress,
    ): List<AwardAreaMapState> = buildList {
        add(
            when {
                targetValue in progress.confirmedTargets -> AwardAreaMapState.CONFIRMED
                targetValue in progress.workedTargets -> AwardAreaMapState.WORKED_UNCONFIRMED
                else -> AwardAreaMapState.NEEDED
            }
        )
        if (AwardProgressState.THRESHOLD_MET in progress.states) {
            add(AwardAreaMapState.LOCAL_THRESHOLD_MET)
        }
    }

    private companion object {
        val supportedTargetKinds = setOf(
            OfficialAwardTargetKind.US_STATE,
            OfficialAwardTargetKind.MAIDENHEAD_GRID4,
        )
    }
}
