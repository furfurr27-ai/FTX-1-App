package dev.n0png.fieldops.core.awards

import dev.n0png.fieldops.core.logbook.QsoRecord

enum class AwardsCenterModeView {
    MIXED,
    CW,
    PHONE,
    DIGITAL;

    val modeGroup: AwardModeGroup?
        get() = when (this) {
            MIXED -> null
            CW -> AwardModeGroup.CW
            PHONE -> AwardModeGroup.PHONE
            DIGITAL -> AwardModeGroup.DIGITAL
        }
}

data class AwardsCenterDateRange(
    val startUtcDate: String? = null,
    val endUtcDate: String? = null,
) {
    val normalizedStart: String? = startUtcDate?.let(::normalizeIsoDate)
    val normalizedEnd: String? = endUtcDate?.let(::normalizeIsoDate)

    init {
        if (normalizedStart != null && normalizedEnd != null) {
            require(normalizedStart <= normalizedEnd) {
                "Awards Center date range start must not be after end"
            }
        }
    }

    fun contains(qsoDate: String): Boolean {
        require(qsoDate.matches(Regex("""\d{8}"""))) {
            "QSO date must be UTC YYYYMMDD for Awards Center filtering"
        }
        if (normalizedStart != null && qsoDate < normalizedStart) return false
        if (normalizedEnd != null && qsoDate > normalizedEnd) return false
        return true
    }

    private companion object {
        fun normalizeIsoDate(value: String): String {
            val trimmed = value.trim()
            require(trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                "Awards Center date filter must be YYYY-MM-DD"
            }
            return trimmed.replace("-", "")
        }
    }
}

data class AwardsCenterQuery(
    val modeView: AwardsCenterModeView = AwardsCenterModeView.MIXED,
    val band: String? = null,
    val dateRange: AwardsCenterDateRange? = null,
) {
    init {
        require(band == null || band.isNotBlank()) {
            "Awards Center band filter must not be blank"
        }
    }

    val normalizedBand: String? = band?.trim()?.lowercase()

    fun evaluationFilter(): AwardEvaluationFilter = AwardEvaluationFilter(
        band = normalizedBand,
        modeGroup = modeView.modeGroup,
    )
}

enum class AwardsCenterLocalEvaluationStatus {
    AVAILABLE,
    EXTERNAL_PROGRAM_SCORING,
}

enum class AwardsCenterProgressKind {
    DISTINCT_TARGETS,
    MODE_TARGET_CELLS,
    COUNT_PLUS_COVERAGE,
    EXTERNAL_UNAVAILABLE,
}

data class AwardsCenterProgressMetric(
    val kind: AwardsCenterProgressKind,
    val completedUnits: Int? = null,
    val requiredUnits: Int? = null,
    val basisPoints: Int? = null,
) {
    init {
        if (kind == AwardsCenterProgressKind.EXTERNAL_UNAVAILABLE) {
            require(completedUnits == null && requiredUnits == null && basisPoints == null) {
                "External progress must not fabricate numeric units"
            }
        } else {
            require(completedUnits != null && completedUnits >= 0) {
                "Local progress completed units must be non-negative"
            }
            require(requiredUnits != null && requiredUnits > 0) {
                "Local progress required units must be positive"
            }
            require(basisPoints != null && basisPoints in 0..10_000) {
                "Local progress basis points must be between 0 and 10000"
            }
        }
    }

    val percent: Double?
        get() = basisPoints?.div(100.0)
}

data class AwardsCenterOfficialSource(
    val role: OfficialAwardSourceRole,
    val url: String,
    val retrievedOn: String,
    val versionLabel: String?,
)

data class AwardsCenterAwardCard(
    val awardId: String,
    val displayName: String,
    val issuer: String,
    val description: String,
    val query: AwardsCenterQuery,
    val localEvaluationStatus: AwardsCenterLocalEvaluationStatus,
    val evaluationSupport: OfficialAwardEvaluationSupport,
    val progress: AwardsCenterProgressMetric,
    val workedCount: Int?,
    val confirmedCount: Int?,
    val localThresholdMet: Boolean?,
    val thresholdBasis: AwardThresholdBasis?,
    val remainingTargets: Set<String>,
    val remainingCells: Set<String>,
    val missingCoverage: Set<String>,
    val informationUrl: String,
    val claimUrl: String?,
    val claimInstructions: String,
    val claimMechanism: OfficialAwardClaimMechanism,
    val officialSources: List<AwardsCenterOfficialSource>,
    val officialConditions: List<String>,
    val sponsorStanding: OfficialAwardStanding,
    val sponsorReference: String?,
    val sponsorStandingRecordedAtUtcMillis: Long?,
    val claimableNow: Boolean?,
    val warnings: List<String>,
)

class AwardsCenterProjectionService(
    private val progressEngine: OfficialAwardProgressEngine = OfficialAwardProgressEngine(),
) {
    fun project(
        qsos: Iterable<QsoRecord>,
        targets: AwardTargetEvidenceIndex,
        confirmations: Iterable<AwardConfirmationEvidence> = emptyList(),
        sponsorStandings: Iterable<OfficialAwardStandingRecord> = emptyList(),
        query: AwardsCenterQuery = AwardsCenterQuery(),
    ): List<AwardsCenterAwardCard> {
        val filteredQsos = qsos.filter { qso ->
            query.dateRange?.contains(qso.qsoDate) ?: true
        }
        val standingByAward = latestSponsorStanding(sponsorStandings)

        return OfficialAwardCatalog.entries.map { entry ->
            val sponsorStanding = standingByAward[entry.id]
            if (entry.evaluationSupport == OfficialAwardEvaluationSupport.EXTERNAL_PROGRAM_SCORING) {
                externalCard(entry, query, sponsorStanding)
            } else {
                val progress = progressEngine.evaluate(
                    entry = entry,
                    qsos = filteredQsos,
                    targets = targets,
                    confirmations = confirmations,
                    filter = query.evaluationFilter(),
                )
                localCard(entry, query, progress, sponsorStanding)
            }
        }
    }

    private fun localCard(
        entry: OfficialAwardCatalogEntry,
        query: AwardsCenterQuery,
        progress: OfficialAwardProgress,
        sponsor: OfficialAwardStandingRecord?,
    ): AwardsCenterAwardCard {
        val metric = metricFor(entry, progress, query)
        val remainingCells = filteredCells(
            progress.composite?.missingCells.orEmpty(),
            query.modeView,
        )

        return AwardsCenterAwardCard(
            awardId = entry.id,
            displayName = entry.displayName,
            issuer = entry.issuer,
            description = entry.description,
            query = query,
            localEvaluationStatus = AwardsCenterLocalEvaluationStatus.AVAILABLE,
            evaluationSupport = entry.evaluationSupport,
            progress = metric,
            workedCount = progress.workedCount,
            confirmedCount = progress.confirmedCount,
            localThresholdMet = progress.thresholdMet,
            thresholdBasis = progress.thresholdBasis,
            remainingTargets = progress.remainingTargets.orEmpty(),
            remainingCells = remainingCells,
            missingCoverage = progress.composite?.missingCoverage.orEmpty(),
            informationUrl = entry.informationUrl,
            claimUrl = entry.claimUrl,
            claimInstructions = entry.claimInstructions,
            claimMechanism = entry.claimMechanism,
            officialSources = entry.sources.map(::sourceView),
            officialConditions = entry.requirement.additionalConditions,
            sponsorStanding = sponsor?.standing ?: OfficialAwardStanding.UNKNOWN,
            sponsorReference = sponsor?.sponsorReference,
            sponsorStandingRecordedAtUtcMillis = sponsor?.recordedAtUtcMillis,
            claimableNow = claimableNow(sponsor),
            warnings = warningsFor(entry, sponsor, external = false),
        )
    }

    private fun externalCard(
        entry: OfficialAwardCatalogEntry,
        query: AwardsCenterQuery,
        sponsor: OfficialAwardStandingRecord?,
    ): AwardsCenterAwardCard = AwardsCenterAwardCard(
        awardId = entry.id,
        displayName = entry.displayName,
        issuer = entry.issuer,
        description = entry.description,
        query = query,
        localEvaluationStatus = AwardsCenterLocalEvaluationStatus.EXTERNAL_PROGRAM_SCORING,
        evaluationSupport = entry.evaluationSupport,
        progress = AwardsCenterProgressMetric(
            kind = AwardsCenterProgressKind.EXTERNAL_UNAVAILABLE,
        ),
        workedCount = null,
        confirmedCount = null,
        localThresholdMet = null,
        thresholdBasis = entry.requirement.thresholdBasis,
        remainingTargets = emptySet(),
        remainingCells = emptySet(),
        missingCoverage = emptySet(),
        informationUrl = entry.informationUrl,
        claimUrl = entry.claimUrl,
        claimInstructions = entry.claimInstructions,
        claimMechanism = entry.claimMechanism,
        officialSources = entry.sources.map(::sourceView),
        officialConditions = entry.requirement.additionalConditions,
        sponsorStanding = sponsor?.standing ?: OfficialAwardStanding.UNKNOWN,
        sponsorReference = sponsor?.sponsorReference,
        sponsorStandingRecordedAtUtcMillis = sponsor?.recordedAtUtcMillis,
        claimableNow = claimableNow(sponsor),
        warnings = warningsFor(entry, sponsor, external = true),
    )

    private fun metricFor(
        entry: OfficialAwardCatalogEntry,
        progress: OfficialAwardProgress,
        query: AwardsCenterQuery,
    ): AwardsCenterProgressMetric = when (entry.requirement.ruleShape) {
        OfficialAwardRuleShape.DISTINCT_TARGET_COUNT -> {
            val required = requireNotNull(progress.requiredDistinctTargets)
            metric(
                AwardsCenterProgressKind.DISTINCT_TARGETS,
                completed = progress.thresholdCount.coerceAtMost(required),
                required = required,
            )
        }

        OfficialAwardRuleShape.MODE_TARGET_MATRIX -> {
            val composite = requireNotNull(progress.composite)
            val requiredCells = filteredCells(composite.requiredCells, query.modeView)
            val countedCells = when (progress.thresholdBasis) {
                AwardThresholdBasis.WORKED -> filteredCells(composite.workedCells, query.modeView)
                AwardThresholdBasis.CONFIRMED -> filteredCells(composite.confirmedCells, query.modeView)
            }
            metric(
                AwardsCenterProgressKind.MODE_TARGET_CELLS,
                completed = countedCells.size.coerceAtMost(requiredCells.size),
                required = requiredCells.size,
            )
        }

        OfficialAwardRuleShape.COUNT_PLUS_REQUIRED_COVERAGE -> {
            val requiredTargets = requireNotNull(progress.requiredDistinctTargets)
            val composite = requireNotNull(progress.composite)
            val achievedCoverage = composite.achievedCoverage.intersect(composite.requiredCoverage)
            val completedTargets = progress.thresholdCount.coerceAtMost(requiredTargets)
            metric(
                AwardsCenterProgressKind.COUNT_PLUS_COVERAGE,
                completed = completedTargets + achievedCoverage.size,
                required = requiredTargets + composite.requiredCoverage.size,
            )
        }

        OfficialAwardRuleShape.POINTS ->
            AwardsCenterProgressMetric(AwardsCenterProgressKind.EXTERNAL_UNAVAILABLE)
    }

    private fun metric(
        kind: AwardsCenterProgressKind,
        completed: Int,
        required: Int,
    ): AwardsCenterProgressMetric {
        require(required > 0) { "Awards Center local progress denominator must be positive" }
        val boundedCompleted = completed.coerceIn(0, required)
        val basisPoints = ((boundedCompleted.toLong() * 10_000L) / required.toLong()).toInt()
        return AwardsCenterProgressMetric(
            kind = kind,
            completedUnits = boundedCompleted,
            requiredUnits = required,
            basisPoints = basisPoints,
        )
    }

    private fun filteredCells(
        cells: Set<String>,
        modeView: AwardsCenterModeView,
    ): Set<String> {
        val mode = modeView.modeGroup ?: return cells
        val suffix = "|${mode.name}"
        return cells.filterTo(linkedSetOf()) { it.endsWith(suffix) }
    }

    private fun sourceView(source: OfficialAwardSource) = AwardsCenterOfficialSource(
        role = source.role,
        url = source.url,
        retrievedOn = source.retrievedOn,
        versionLabel = source.versionLabel,
    )

    private fun claimableNow(sponsor: OfficialAwardStandingRecord?): Boolean? =
        when (sponsor?.standing ?: OfficialAwardStanding.UNKNOWN) {
            OfficialAwardStanding.UNKNOWN -> null
            OfficialAwardStanding.ELIGIBLE_NOT_CLAIMED -> true
            OfficialAwardStanding.SUBMITTED,
            OfficialAwardStanding.AWARDED,
            OfficialAwardStanding.CREDITED -> false
        }

    private fun warningsFor(
        entry: OfficialAwardCatalogEntry,
        sponsor: OfficialAwardStandingRecord?,
        external: Boolean,
    ): List<String> = buildList {
        add("Local progress does not establish sponsor claimability or award credit.")

        if (external) {
            add("Local percentage is unavailable because this award uses external program scoring.")
        } else {
            when (entry.evaluationSupport) {
                OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET ->
                    add("Local progress requires provenance-bearing normalized award target evidence.")

                OfficialAwardEvaluationSupport.REQUIRES_COMPOSITE_RULE_ENGINE ->
                    add("Local progress uses the catalog's composite rule evaluator; sponsor-only checks may still remain external.")

                OfficialAwardEvaluationSupport.EXTERNAL_PROGRAM_SCORING -> Unit
            }
        }

        if (entry.requirement.confirmationPolicy == OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG) {
            add("Sponsor program validation is external and is not inferred from ordinary QSO or LoTW state.")
        }

        if (sponsor == null || sponsor.standing == OfficialAwardStanding.UNKNOWN) {
            add("Sponsor standing is unknown until explicit sponsor evidence is recorded.")
        }
    }

    private fun latestSponsorStanding(
        standings: Iterable<OfficialAwardStandingRecord>,
    ): Map<String, OfficialAwardStandingRecord> {
        val grouped = standings.groupBy { it.awardId }
        return grouped.mapValues { (awardId, records) ->
            val ordered = records.sortedBy { it.recordedAtUtcMillis ?: Long.MIN_VALUE }
            val latest = ordered.last()
            val latestTime = latest.recordedAtUtcMillis
            val ties = ordered.filter { it.recordedAtUtcMillis == latestTime }
            require(ties.distinct().size == 1) {
                "Conflicting sponsor standing records share the latest timestamp for award $awardId"
            }
            latest
        }
    }
}
