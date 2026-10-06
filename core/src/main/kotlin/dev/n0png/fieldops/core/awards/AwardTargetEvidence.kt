package dev.n0png.fieldops.core.awards

import dev.n0png.fieldops.core.logbook.QsoRecord

/**
 * Provenance-bearing award enrichment is kept outside QsoRecord.
 *
 * A QSO remains authoritative and immutable with respect to award metadata.
 * Enrichment can be replaced/versioned independently as better authoritative
 * data becomes available.
 */
data class AwardTargetProvenance(
    val sourceId: String,
    val sourceVersion: String,
    val sourceUrl: String? = null,
    val reference: String? = null,
    val retrievedAtUtcMillis: Long? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Award target provenance source id must not be blank" }
        require(sourceVersion.isNotBlank()) { "Award target provenance source version must not be blank" }
        require(sourceUrl == null || sourceUrl.startsWith("https://")) {
            "Award target provenance URL must use HTTPS"
        }
        require(reference == null || reference.isNotBlank()) {
            "Award target provenance reference must not be blank"
        }
        retrievedAtUtcMillis?.let {
            require(it >= 0) { "Award target provenance UTC must be non-negative" }
        }
    }
}

data class AwardTargetEvidence(
    val qsoId: Long,
    val kind: OfficialAwardTargetKind,
    val value: String,
    val provenance: AwardTargetProvenance,
) {
    init {
        require(qsoId > 0) { "Award target evidence QSO id must be positive" }
        require(value.isNotBlank()) { "Award target evidence value must not be blank" }
        require(kind in AwardTargetNormalizer.supportedKinds) {
            "Award target kind $kind is not directly enrichable in CP-0006C"
        }
    }

    val normalizedValue: String = AwardTargetNormalizer.normalize(kind, value)
}

object AwardTargetNormalizer {
    val supportedKinds = setOf(
        OfficialAwardTargetKind.DXCC_ENTITY,
        OfficialAwardTargetKind.US_STATE,
        OfficialAwardTargetKind.CONTINENT,
        OfficialAwardTargetKind.IOTA_GROUP,
        OfficialAwardTargetKind.POTA_REFERENCE,
        OfficialAwardTargetKind.MAIDENHEAD_GRID4,
    )

    private val continentAliases = mapOf(
        "AF" to "AFRICA",
        "AFRICA" to "AFRICA",
        "AS" to "ASIA",
        "ASIA" to "ASIA",
        "EU" to "EUROPE",
        "EUROPE" to "EUROPE",
        "NA" to "NORTH_AMERICA",
        "NORTH AMERICA" to "NORTH_AMERICA",
        "NORTH_AMERICA" to "NORTH_AMERICA",
        "OC" to "OCEANIA",
        "OCEANIA" to "OCEANIA",
        "SA" to "SOUTH_AMERICA",
        "SOUTH AMERICA" to "SOUTH_AMERICA",
        "SOUTH_AMERICA" to "SOUTH_AMERICA",
        "AN" to "ANTARCTICA",
        "ANTARCTICA" to "ANTARCTICA",
    )

    fun normalize(kind: OfficialAwardTargetKind, value: String): String {
        require(kind in supportedKinds) { "Unsupported direct award target kind: $kind" }
        val raw = value.trim().uppercase()
        require(raw.isNotEmpty()) { "Award target value must not be blank" }

        return when (kind) {
            OfficialAwardTargetKind.DXCC_ENTITY -> {
                require(raw.all(Char::isDigit) && raw.toIntOrNull()?.let { it > 0 } == true) {
                    "DXCC entity evidence must be a positive numeric entity id"
                }
                raw.toInt().toString()
            }

            OfficialAwardTargetKind.US_STATE -> {
                require(raw.matches(Regex("[A-Z]{2}"))) {
                    "U.S. state evidence must be a two-letter abbreviation"
                }
                raw
            }

            OfficialAwardTargetKind.CONTINENT ->
                requireNotNull(continentAliases[raw]) {
                    "Continent evidence must be a canonical continent name or ADIF continent code"
                }

            OfficialAwardTargetKind.IOTA_GROUP -> {
                require(raw.matches(Regex("(AF|AN|AS|EU|NA|OC|SA)-[0-9]{3}"))) {
                    "IOTA evidence must look like EU-005"
                }
                raw
            }

            OfficialAwardTargetKind.POTA_REFERENCE -> {
                require(raw.matches(Regex("[A-Z0-9]{1,8}-[A-Z0-9]{1,12}"))) {
                    "POTA evidence must be a normalized program reference"
                }
                raw
            }

            OfficialAwardTargetKind.MAIDENHEAD_GRID4 -> {
                require(raw.matches(Regex("[A-R]{2}[0-9]{2}([A-X]{2}([0-9]{2})?)?"))) {
                    "Maidenhead grid evidence must be a valid 4, 6, or 8 character locator"
                }
                raw.take(4)
            }

            else -> error("Unsupported direct award target kind: $kind")
        }
    }
}

class AwardTargetEvidenceIndex(evidence: Iterable<AwardTargetEvidence>) {
    private val byQsoAndKind: Map<Pair<Long, OfficialAwardTargetKind>, List<AwardTargetEvidence>>

    init {
        val distinct = evidence.toList().distinct()
        byQsoAndKind = distinct.groupBy { it.qsoId to it.kind }

        val singleValuedKinds = setOf(
            OfficialAwardTargetKind.DXCC_ENTITY,
            OfficialAwardTargetKind.US_STATE,
            OfficialAwardTargetKind.CONTINENT,
            OfficialAwardTargetKind.IOTA_GROUP,
            OfficialAwardTargetKind.MAIDENHEAD_GRID4,
        )
        byQsoAndKind.forEach { (key, records) ->
            if (key.second in singleValuedKinds) {
                val values = records.map { it.normalizedValue }.toSet()
                require(values.size <= 1) {
                    "Conflicting ${key.second} evidence for immutable QSO ${key.first}: $values"
                }
            }
        }
    }

    fun evidence(qsoId: Long, kind: OfficialAwardTargetKind): List<AwardTargetEvidence> =
        byQsoAndKind[qsoId to kind].orEmpty()

    fun values(qsoId: Long, kind: OfficialAwardTargetKind): Set<String> =
        evidence(qsoId, kind).mapTo(linkedSetOf()) { it.normalizedValue }

    fun singleValue(qsoId: Long, kind: OfficialAwardTargetKind): String? =
        values(qsoId, kind).singleOrNull()
}

data class OfficialAwardContribution(
    val qsoId: Long,
    val targetKind: OfficialAwardTargetKind,
    val targetValue: String,
    val band: String,
    val exactMode: String,
    val exactSubmode: String?,
    val modeGroup: AwardModeGroup,
    val targetProvenance: List<AwardTargetProvenance>,
    val acceptedConfirmationEvidence: List<AwardConfirmationEvidence>,
) {
    val confirmed: Boolean
        get() = acceptedConfirmationEvidence.isNotEmpty()
}

data class OfficialAwardCompositeProgress(
    val requiredCoverage: Set<String> = emptySet(),
    val achievedCoverage: Set<String> = emptySet(),
    val missingCoverage: Set<String> = emptySet(),
    val requiredCells: Set<String> = emptySet(),
    val workedCells: Set<String> = emptySet(),
    val confirmedCells: Set<String> = emptySet(),
    val missingCells: Set<String> = emptySet(),
)

data class OfficialAwardProgress(
    val awardId: String,
    val displayName: String,
    val filter: AwardEvaluationFilter,
    val states: Set<AwardProgressState>,
    val thresholdBasis: AwardThresholdBasis,
    val requiredDistinctTargets: Int?,
    val workedTargets: Set<String>,
    val confirmedTargets: Set<String>,
    val remainingTargets: Set<String>?,
    val contributions: List<OfficialAwardContribution>,
    val composite: OfficialAwardCompositeProgress? = null,
) {
    val workedCount: Int
        get() = workedTargets.size

    val confirmedCount: Int
        get() = confirmedTargets.size

    val thresholdCount: Int
        get() = when (thresholdBasis) {
            AwardThresholdBasis.WORKED -> workedCount
            AwardThresholdBasis.CONFIRMED -> confirmedCount
        }

    val thresholdMet: Boolean
        get() = AwardProgressState.THRESHOLD_MET in states
}

class OfficialAwardProgressEngine(
    private val modeGrouper: ControlledAwardModeGrouper = ControlledAwardModeGrouper.STANDARD,
) {
    fun evaluate(
        entry: OfficialAwardCatalogEntry,
        qsos: Iterable<QsoRecord>,
        targets: AwardTargetEvidenceIndex,
        confirmations: Iterable<AwardConfirmationEvidence> = emptyList(),
        filter: AwardEvaluationFilter = AwardEvaluationFilter(),
    ): OfficialAwardProgress {
        val confirmationIndex = confirmations
            .groupBy { it.qsoId }
            .mapValues { (_, values) -> values.distinct() }

        return when (entry.requirement.ruleShape) {
            OfficialAwardRuleShape.DISTINCT_TARGET_COUNT ->
                evaluateDistinct(entry, qsos, targets, confirmationIndex, filter)

            OfficialAwardRuleShape.MODE_TARGET_MATRIX ->
                evaluateModeTargetMatrix(entry, qsos, targets, confirmationIndex, filter)

            OfficialAwardRuleShape.COUNT_PLUS_REQUIRED_COVERAGE ->
                evaluateCountPlusCoverage(entry, qsos, targets, confirmationIndex, filter)

            OfficialAwardRuleShape.POINTS ->
                throw IllegalArgumentException(
                    "Award ${entry.id} uses external/program point scoring and is not locally evaluated"
                )
        }
    }

    private fun evaluateDistinct(
        entry: OfficialAwardCatalogEntry,
        qsos: Iterable<QsoRecord>,
        targets: AwardTargetEvidenceIndex,
        confirmations: Map<Long, List<AwardConfirmationEvidence>>,
        filter: AwardEvaluationFilter,
    ): OfficialAwardProgress {
        val requirement = entry.requirement
        require(requirement.targetKind in AwardTargetNormalizer.supportedKinds) {
            "Distinct-target award ${entry.id} has no direct enrichment target"
        }
        val required = requireNotNull(requirement.requiredDistinctTargets)

        val contributions = mutableListOf<OfficialAwardContribution>()
        for (qso in qsos) {
            if (!qsoEligible(qso, requirement, filter)) continue

            val acceptedConfirmation = acceptedConfirmations(
                requirement.confirmationPolicy,
                confirmations[qso.id].orEmpty(),
            )
            val groupedEvidence = targets.evidence(qso.id, requirement.targetKind)
                .groupBy { canonicalTarget(entry, it.normalizedValue) }

            for ((targetValue, evidence) in groupedEvidence) {
                if (!targetAllowed(requirement, targetValue)) continue
                contributions += contribution(
                    qso = qso,
                    kind = requirement.targetKind,
                    targetValue = targetValue,
                    targetEvidence = evidence,
                    confirmations = acceptedConfirmation,
                )
            }
        }

        val workedTargets = contributions.mapTo(linkedSetOf()) { it.targetValue }
        val confirmedTargets = contributions
            .asSequence()
            .filter { it.confirmed }
            .mapTo(linkedSetOf()) { it.targetValue }

        val countedTargets = when (requirement.thresholdBasis) {
            AwardThresholdBasis.WORKED -> workedTargets
            AwardThresholdBasis.CONFIRMED -> confirmedTargets
        }

        val states = basicStates(
            worked = workedTargets,
            confirmed = confirmedTargets,
            thresholdMet = countedTargets.size >= required,
        )

        val remainingTargets = requirement.targetUniverse?.let { universe ->
            universe.mapTo(linkedSetOf()) { canonicalTarget(entry, it.trim().uppercase()) } - countedTargets
        }

        return OfficialAwardProgress(
            awardId = entry.id,
            displayName = entry.displayName,
            filter = filter,
            states = states,
            thresholdBasis = requirement.thresholdBasis,
            requiredDistinctTargets = required,
            workedTargets = workedTargets,
            confirmedTargets = confirmedTargets,
            remainingTargets = remainingTargets,
            contributions = contributions.sortedWith(compareBy({ it.targetValue }, { it.qsoId })),
        )
    }

    private fun evaluateModeTargetMatrix(
        entry: OfficialAwardCatalogEntry,
        qsos: Iterable<QsoRecord>,
        targets: AwardTargetEvidenceIndex,
        confirmations: Map<Long, List<AwardConfirmationEvidence>>,
        filter: AwardEvaluationFilter,
    ): OfficialAwardProgress {
        val requirement = entry.requirement
        require(requirement.targetKind == OfficialAwardTargetKind.MODE_STATE_PAIR) {
            "CP-0006C matrix evaluator supports mode/state awards"
        }
        require(requirement.requiredCoverage.isNotEmpty()) {
            "Mode/state matrix requires a state coverage set"
        }
        require(requirement.requiredModeGroups.isNotEmpty()) {
            "Mode/state matrix requires mode groups"
        }

        val requiredStates = requirement.requiredCoverage
            .mapTo(linkedSetOf()) { canonicalTarget(entry, it.trim().uppercase()) }
        val requiredCells = linkedSetOf<String>()
        for (state in requiredStates.sorted()) {
            for (mode in requirement.requiredModeGroups.sortedBy { it.name }) {
                requiredCells += matrixCell(state, mode)
            }
        }

        val contributions = mutableListOf<OfficialAwardContribution>()
        val workedCells = linkedSetOf<String>()
        val confirmedCells = linkedSetOf<String>()

        for (qso in qsos) {
            if (!qsoEligible(qso, requirement, filter)) continue
            val modeGroup = modeGrouper.groupFor(qso)
            if (modeGroup !in requirement.requiredModeGroups) continue

            val stateEvidence = targets.evidence(qso.id, OfficialAwardTargetKind.US_STATE)
            if (stateEvidence.isEmpty()) continue
            val state = canonicalTarget(entry, stateEvidence.first().normalizedValue)
            if (state !in requiredStates) continue

            val acceptedConfirmation = acceptedConfirmations(
                requirement.confirmationPolicy,
                confirmations[qso.id].orEmpty(),
            )
            val cell = matrixCell(state, modeGroup)
            workedCells += cell
            if (acceptedConfirmation.isNotEmpty()) confirmedCells += cell

            contributions += contribution(
                qso = qso,
                kind = OfficialAwardTargetKind.US_STATE,
                targetValue = state,
                targetEvidence = stateEvidence,
                confirmations = acceptedConfirmation,
            )
        }

        val workedTargets = contributions.mapTo(linkedSetOf()) { it.targetValue }
        val confirmedTargets = contributions
            .asSequence()
            .filter { it.confirmed }
            .mapTo(linkedSetOf()) { it.targetValue }

        val states = basicStates(
            worked = workedTargets,
            confirmed = confirmedTargets,
            thresholdMet = confirmedCells.containsAll(requiredCells),
        )

        return OfficialAwardProgress(
            awardId = entry.id,
            displayName = entry.displayName,
            filter = filter,
            states = states,
            thresholdBasis = requirement.thresholdBasis,
            requiredDistinctTargets = requirement.requiredDistinctTargets,
            workedTargets = workedTargets,
            confirmedTargets = confirmedTargets,
            remainingTargets = null,
            contributions = contributions.sortedWith(compareBy({ it.targetValue }, { it.modeGroup.name }, { it.qsoId })),
            composite = OfficialAwardCompositeProgress(
                requiredCells = requiredCells,
                workedCells = workedCells,
                confirmedCells = confirmedCells,
                missingCells = requiredCells - confirmedCells,
            ),
        )
    }

    private fun evaluateCountPlusCoverage(
        entry: OfficialAwardCatalogEntry,
        qsos: Iterable<QsoRecord>,
        targets: AwardTargetEvidenceIndex,
        confirmations: Map<Long, List<AwardConfirmationEvidence>>,
        filter: AwardEvaluationFilter,
    ): OfficialAwardProgress {
        val requirement = entry.requirement
        require(requirement.targetKind == OfficialAwardTargetKind.IOTA_GROUP) {
            "CP-0006C count-plus-coverage evaluator currently supports IOTA-group awards"
        }
        val required = requireNotNull(requirement.requiredDistinctTargets)
        val requiredCoverage = requirement.requiredCoverage
            .mapTo(linkedSetOf()) { AwardTargetNormalizer.normalize(OfficialAwardTargetKind.CONTINENT, it) }

        val contributions = mutableListOf<OfficialAwardContribution>()
        val workedTargets = linkedSetOf<String>()
        val confirmedTargets = linkedSetOf<String>()
        val achievedCoverage = linkedSetOf<String>()

        for (qso in qsos) {
            if (!qsoEligible(qso, requirement, filter)) continue

            val groupEvidence = targets.evidence(qso.id, OfficialAwardTargetKind.IOTA_GROUP)
            if (groupEvidence.isEmpty()) continue

            val acceptedConfirmation = acceptedConfirmations(
                requirement.confirmationPolicy,
                confirmations[qso.id].orEmpty(),
            )
            val group = groupEvidence.first().normalizedValue
            workedTargets += group
            if (acceptedConfirmation.isNotEmpty()) {
                confirmedTargets += group
                targets.values(qso.id, OfficialAwardTargetKind.CONTINENT)
                    .filterTo(achievedCoverage) { it in requiredCoverage }
            }

            contributions += contribution(
                qso = qso,
                kind = OfficialAwardTargetKind.IOTA_GROUP,
                targetValue = group,
                targetEvidence = groupEvidence,
                confirmations = acceptedConfirmation,
            )
        }

        val countedTargets = when (requirement.thresholdBasis) {
            AwardThresholdBasis.WORKED -> workedTargets
            AwardThresholdBasis.CONFIRMED -> confirmedTargets
        }
        val thresholdMet =
            countedTargets.size >= required && achievedCoverage.containsAll(requiredCoverage)

        val states = basicStates(
            worked = workedTargets,
            confirmed = confirmedTargets,
            thresholdMet = thresholdMet,
        )

        return OfficialAwardProgress(
            awardId = entry.id,
            displayName = entry.displayName,
            filter = filter,
            states = states,
            thresholdBasis = requirement.thresholdBasis,
            requiredDistinctTargets = required,
            workedTargets = workedTargets,
            confirmedTargets = confirmedTargets,
            remainingTargets = null,
            contributions = contributions.sortedWith(compareBy({ it.targetValue }, { it.qsoId })),
            composite = OfficialAwardCompositeProgress(
                requiredCoverage = requiredCoverage,
                achievedCoverage = achievedCoverage,
                missingCoverage = requiredCoverage - achievedCoverage,
            ),
        )
    }

    private fun qsoEligible(
        qso: QsoRecord,
        requirement: OfficialAwardRequirement,
        filter: AwardEvaluationFilter,
    ): Boolean {
        requirement.notBeforeUtcDate?.let { isoDate ->
            val minimum = isoDate.replace("-", "")
            if (qso.qsoDate < minimum) return false
        }

        val band = qso.band.trim().lowercase()
        if (
            requirement.requiredBands.isNotEmpty() &&
            requirement.requiredBands.none { it.trim().lowercase() == band }
        ) return false
        if (requirement.excludedBands.any { it.trim().lowercase() == band }) return false
        if (filter.normalizedBand != null && filter.normalizedBand != band) return false

        filter.modeGroup?.let { requiredViewMode ->
            if (modeGrouper.groupFor(qso) != requiredViewMode) return false
        }

        return true
    }

    private fun acceptedConfirmations(
        policy: OfficialAwardConfirmationPolicy,
        evidence: List<AwardConfirmationEvidence>,
    ): List<AwardConfirmationEvidence> = when (policy) {
        OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION -> evidence
        OfficialAwardConfirmationPolicy.LOTW_ONLY -> evidence.filter {
            normalizedConfirmationSource(it.source) in setOf("LOTW", "ARRL_LOTW")
        }
        OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG -> emptyList()
    }

    private fun normalizedConfirmationSource(source: String): String =
        source.trim().uppercase().replace('-', '_').replace(' ', '_')

    private fun canonicalTarget(entry: OfficialAwardCatalogEntry, value: String): String =
        if (
            entry.id in setOf("ARRL_WAS_BASIC", "ARRL_TRIPLE_PLAY_WAS") &&
            value == "DC"
        ) {
            "MD"
        } else {
            value
        }

    private fun targetAllowed(requirement: OfficialAwardRequirement, target: String): Boolean =
        requirement.targetUniverse?.contains(target) ?: true

    private fun contribution(
        qso: QsoRecord,
        kind: OfficialAwardTargetKind,
        targetValue: String,
        targetEvidence: List<AwardTargetEvidence>,
        confirmations: List<AwardConfirmationEvidence>,
    ) = OfficialAwardContribution(
        qsoId = qso.id,
        targetKind = kind,
        targetValue = targetValue,
        band = qso.band.trim().lowercase(),
        exactMode = qso.mode,
        exactSubmode = qso.submode,
        modeGroup = modeGrouper.groupFor(qso),
        targetProvenance = targetEvidence.map { it.provenance }.distinct(),
        acceptedConfirmationEvidence = confirmations,
    )

    private fun basicStates(
        worked: Set<String>,
        confirmed: Set<String>,
        thresholdMet: Boolean,
    ): Set<AwardProgressState> = linkedSetOf<AwardProgressState>().apply {
        if (worked.isNotEmpty()) add(AwardProgressState.WORKED)
        if (confirmed.isNotEmpty()) add(AwardProgressState.CONFIRMED)
        if (thresholdMet) add(AwardProgressState.THRESHOLD_MET)
        // OFFICIALLY_CLAIMABLE is intentionally never inferred locally.
    }

    private fun matrixCell(state: String, mode: AwardModeGroup): String =
        "$state|${mode.name}"
}
