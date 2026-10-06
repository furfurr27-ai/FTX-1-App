package dev.n0png.fieldops.core.awards

enum class OfficialAwardTargetKind {
    DXCC_ENTITY,
    US_STATE,
    CONTINENT,
    MODE_STATE_PAIR,
    IOTA_GROUP,
    POTA_REFERENCE,
    SOTA_POINT,
}

enum class OfficialAwardRuleShape {
    DISTINCT_TARGET_COUNT,
    COUNT_PLUS_REQUIRED_COVERAGE,
    MODE_TARGET_MATRIX,
    POINTS,
}

enum class OfficialAwardEvaluationSupport {
    REQUIRES_NORMALIZED_QSO_TARGET,
    REQUIRES_COMPOSITE_RULE_ENGINE,
    EXTERNAL_PROGRAM_SCORING,
}

enum class OfficialAwardConfirmationPolicy {
    ACCEPTED_CONFIRMATION,
    LOTW_ONLY,
    PROGRAM_VALIDATED_LOG,
}

enum class OfficialAwardClaimMechanism {
    APPLICATION_OR_LOTW,
    PAPER_OR_CHECKED_APPLICATION,
    LOTW_ELECTRONIC_APPLICATION,
    ONLINE_PROGRAM_APPLICATION,
    AUTOMATIC_PROGRAM_ISSUE,
    PROGRAM_DATABASE_AND_SHOP,
}

enum class OfficialAwardSourceRole {
    RULES,
    CLAIM_PROCESS,
    PROGRAM_INFO,
}

enum class OfficialAwardStanding {
    UNKNOWN,
    ELIGIBLE_NOT_CLAIMED,
    SUBMITTED,
    AWARDED,
    CREDITED,
}

data class OfficialAwardSource(
    val role: OfficialAwardSourceRole,
    val url: String,
    val retrievedOn: String,
    val versionLabel: String? = null,
) {
    init {
        require(url.startsWith("https://")) { "Official award source must use HTTPS" }
        require(retrievedOn.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            "Official award source retrieval date must be YYYY-MM-DD"
        }
        require(versionLabel == null || versionLabel.isNotBlank()) {
            "Official award source version label must not be blank"
        }
    }
}

data class OfficialAwardRequirement(
    val targetKind: OfficialAwardTargetKind,
    val ruleShape: OfficialAwardRuleShape,
    val requiredDistinctTargets: Int? = null,
    val thresholdBasis: AwardThresholdBasis,
    val targetUniverse: Set<String>? = null,
    val requiredCoverage: Set<String> = emptySet(),
    val requiredModeGroups: Set<AwardModeGroup> = emptySet(),
    val excludedBands: Set<String> = emptySet(),
    val notBeforeUtcDate: String? = null,
    val confirmationPolicy: OfficialAwardConfirmationPolicy,
    val additionalConditions: List<String> = emptyList(),
) {
    init {
        requiredDistinctTargets?.let { require(it > 0) { "Official award threshold must be positive" } }
        require(
            ruleShape == OfficialAwardRuleShape.POINTS || requiredDistinctTargets != null
        ) {
            "Non-points official award rules require a target threshold"
        }
        require(
            ruleShape != OfficialAwardRuleShape.POINTS || targetKind == OfficialAwardTargetKind.SOTA_POINT
        ) {
            "Points rule shape is reserved for point-based award targets"
        }
        require(AwardModeGroup.UNCLASSIFIED !in requiredModeGroups) {
            "Official award mode requirements cannot use UNCLASSIFIED"
        }
        require(excludedBands.none { it.isBlank() }) { "Excluded award bands must not be blank" }
        require(requiredCoverage.none { it.isBlank() }) { "Required award coverage values must not be blank" }
        require(additionalConditions.none { it.isBlank() }) { "Additional award conditions must not be blank" }
        targetUniverse?.let { universe ->
            require(universe.isNotEmpty()) { "Official award target universe must not be empty" }
            require(universe.none { it.isBlank() }) { "Official award target universe must not contain blanks" }
            requiredDistinctTargets?.let {
                require(it <= universe.size) {
                    "Official award threshold cannot exceed its supplied target universe"
                }
            }
        }
        notBeforeUtcDate?.let {
            require(it.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                "Official award not-before date must be YYYY-MM-DD"
            }
        }
    }
}

data class OfficialAwardCatalogEntry(
    val id: String,
    val displayName: String,
    val issuer: String,
    val description: String,
    val requirement: OfficialAwardRequirement,
    val evaluationSupport: OfficialAwardEvaluationSupport,
    val claimMechanism: OfficialAwardClaimMechanism,
    val informationUrl: String,
    val claimUrl: String?,
    val claimInstructions: String,
    val sources: List<OfficialAwardSource>,
) {
    init {
        require(id.isNotBlank()) { "Official award id must not be blank" }
        require(displayName.isNotBlank()) { "Official award display name must not be blank" }
        require(issuer.isNotBlank()) { "Official award issuer must not be blank" }
        require(description.isNotBlank()) { "Official award description must not be blank" }
        require(informationUrl.startsWith("https://")) { "Official award information URL must use HTTPS" }
        require(claimUrl == null || claimUrl.startsWith("https://")) {
            "Official award claim URL must use HTTPS"
        }
        require(claimInstructions.isNotBlank()) { "Official award claim instructions must not be blank" }
        require(sources.isNotEmpty()) { "Official award entry must retain at least one official source" }
        if (claimMechanism == OfficialAwardClaimMechanism.AUTOMATIC_PROGRAM_ISSUE) {
            require(claimUrl == null) { "Automatically issued awards must not invent a claim URL" }
        }
    }

    /**
     * Produces only a local CP-0006A threshold definition.
     *
     * This deliberately never attaches an official-claimability evaluator:
     * matching a locally countable threshold is not proof that the issuing
     * organization will accept or credit an application.
     */
    fun toLocalThresholdDefinition(targetSelector: AwardTargetSelector): AwardDefinition {
        require(requirement.ruleShape == OfficialAwardRuleShape.DISTINCT_TARGET_COUNT) {
            "Award $id requires composite/external rule handling and cannot be reduced to distinct-target counting"
        }
        val required = requireNotNull(requirement.requiredDistinctTargets)
        return AwardDefinition(
            id = id,
            displayName = displayName,
            requiredDistinctTargets = required,
            targetSelector = targetSelector,
            thresholdBasis = requirement.thresholdBasis,
            targetUniverse = requirement.targetUniverse,
            officialClaimabilityEvaluator = null,
        )
    }
}

data class OfficialAwardStandingRecord(
    val awardId: String,
    val standing: OfficialAwardStanding,
    val sponsorReference: String? = null,
    val recordedAtUtcMillis: Long? = null,
) {
    init {
        require(awardId.isNotBlank()) { "Official award standing requires an award id" }
        require(sponsorReference == null || sponsorReference.isNotBlank()) {
            "Official award sponsor reference must not be blank"
        }
        recordedAtUtcMillis?.let { require(it >= 0) { "Official award standing UTC must be non-negative" } }
        if (standing == OfficialAwardStanding.AWARDED || standing == OfficialAwardStanding.CREDITED) {
            require(!sponsorReference.isNullOrBlank()) {
                "Awarded/credited standing requires explicit sponsor evidence"
            }
        }
    }
}

object OfficialAwardCatalog {
    const val CATALOG_VERSION = "2026-10-06.1"
    const val RETRIEVED_ON = "2026-10-06"

    private val usStates = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    )

    private val sixWacContinents = setOf(
        "AFRICA",
        "ASIA",
        "EUROPE",
        "NORTH_AMERICA",
        "OCEANIA",
        "SOUTH_AMERICA",
    )

    private val sevenIotaContinents = sixWacContinents + "ANTARCTICA"

    val entries: List<OfficialAwardCatalogEntry> = listOf(
        OfficialAwardCatalogEntry(
            id = "ARRL_DXCC_MIXED",
            displayName = "ARRL DX Century Club (DXCC) — Mixed",
            issuer = "ARRL",
            description = "Basic Mixed DXCC certificate threshold using confirmed DXCC entities.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.DXCC_ENTITY,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 100,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
                notBeforeUtcDate = "1945-11-15",
                additionalConditions = listOf(
                    "Contacts must satisfy the current DXCC rules and entity-credit rules.",
                    "Local threshold completion is not equivalent to ARRL award credit.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET,
            claimMechanism = OfficialAwardClaimMechanism.APPLICATION_OR_LOTW,
            informationUrl = "https://www.arrl.org/dxcc-rules",
            claimUrl = "https://lotw.arrl.org/lotw-help/dxcc-award-credit/",
            claimInstructions = "Use accepted DXCC confirmations and submit an application through ARRL/LoTW or another ARRL-supported DXCC application path.",
            sources = listOf(
                source(OfficialAwardSourceRole.RULES, "https://www.arrl.org/dxcc-rules"),
                source(OfficialAwardSourceRole.CLAIM_PROCESS, "https://lotw.arrl.org/lotw-help/dxcc-award-credit/"),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "ARRL_WAS_BASIC",
            displayName = "ARRL Worked All States (WAS)",
            issuer = "ARRL",
            description = "Basic Worked All States award for confirmed contacts with all 50 U.S. states.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.US_STATE,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 50,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                targetUniverse = usStates,
                excludedBands = setOf("60m"),
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
                additionalConditions = listOf(
                    "District of Columbia may be counted for Maryland.",
                    "Contacts must satisfy the WAS same-location rule; locations may not be more than 50 miles apart.",
                    "Specialty awards and endorsements may add band/mode conditions beyond basic WAS.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET,
            claimMechanism = OfficialAwardClaimMechanism.APPLICATION_OR_LOTW,
            informationUrl = "https://www.arrl.org/WAS",
            claimUrl = "https://www.arrl.org/was-forms",
            claimInstructions = "Apply using an ARRL-supported WAS method: paper-card, LoTW, or hybrid as applicable to the confirmations being claimed.",
            sources = listOf(
                source(OfficialAwardSourceRole.RULES, "https://www.arrl.org/WAS"),
                source(OfficialAwardSourceRole.CLAIM_PROCESS, "https://www.arrl.org/was-forms"),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "IARU_WAC_BASIC",
            displayName = "IARU Worked All Continents (WAC)",
            issuer = "IARU (administered through ARRL for this catalog source)",
            description = "Worked All Continents certificate requiring confirmed contacts with all six WAC continents.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.CONTINENT,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 6,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                targetUniverse = sixWacContinents,
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET,
            claimMechanism = OfficialAwardClaimMechanism.PAPER_OR_CHECKED_APPLICATION,
            informationUrl = "https://www.arrl.org/wac",
            claimUrl = "https://www.arrl.org/wac",
            claimInstructions = "Submit the WAC application using the official ARRL/IARU checking process described on the WAC page.",
            sources = listOf(
                source(OfficialAwardSourceRole.RULES, "https://www.arrl.org/wac"),
                source(OfficialAwardSourceRole.CLAIM_PROCESS, "https://www.arrl.org/wac"),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "ARRL_TRIPLE_PLAY_WAS",
            displayName = "ARRL Triple Play WAS",
            issuer = "ARRL",
            description = "All 50 U.S. states confirmed separately on voice, CW, and digital using LoTW.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.MODE_STATE_PAIR,
                ruleShape = OfficialAwardRuleShape.MODE_TARGET_MATRIX,
                requiredDistinctTargets = 150,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                requiredCoverage = usStates,
                requiredModeGroups = setOf(
                    AwardModeGroup.PHONE,
                    AwardModeGroup.CW,
                    AwardModeGroup.DIGITAL,
                ),
                excludedBands = setOf("60m"),
                notBeforeUtcDate = "2009-01-01",
                confirmationPolicy = OfficialAwardConfirmationPolicy.LOTW_ONLY,
                additionalConditions = listOf(
                    "Each of the 50 states must be confirmed in each of the three required mode groups.",
                    "District of Columbia may be counted for Maryland.",
                    "Contacts must satisfy the Triple Play same-location rule; locations may not be more than 50 miles apart.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_COMPOSITE_RULE_ENGINE,
            claimMechanism = OfficialAwardClaimMechanism.LOTW_ELECTRONIC_APPLICATION,
            informationUrl = "https://www.arrl.org/triple-play",
            claimUrl = "https://www.arrl.org/triple-play",
            claimInstructions = "Use LoTW confirmations for all qualifying QSOs and submit the electronic Triple Play application through the LoTW-supported process.",
            sources = listOf(
                source(OfficialAwardSourceRole.RULES, "https://www.arrl.org/triple-play"),
                source(OfficialAwardSourceRole.CLAIM_PROCESS, "https://www.arrl.org/triple-play"),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "IOTA_100",
            displayName = "IOTA 100 Islands of the World",
            issuer = "IOTA Ltd / IOTA Programme",
            description = "Basic IOTA certificate for 100 confirmed IOTA groups with required continental coverage.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.IOTA_GROUP,
                ruleShape = OfficialAwardRuleShape.COUNT_PLUS_REQUIRED_COVERAGE,
                requiredDistinctTargets = 100,
                thresholdBasis = AwardThresholdBasis.CONFIRMED,
                requiredCoverage = sevenIotaContinents,
                confirmationPolicy = OfficialAwardConfirmationPolicy.ACCEPTED_CONFIRMATION,
                notBeforeUtcDate = "1945-11-15",
                additionalConditions = listOf(
                    "Standard HF basic award requires at least one confirmed IOTA group from each of seven continents.",
                    "Only one confirmed contact counts for each IOTA reference group.",
                    "Standard HF contacts must satisfy current IOTA programme participation and operating rules.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_COMPOSITE_RULE_ENGINE,
            claimMechanism = OfficialAwardClaimMechanism.ONLINE_PROGRAM_APPLICATION,
            informationUrl = "https://www.iota-world.org/iota-directory/iota-programme-rules.html",
            claimUrl = "https://www.iota-world.org/",
            claimInstructions = "Build and submit the IOTA claim through the official IOTA online database using accepted QSO matches and/or other confirmation methods permitted by the current rules.",
            sources = listOf(
                source(
                    OfficialAwardSourceRole.RULES,
                    "https://www.iota-world.org/info/directory/rules-en.pdf",
                    "Programme Rules published 2026-05-05",
                ),
                source(OfficialAwardSourceRole.CLAIM_PROCESS, "https://www.iota-world.org/"),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "POTA_BRONZE_HUNTER",
            displayName = "POTA Bronze Hunter",
            issuer = "Parks on the Air",
            description = "Hunter unique-reference award for contacting 10 different POTA references.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.POTA_REFERENCE,
                ruleShape = OfficialAwardRuleShape.DISTINCT_TARGET_COUNT,
                requiredDistinctTargets = 10,
                thresholdBasis = AwardThresholdBasis.WORKED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG,
                additionalConditions = listOf(
                    "POTA determines award progress from logs accepted by the POTA program.",
                    "The award is issued automatically by POTA after qualifying logs are processed.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.REQUIRES_NORMALIZED_QSO_TARGET,
            claimMechanism = OfficialAwardClaimMechanism.AUTOMATIC_PROGRAM_ISSUE,
            informationUrl = "https://docs.pota.app/docs/award_certificates/unique_reference.html",
            claimUrl = null,
            claimInstructions = "No manual award claim is required; POTA states that qualifying unique-reference awards are issued automatically after associated logs are uploaded and processed.",
            sources = listOf(
                source(
                    OfficialAwardSourceRole.RULES,
                    "https://docs.pota.app/docs/award_certificates/unique_reference.html",
                ),
            ),
        ),
        OfficialAwardCatalogEntry(
            id = "SOTA_SHACK_SLOTH_1000",
            displayName = "SOTA Shack Sloth — 1,000 points",
            issuer = "Summits on the Air (SOTA)",
            description = "SOTA chaser milestone reaching 1,000 points.",
            requirement = OfficialAwardRequirement(
                targetKind = OfficialAwardTargetKind.SOTA_POINT,
                ruleShape = OfficialAwardRuleShape.POINTS,
                requiredDistinctTargets = null,
                thresholdBasis = AwardThresholdBasis.WORKED,
                confirmationPolicy = OfficialAwardConfirmationPolicy.PROGRAM_VALIDATED_LOG,
                additionalConditions = listOf(
                    "SOTA chaser scoring is governed by SOTA programme and association rules rather than a simple distinct-QSO count.",
                    "SOTA awards are not sent automatically; the participant must claim/order the award.",
                ),
            ),
            evaluationSupport = OfficialAwardEvaluationSupport.EXTERNAL_PROGRAM_SCORING,
            claimMechanism = OfficialAwardClaimMechanism.PROGRAM_DATABASE_AND_SHOP,
            informationUrl = "https://www.sota.org.uk/Joining-In/Awards",
            claimUrl = "https://www.sota.org.uk/Joining-In/Awards",
            claimInstructions = "Keep the SOTA online database current, then claim/order the eligible award through the official SOTA award/shop process.",
            sources = listOf(
                source(OfficialAwardSourceRole.RULES, "https://www.sota.org.uk/Joining-In/Awards"),
                source(
                    OfficialAwardSourceRole.PROGRAM_INFO,
                    "https://www.sota.org.uk/Joining-In/General-Rules",
                    "General Rules v1.21, updated 2022-06-01",
                ),
            ),
        ),
    )

    private val byId = entries.associateBy { it.id }

    init {
        require(entries.size == byId.size) { "Official award catalog ids must be unique" }
    }

    fun get(id: String): OfficialAwardCatalogEntry? = byId[id]

    fun require(id: String): OfficialAwardCatalogEntry =
        requireNotNull(get(id)) { "Unknown official award id: $id" }

    private fun source(
        role: OfficialAwardSourceRole,
        url: String,
        versionLabel: String? = null,
    ) = OfficialAwardSource(
        role = role,
        url = url,
        retrievedOn = RETRIEVED_ON,
        versionLabel = versionLabel,
    )
}
