package dev.n0png.fieldops.core.awards

import dev.n0png.fieldops.core.logbook.QsoRecord

enum class AwardProgressState {
    WORKED,
    CONFIRMED,
    THRESHOLD_MET,
    OFFICIALLY_CLAIMABLE,
}

enum class AwardModeGroup {
    CW,
    PHONE,
    DIGITAL,
    UNCLASSIFIED,
}

enum class AwardThresholdBasis {
    WORKED,
    CONFIRMED,
}

data class AwardModeRule(
    val mode: String,
    val submode: String? = null,
    val group: AwardModeGroup,
) {
    init {
        require(mode.isNotBlank()) { "Award mode rule MODE must not be blank" }
        require(submode == null || submode.isNotBlank()) { "Award mode rule SUBMODE must not be blank" }
    }

    internal val normalizedMode: String = mode.trim().uppercase()
    internal val normalizedSubmode: String? = submode?.trim()?.uppercase()
}

class ControlledAwardModeGrouper(rules: List<AwardModeRule>) {
    private val rules = rules.toList()

    init {
        val duplicateSelectors = this.rules
            .groupBy { it.normalizedMode to it.normalizedSubmode }
            .filterValues { it.size > 1 }
        require(duplicateSelectors.isEmpty()) {
            "Award mode rules must have one controlled group per exact MODE/SUBMODE selector"
        }
    }

    fun groupFor(qso: QsoRecord): AwardModeGroup {
        val mode = qso.mode.trim().uppercase()
        val submode = qso.submode?.trim()?.uppercase()
        val exact = rules.firstOrNull {
            it.normalizedSubmode != null &&
                it.normalizedMode == mode &&
                it.normalizedSubmode == submode
        }
        if (exact != null) return exact.group

        return rules.firstOrNull {
            it.normalizedSubmode == null && it.normalizedMode == mode
        }?.group ?: AwardModeGroup.UNCLASSIFIED
    }

    companion object {
        /**
         * Conservative operational grouping only. These are not award-program rules.
         * Exact QSO MODE/SUBMODE stays on every contribution.
         */
        val STANDARD = ControlledAwardModeGrouper(
            listOf(
                AwardModeRule("CW", group = AwardModeGroup.CW),
                AwardModeRule("SSB", group = AwardModeGroup.PHONE),
                AwardModeRule("AM", group = AwardModeGroup.PHONE),
                AwardModeRule("FM", group = AwardModeGroup.PHONE),
                AwardModeRule("MFSK", group = AwardModeGroup.DIGITAL),
                AwardModeRule("RTTY", group = AwardModeGroup.DIGITAL),
                AwardModeRule("PSK", group = AwardModeGroup.DIGITAL),
                AwardModeRule("DATA", group = AwardModeGroup.DIGITAL),
                AwardModeRule("FT8", group = AwardModeGroup.DIGITAL),
                AwardModeRule("FT4", group = AwardModeGroup.DIGITAL),
            )
        )
    }
}

data class AwardConfirmationEvidence(
    val qsoId: Long,
    val source: String,
    val reference: String? = null,
    val confirmedAtUtcMillis: Long? = null,
) {
    init {
        require(qsoId > 0) { "Confirmation evidence QSO id must be positive" }
        require(source.isNotBlank()) { "Confirmation evidence source must not be blank" }
        require(reference == null || reference.isNotBlank()) { "Confirmation evidence reference must not be blank" }
        confirmedAtUtcMillis?.let { require(it >= 0) { "Confirmation evidence UTC must be non-negative" } }
    }
}

fun interface AwardTargetSelector {
    fun targetKey(qso: QsoRecord): String?
}

fun interface AwardOfficialClaimabilityEvaluator {
    fun isOfficiallyClaimable(context: AwardClaimabilityContext): Boolean
}

data class AwardDefinition(
    val id: String,
    val displayName: String,
    val requiredDistinctTargets: Int,
    val targetSelector: AwardTargetSelector,
    val thresholdBasis: AwardThresholdBasis = AwardThresholdBasis.CONFIRMED,
    val eligibleModeGroups: Set<AwardModeGroup> = emptySet(),
    val targetUniverse: Set<String>? = null,
    val officialClaimabilityEvaluator: AwardOfficialClaimabilityEvaluator? = null,
) {
    init {
        require(id.isNotBlank()) { "Award id must not be blank" }
        require(displayName.isNotBlank()) { "Award display name must not be blank" }
        require(requiredDistinctTargets > 0) { "Award threshold must be positive" }
        require(AwardModeGroup.UNCLASSIFIED !in eligibleModeGroups) {
            "UNCLASSIFIED cannot be an eligible award-mode group; add an explicit controlled mapping instead"
        }
        targetUniverse?.let { universe ->
            val normalized = normalizeTargets(universe)
            require(normalized.isNotEmpty()) { "Award target universe must not be empty" }
            require(normalized.size == universe.size) {
                "Award target universe must contain unique non-blank targets after normalization"
            }
            require(requiredDistinctTargets <= normalized.size) {
                "Award threshold cannot exceed the supplied target universe"
            }
        }
    }
}

data class AwardEvaluationFilter(
    val band: String? = null,
    val modeGroup: AwardModeGroup? = null,
) {
    init {
        require(band == null || band.isNotBlank()) { "Award band filter must not be blank" }
        require(modeGroup != AwardModeGroup.UNCLASSIFIED) {
            "UNCLASSIFIED is not a selectable award-mode filter"
        }
    }

    val normalizedBand: String? = band?.trim()?.lowercase()
}

data class AwardContribution(
    val qsoId: Long,
    val targetKey: String,
    val band: String,
    val exactMode: String,
    val exactSubmode: String?,
    val modeGroup: AwardModeGroup,
    val confirmationEvidence: List<AwardConfirmationEvidence>,
) {
    val confirmed: Boolean
        get() = confirmationEvidence.isNotEmpty()
}

data class AwardClaimabilityContext(
    val awardId: String,
    val filter: AwardEvaluationFilter,
    val workedTargets: Set<String>,
    val confirmedTargets: Set<String>,
    val thresholdBasis: AwardThresholdBasis,
    val requiredDistinctTargets: Int,
) {
    val thresholdCount: Int
        get() = when (thresholdBasis) {
            AwardThresholdBasis.WORKED -> workedTargets.size
            AwardThresholdBasis.CONFIRMED -> confirmedTargets.size
        }

    val thresholdMet: Boolean
        get() = thresholdCount >= requiredDistinctTargets
}

data class AwardEvaluation(
    val awardId: String,
    val displayName: String,
    val filter: AwardEvaluationFilter,
    val states: Set<AwardProgressState>,
    val workedTargets: Set<String>,
    val confirmedTargets: Set<String>,
    val thresholdBasis: AwardThresholdBasis,
    val requiredDistinctTargets: Int,
    val remainingTargets: Set<String>?,
    val contributions: List<AwardContribution>,
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
}

class AwardEvaluator(
    private val modeGrouper: ControlledAwardModeGrouper = ControlledAwardModeGrouper.STANDARD,
) {
    fun evaluate(
        definition: AwardDefinition,
        qsos: Iterable<QsoRecord>,
        confirmations: Iterable<AwardConfirmationEvidence> = emptyList(),
        filter: AwardEvaluationFilter = AwardEvaluationFilter(),
    ): AwardEvaluation {
        val evidenceByQso = confirmations
            .groupBy { it.qsoId }
            .mapValues { (_, evidence) -> evidence.distinct() }

        val contributions = qsos.mapNotNull { qso ->
            val group = modeGrouper.groupFor(qso)
            if (group == AwardModeGroup.UNCLASSIFIED) return@mapNotNull null
            if (definition.eligibleModeGroups.isNotEmpty() && group !in definition.eligibleModeGroups) {
                return@mapNotNull null
            }
            if (filter.modeGroup != null && group != filter.modeGroup) return@mapNotNull null

            val band = qso.band.trim().lowercase()
            if (filter.normalizedBand != null && band != filter.normalizedBand) return@mapNotNull null

            val target = definition.targetSelector.targetKey(qso)?.let(::normalizeTarget)
            if (target.isNullOrEmpty()) return@mapNotNull null

            AwardContribution(
                qsoId = qso.id,
                targetKey = target,
                band = band,
                exactMode = qso.mode,
                exactSubmode = qso.submode,
                modeGroup = group,
                confirmationEvidence = evidenceByQso[qso.id].orEmpty(),
            )
        }

        val workedTargets = contributions.mapTo(linkedSetOf()) { it.targetKey }
        val confirmedTargets = contributions
            .asSequence()
            .filter { it.confirmed }
            .mapTo(linkedSetOf()) { it.targetKey }

        val context = AwardClaimabilityContext(
            awardId = definition.id,
            filter = filter,
            workedTargets = workedTargets,
            confirmedTargets = confirmedTargets,
            thresholdBasis = definition.thresholdBasis,
            requiredDistinctTargets = definition.requiredDistinctTargets,
        )

        val states = linkedSetOf<AwardProgressState>()
        if (workedTargets.isNotEmpty()) states += AwardProgressState.WORKED
        if (confirmedTargets.isNotEmpty()) states += AwardProgressState.CONFIRMED
        if (context.thresholdMet) states += AwardProgressState.THRESHOLD_MET
        if (
            context.thresholdMet &&
            definition.officialClaimabilityEvaluator?.isOfficiallyClaimable(context) == true
        ) {
            states += AwardProgressState.OFFICIALLY_CLAIMABLE
        }

        val countedTargets = when (definition.thresholdBasis) {
            AwardThresholdBasis.WORKED -> workedTargets
            AwardThresholdBasis.CONFIRMED -> confirmedTargets
        }
        val remainingTargets = definition.targetUniverse?.let { universe ->
            normalizeTargets(universe) - countedTargets
        }

        return AwardEvaluation(
            awardId = definition.id,
            displayName = definition.displayName,
            filter = filter,
            states = states,
            workedTargets = workedTargets,
            confirmedTargets = confirmedTargets,
            thresholdBasis = definition.thresholdBasis,
            requiredDistinctTargets = definition.requiredDistinctTargets,
            remainingTargets = remainingTargets,
            contributions = contributions,
        )
    }
}

private fun normalizeTarget(value: String): String = value.trim().uppercase()

private fun normalizeTargets(values: Set<String>): Set<String> {
    require(values.none { it.isBlank() }) { "Award target universe must not contain blank targets" }
    return values.mapTo(linkedSetOf(), ::normalizeTarget)
}
